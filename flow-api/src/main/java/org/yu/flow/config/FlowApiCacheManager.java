package org.yu.flow.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.AntPathMatcher;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 动态 API 路由本地缓存管理器（L1 本地缓存 + L2 Redis Pub/Sub 集群广播）
 *
 * <h3>架构概述</h3>
 * <p>参考 Spring Cloud Gateway 的路由缓存设计，将所有已发布的动态 API 路由加载到 JVM 内存，
 * 实现读操作 100% 本地化，彻底消除拦截器热路径上的 Redis 网络 I/O。</p>
 *
 * <pre>
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  FlowApiInterceptor（每次 HTTP 请求均会命中）                      │
 *   │      │                                                          │
 *   │      ▼  ① 精确匹配：O(1) HashMap 查找                            │
 *   │  exactCache (Map&lt;String, FlowApiDO&gt;)                           │
 *   │      │                                                          │
 *   │      ▼  ② Ant 模式匹配：O(N) 遍历（N 通常 &lt; 50）                  │
 *   │  patternCache (List&lt;FlowApiDO&gt;)                                │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   管理员发布/修改/删除 API → ServiceImpl.save/update/delete
 *       │
 *       ▼
 *   publishRefreshEvent()  →  Redis Pub/Sub 广播
 *       │
 *       ├── 节点 A (当前) → refreshCache()
 *       ├── 节点 B         → refreshCache()
 *       └── 节点 C         → refreshCache()
 * </pre>
 *
 * <h3>缓存分区策略</h3>
 * <ul>
 *   <li><b>exactCache</b>（精确路由）：Key 格式 {@code METHOD-/path}，例如 {@code GET-/api/user}。
 *       路径中不含 {@code {}} 和 {@code *} 等通配符。O(1) 直接命中，覆盖 90%+ 的请求。</li>
 *   <li><b>patternCache</b>（模式路由）：存放 URL 中包含 {@code {id}}、{@code **} 等 Ant 风格
 *       通配符的动态路径。逐条遍历调用 {@link AntPathMatcher#match}，时间复杂度 O(N)。</li>
 * </ul>
 *
 * <h3>线程安全说明</h3>
 * <ul>
 *   <li>exactCache 和 patternCache 均使用 {@code volatile} 引用，通过"先构建新容器，再整体替换引用"
 *       的 Copy-on-Write 策略保证刷新时的原子性。</li>
 *   <li>读端无锁、无同步，极致低延迟。</li>
 *   <li>唯一的写操作 {@link #refreshCache()} 本身不会被并发调用（由 Redis 消息顺序驱动），
 *       但即使并发写也仅是多做一次 DB 查询，不会产生数据不一致。</li>
 * </ul>
 *
 * <h3>异常隔离</h3>
 * <p>全量加载失败时保留旧缓存继续服务，仅打 ERROR 日志，不会导致服务不可用。</p>
 *
 * yu-flow
 * @see FlowApiInterceptor
 * @see SysMacroCacheManager
 */
@Slf4j
@Component
public class FlowApiCacheManager {

    // ============================= 常量 =============================

    /** Redis Pub/Sub 频道名称，用于集群间广播缓存刷新事件 */
    public static final String REFRESH_TOPIC = "flow:api:cache:refresh:topic";

    // ============================= 本地缓存 =============================

    /**
     * 精确匹配缓存：Key 格式 {@code METHOD-/path}
     * <p>使用 volatile 引用，保证 refreshCache 中整体替换时的可见性。
     * 底层使用 {@link HashMap} 即可（只在写入时整体替换，读端无并发写入）。</p>
     */
    private volatile Map<String, FlowApiDO> exactCache = new HashMap<>(64);

    /**
     * Ant 模式匹配缓存：存放 URL 中含有 {@code {}} 或 {@code *} 等通配符的动态路径
     * <p>使用 volatile 引用，整体替换保证原子性。</p>
     */
    private volatile List<FlowApiDO> patternCache = new ArrayList<>(16);

    /** 防重入/防并发全量刷新锁，防止缓存穿透风暴 */
    private final ReentrantLock refreshLock = new ReentrantLock();

    // ============================= 依赖注入 =============================

    /**
     * 直接注入 Repository 而非 Service，避免与 FlowApiService 形成循环依赖。
     * （ServiceImpl 可能注入 CacheManager，CacheManager 注入 Repository）
     */
    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ============================= 初始化 =============================

    /**
     * 应用启动时自动从数据库全量加载已发布的 API 路由到本地缓存。
     *
     * <p>使用 {@code @PostConstruct} 确保在 Spring 容器初始化完成后立即执行，
     * 保证 {@link FlowApiInterceptor} 在首次 HTTP 请求到达前即可使用缓存，无需访问 Redis。</p>
     */
    @PostConstruct
    public void initCache() {
        log.info("[FlowApiCacheManager] 应用启动，开始全量加载动态 API 路由缓存...");
        doRefreshCache();
    }

    // ============================= 核心方法 =============================

    /**
     * 刷新缓存：清空并重新加载内存缓存。
     *
     * <p><b>调用场景</b>：由 Redis Pub/Sub 监听器回调触发，当管理员对动态 API
     * 进行发布/修改/删除操作后，通过 {@link #publishRefreshEvent()} 广播消息，
     * 集群中所有节点均会执行此方法。</p>
     *
     * <p><b>线程安全策略：Copy-on-Write（先构建 → 再替换）</b></p>
     * <ol>
     *   <li>从 DB 加载 publishStatus=1 的所有 API 记录。</li>
     *   <li>按路径特征分拆为精确路由和模式路由，分别构建新的容器。</li>
     *   <li>构建完成后，通过 volatile 引用赋值整体替换旧缓存。</li>
     * </ol>
     *
     * <p>此策略保证：在刷新过程中，正在执行的请求仍然可以读取旧缓存的完整数据，
     * 不会出现"部分加载"的中间状态，实现了无锁读取下的安全刷新。</p>
     */
    public void refreshCache() {
        log.info("[FlowApiCacheManager] 收到缓存刷新通知，开始重新加载...");
        doRefreshCache();
    }

    /**
     * 执行实际的缓存加载逻辑（供 initCache 和 refreshCache 共用）。
     *
     * <p>将所有已发布的 API 按照 URL 特征分类：
     * <ul>
     *   <li>包含 {@code {}} 或 {@code *} 的路径 → 模式路由，放入 patternCache</li>
     *   <li>其余路径 → 精确路由，放入 exactCache</li>
     * </ul>
     * </p>
     */
    private void doRefreshCache() {
        // 使用 tryLock，如果已经有线程（事件）正在刷新缓存，其他并发事件直接摒弃
        // 这样即使短时间内接收到十几个 REFRESH 事件，也只发生一次真正的合并刷新
        if (!refreshLock.tryLock()) {
            log.info("[FlowApiCacheManager] 检测到缓存正在被刷新，忽略本次并发刷新请求。");
            return;
        }

        try {
            // 加载所有已发布（publishStatus=1）的 API
            List<FlowApiDO> publishedApis = flowApiRepository.findByPublishStatus(1);

            Map<String, FlowApiDO> newExactCache = new HashMap<>(publishedApis.size() * 2);
            List<FlowApiDO> newPatternCache = new ArrayList<>(16);

            int exactCount = 0;
            int patternCount = 0;

            for (FlowApiDO api : publishedApis) {
                String originalUrl = api.getUrl();
                String method = api.getMethod();

                if (originalUrl == null || method == null) {
                    log.warn("[FlowApiCacheManager] 跳过无效 API 记录：id={}, url={}, method={}",
                            api.getId(), originalUrl, method);
                    continue;
                }

                // 容错处理：确保 url 以 / 开头
                String url = originalUrl.trim();
                if (!url.startsWith("/")) {
                    url = "/" + url;
                }
                api.setUrl(url); // 规范化内存中的定义

                // 构建缓存 Key：METHOD-/path（与 FlowApiInterceptor 中的格式一致）
                String cacheKey = method.toUpperCase() + "-" + url;

                if (isPatternPath(url)) {
                    // 含有 Ant 通配符的动态路径 → 模式路由
                    newPatternCache.add(api);
                    patternCount++;
                } else {
                    // 纯静态路径 → 精确路由
                    newExactCache.put(cacheKey, api);
                    exactCount++;
                }
            }

            // ★ 整体替换旧缓存（volatile 写保证可见性，无需加锁）
            this.exactCache = newExactCache;
            this.patternCache = newPatternCache;

            log.info("[FlowApiCacheManager] 缓存加载完成。精确路由={}, 模式路由={}, 总计={}",
                    exactCount, patternCount, publishedApis.size());
        } catch (Exception e) {
            log.error("[FlowApiCacheManager] 全量加载缓存异常，保留旧缓存继续服务。", e);
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * 判断给定 URL 路径是否为 Ant 风格的模式路径。
     *
     * <p>包含以下特征之一即视为模式路径：
     * <ul>
     *   <li>{@code {}} — 路径变量占位符，如 {@code /api/user/{id}}</li>
     *   <li>{@code *} — 单级通配符，如 {@code /api/file/*.json}</li>
     *   <li>{@code **} — 多级通配符，如 {@code /api/proxy/**}</li>
     * </ul>
     * </p>
     *
     * @param url API 的 URL 路径
     * @return true 表示是模式路径，false 表示是精确路径
     */
    private boolean isPatternPath(String url) {
        return url.contains("{") || url.contains("*");
    }

    // ============================= 查询方法 =============================

    /**
     * 精确匹配：根据请求方法和路径从 exactCache 中 O(1) 查找。
     *
     * <p>这是拦截器热路径上的第一优先级匹配，覆盖 90%+ 的请求场景。
     * 无锁、无同步、无网络 I/O，延迟在纳秒级别。</p>
     *
     * @param method      HTTP 请求方法（GET, POST, PUT, DELETE 等）
     * @param requestPath 请求路径（如 {@code /api/user}）
     * @return 匹配到的 FlowApiDO 对象；未命中返回 null
     */
    public FlowApiDO getExactMatch(String method, String requestPath) {
        if (requestPath != null && !requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        String cacheKey = method.toUpperCase() + "-" + requestPath;
        return exactCache.get(cacheKey);
    }

    /**
     * Ant 模式匹配：遍历 patternCache，逐条进行 Ant 风格路径匹配。
     *
     * <p>仅在 {@link #getExactMatch} 未命中时调用。遍历所有模式路由，
     * 使用 {@link AntPathMatcher#match} 进行匹配，如果匹配成功，
     * 还会通过 {@link AntPathMatcher#extractUriTemplateVariables} 提取路径变量。</p>
     *
     * <p>时间复杂度 O(N)，其中 N 为模式路由数量（通常 &lt; 50），性能完全可控。</p>
     *
     * @param method      HTTP 请求方法（GET, POST, PUT, DELETE 等）
     * @param requestPath 请求路径（如 {@code /api/user/123}）
     * @param matcher     AntPathMatcher 实例（由调用方传入，避免重复创建）
     * @return 匹配结果 {@link AntMatchResult}；未命中返回 null
     */
    public AntMatchResult getPatternMatch(String method, String requestPath, AntPathMatcher matcher) {
        if (requestPath != null && !requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        // 读取 volatile 引用的快照，保证遍历期间引用不变
        List<FlowApiDO> snapshot = this.patternCache;

        for (FlowApiDO api : snapshot) {
            // 方法不匹配则跳过
            if (!method.equalsIgnoreCase(api.getMethod())) {
                continue;
            }

            String pattern = api.getUrl();
            if (matcher.match(pattern, requestPath)) {
                // 匹配成功，提取路径变量（如 {id} → 123）
                Map<String, String> pathVariables;
                try {
                    pathVariables = matcher.extractUriTemplateVariables(pattern, requestPath);
                } catch (Exception e) {
                    log.warn("[FlowApiCacheManager] 提取路径变量失败：pattern={}, path={}, error={}",
                            pattern, requestPath, e.getMessage());
                    pathVariables = Collections.emptyMap();
                }
                return new AntMatchResult(api, pathVariables);
            }
        }

        return null;
    }

    // ============================= Redis Pub/Sub 发布 =============================

    /**
     * 发布缓存刷新事件到 Redis Pub/Sub 频道。
     *
     * <p><b>调用时机</b>：在 {@code FlowApiServiceImpl} 的 create / update / delete / publish
     * 方法成功执行后调用。</p>
     *
     * <p>所有订阅了 {@link #REFRESH_TOPIC} 频道的集群节点（包括当前节点）均会收到消息，
     * 触发 {@link #refreshCache()} 刷新本地缓存。</p>
     *
     * <p>如果当前处于 Spring 事务中，会自动延迟到事务提交后再发布消息，
     * 避免其他节点读取到未提交的数据导致缓存不一致。</p>
     *
     * <p>发送失败时仅打日志，不影响业务主流程（降级策略：等待下次刷新或重启自动加载）。</p>
     */
    public void publishRefreshEvent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 如果在事务中，注册同步器，在事务成功提交后再发布
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublishRefreshEvent();
                }
            });
            log.debug("[FlowApiCacheManager] 检测到当前处于事务中，已注册事务提交后执行缓存刷新广播的回调。");
        } else {
            // 如果不在事务中，直接发布
            doPublishRefreshEvent();
        }
    }

    /**
     * 执行真正的 Redis 消息发布逻辑。
     *
     * <p>发送失败时降级为本地刷新，保证至少当前节点的缓存是最新的。</p>
     */
    private void doPublishRefreshEvent() {
        try {
            stringRedisTemplate.convertAndSend(REFRESH_TOPIC, "REFRESH");
            log.info("[FlowApiCacheManager] 已发布缓存刷新事件到 Redis 频道: {}", REFRESH_TOPIC);
        } catch (Exception e) {
            log.error("[FlowApiCacheManager] 发布缓存刷新事件失败，尝试本地降级刷新。", e);
            // 降级：至少保证当前节点的缓存是最新的
            doRefreshCache();
        }
    }

    // ============================= 辅助查询 =============================

    /**
     * 获取精确路由缓存大小（调试/监控用）。
     *
     * @return exactCache 中的条目数量
     */
    public int getExactCacheSize() {
        return exactCache.size();
    }

    /**
     * 获取模式路由缓存大小（调试/监控用）。
     *
     * @return patternCache 中的条目数量
     */
    public int getPatternCacheSize() {
        return patternCache.size();
    }

    // ============================= 内部类 =============================

    /**
     * Ant 模式路径匹配结果
     *
     * <p>封装了匹配成功后的 {@link FlowApiDO} 对象和从 URL 中提取的路径变量 Map。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     *   // 模式路径: /api/user/{id}
     *   // 请求路径: /api/user/123
     *   AntMatchResult result = cacheManager.getPatternMatch("GET", "/api/user/123", matcher);
     *   if (result != null) {
     *       FlowApiDO api = result.getApi();             // 匹配到的 API 定义
     *       String userId = result.getPathVariables().get("id"); // "123"
     *   }
     * }</pre>
     *
     * yu-flow
     */
    @Data
    @AllArgsConstructor
    public static class AntMatchResult {

        /**
         * 匹配到的动态 API 定义对象
         */
        private FlowApiDO api;

        /**
         * 从请求路径中提取的路径变量
         * <p>例如模式 {@code /api/user/{id}} 匹配路径 {@code /api/user/123} 时，
         * 此 Map 的内容为 {@code {"id": "123"}}。</p>
         * <p>如果模式中没有路径变量（如 {@code /api/file/**}），则为空 Map。</p>
         */
        private Map<String, String> pathVariables;
    }
}
