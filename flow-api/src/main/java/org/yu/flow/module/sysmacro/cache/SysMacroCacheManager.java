package org.yu.flow.module.sysmacro.cache;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统宏定义缓存管理器（L1 本地缓存 + L2 Redis Pub/Sub 集群广播）
 *
 * <h3>架构概述</h3>
 * <pre>
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  动态 API 引擎（高频调用路径）                            │
 *   │      │                                                   │
 *   │      ▼                                                   │
 *   │  SysMacroCacheManager.getMacro("sys_user_id")           │
 *   │      │                                                   │
 *   │      ▼  O(1) ConcurrentHashMap 查找                      │
 *   │  L1 本地缓存 (ConcurrentHashMap&lt;String, CachedMacro&gt;)   │
 *   └─────────────────────────────────────────────────────────┘
 *
 *   管理员修改宏配置 → ServiceImpl.save/update/delete
 *       │
 *       ▼
 *   publishRefreshEvent()  →  Redis Pub/Sub 广播
 *       │
 *       ├── 节点 A (当前) → reloadAll()
 *       ├── 节点 B         → reloadAll()
 *       └── 节点 C         → reloadAll()
 * </pre>
 *
 * <h3>线程安全说明</h3>
 * <ul>
 *   <li>MACRO_CACHE 使用 {@link ConcurrentHashMap}，读写并发安全。</li>
 *   <li>{@link #reloadAll()} 采用"先构建新 Map 再整体替换"策略，
 *       避免在重载过程中出现半加载状态。</li>
 *   <li>SpEL {@link Expression} 对象本身是线程安全的，可并发调用 getValue。</li>
 * </ul>
 *
 * <h3>异常隔离</h3>
 * <p>单条宏表达式解析失败不会阻断整个加载流程，仅打 ERROR 日志并跳过该条记录。</p>
 *
 * yu-flow
 */
@Slf4j
@Component
public class SysMacroCacheManager {

    /** Redis Pub/Sub 频道名称 */
    public static final String REFRESH_TOPIC = "flow:sys:macro:refresh:topic";

    /** SpEL 表达式解析器（线程安全，全局单例） */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * L1 本地缓存：macroCode → CachedMacro
     * <p>使用 volatile 引用，保证 reloadAll 中整体替换时的可见性。</p>
     */
    private volatile Map<String, CachedMacro> MACRO_CACHE = new ConcurrentHashMap<>(64);

    /**
     * 直接注入 Repository 而非 Service，避免与 SysMacroServiceImpl 形成循环依赖。
     * （ServiceImpl 注入 CacheManager，CacheManager 注入 Repository）
     */
    @Resource
    private SysMacroRepository sysMacroRepository;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ============================= 初始化 =============================

    /**
     * 应用启动时自动从数据库全量加载启用状态的宏定义到本地缓存。
     * <p>使用 {@code @PostConstruct} 确保在 Spring 容器初始化完成后立即执行，
     * 保证动态 API 引擎在首次请求到达前即可使用缓存。</p>
     */
    @PostConstruct
    public void init() {
        log.info("[SysMacroCacheManager] 应用启动，开始全量加载系统宏定义缓存...");
        reloadAll();
    }

    // ============================= 核心方法 =============================

    /**
     * 全量重载：从数据库加载所有启用状态的宏定义，编译 SpEL 表达式，并整体替换本地缓存。
     *
     * <p><b>实现策略：先构建 → 再替换（Copy-on-Write 思想）</b></p>
     * <ol>
     *   <li>从 DB 加载 status=1 的所有宏记录。</li>
     *   <li>逐条编译 SpEL 表达式，遇到语法错误打 ERROR 日志并跳过（异常隔离）。</li>
     *   <li>构建完成后，通过 volatile 引用赋值整体替换旧缓存。</li>
     * </ol>
     *
     * <p>这种策略保证：在重载过程中，正在执行的请求仍然可以读取旧缓存的完整数据，
     * 不会出现"部分加载"的中间状态。</p>
     */
    public void reloadAll() {
        try {
            List<SysMacroDO> activeMacros = sysMacroRepository.findAllByStatus(1);

            Map<String, CachedMacro> newCache = new ConcurrentHashMap<>(activeMacros.size() * 2);
            int successCount = 0;
            int failCount = 0;

            for (SysMacroDO macro : activeMacros) {
                try {
                    Expression compiled = PARSER.parseExpression(macro.getExpression());
                    newCache.put(macro.getMacroCode(), new CachedMacro(macro, compiled));
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("[SysMacroCacheManager] SpEL 表达式编译失败，已跳过。macroCode={}, expression={}, error={}",
                            macro.getMacroCode(), macro.getExpression(), e.getMessage());
                }
            }

            // 整体替换旧缓存（volatile 写保证可见性）
            this.MACRO_CACHE = newCache;

            log.info("[SysMacroCacheManager] 缓存重载完成。成功={}, 失败={}, 总计={}",
                    successCount, failCount, activeMacros.size());
        } catch (Exception e) {
            log.error("[SysMacroCacheManager] 全量重载缓存异常，保留旧缓存继续服务。", e);
        }
    }

    /**
     * 根据宏编码从本地缓存中获取已编译的宏定义。
     *
     * <p>时间复杂度 O(1)，无锁访问，适合在动态 API 引擎的热路径上调用。</p>
     *
     * @param macroCode 宏编码（如 "sys_user_id"）
     * @return 缓存条目，未命中返回 null
     */
    public CachedMacro getMacro(String macroCode) {
        return MACRO_CACHE.get(macroCode);
    }

    /**
     * 获取当前本地缓存的所有宏定义（只读快照）。
     *
     * @return 不可变的缓存视图
     */
    public Map<String, CachedMacro> getAllCachedMacros() {
        return java.util.Collections.unmodifiableMap(MACRO_CACHE);
    }

    /**
     * 获取当前缓存大小（调试/监控用）。
     */
    public int getCacheSize() {
        return MACRO_CACHE.size();
    }

    // ============================= Redis Pub/Sub 发布 =============================

    /**
     * 发布缓存刷新事件到 Redis Pub/Sub 频道。
     *
     * <p>调用时机：在 {@code SysMacroServiceImpl} 的 create / update / delete 方法成功执行后调用。</p>
     *
     * <p>所有订阅了 {@link #REFRESH_TOPIC} 频道的集群节点（包括当前节点）均会收到消息，
     * 触发 {@link #reloadAll()} 刷新本地缓存。</p>
     *
     * <p>发送失败时仅打日志，不影响业务主流程（降级策略：等待下次刷新或重启自动加载）。</p>
     */
    public void publishRefreshEvent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 如果在事务中，注册同步器，在事务成功提交后发布
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublishRefreshEvent();
                }
            });
            log.debug("[SysMacroCacheManager] 检测到当前处于事务中，已注册事务提交后执行缓存刷新广播的回调。");
        } else {
            // 如果不在事务中，直接发布
            doPublishRefreshEvent();
        }
    }

    /**
     * 执行真正的 Redis 消息发布逻辑
     */
    private void doPublishRefreshEvent() {
        try {
            stringRedisTemplate.convertAndSend(REFRESH_TOPIC, "REFRESH");
            log.info("[SysMacroCacheManager] 已发布缓存刷新事件到 Redis 频道: {}", REFRESH_TOPIC);
        } catch (Exception e) {
            log.error("[SysMacroCacheManager] 发布缓存刷新事件失败，尝试本地降级刷新。", e);
            // 降级：至少保证当前节点的缓存是最新的
            reloadAll();
        }
    }
}
