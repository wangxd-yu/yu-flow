package org.yu.flow.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.util.ReflectionUtils;
import org.yu.flow.util.AfterCommitExecutor;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *   publishRefreshEvent(apiId)  →  事务提交后
 *       │
 *       ├── 节点 A (当前) → refreshApis(apiId)，立即生效
 *       └── Redis 广播 → 节点 B / C → refreshApis(apiId)
 *
 *   批量导入、回滚等改动面不确定的场景 publishRefreshEvent() → 各节点全量重载
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
 *   <li>全量与按接口刷新共用一把写锁，在锁内读库并替换，避免旧数据覆盖新数据。</li>
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

    /**
     * 本进程标识：发布节点在提交后已经本地生效，收到自己发出的广播时跳过。
     * 广播格式 {@code REFRESH;node=…}（全量）或 {@code API;node=…;ids=a,b}（按接口）；
     * 旧版本节点不解析消息体，一律全量重载，滚动升级期间仍然一致。
     */
    static final String NODE_ID = UUID.randomUUID().toString().replace("-", "");

    /** 单次按接口刷新的上限，超过直接全量 */
    static final int MAX_INCREMENTAL_IDS = 200;

    private static final String TX_COLLECT_KEY = "flow api route refresh";

    /** 与全量加载的库内顺序（主键序）一致，保证模式路由的匹配优先级不因增量刷新而改变 */
    private static final Comparator<FlowApiDO> BY_ID =
            Comparator.comparing(FlowApiDO::getId, Comparator.nullsLast(Comparator.naturalOrder()));

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

    /** 全量与增量刷新共用的写锁 */
    private final ReentrantLock refreshLock = new ReentrantLock();

    /**
     * 有全量重载待执行。持锁方（全量或增量）退出前会检查并补做，短时间内的多次全量请求只合并成一次，
     * 也不会因为恰好有增量刷新在进行而被丢掉。
     */
    private final AtomicBoolean fullReloadPending = new AtomicBoolean();

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
        requestFullReload();
    }

    // ============================= 核心方法 =============================

    /**
     * 全量重载：从库里重新加载全部已发布接口。
     *
     * <p>用于启动、批量导入 / 回滚、旧版本节点的广播，以及增量刷新失败时兜底。
     * 先构建新容器再整体替换引用，刷新过程中正在执行的请求仍读旧缓存的完整数据。</p>
     */
    public void refreshCache() {
        log.info("[FlowApiCacheManager] 收到全量刷新请求，开始重新加载...");
        requestFullReload();
    }

    /**
     * 按接口刷新：只重新读取这几条记录，在现有缓存副本上替换后整体换入。
     * 已删除、已下线的接口从路由表移除；已发布的按发布快照重新登记。
     */
    public void refreshApis(Collection<String> apiIds) {
        Set<String> ids = cleanIds(apiIds);
        if (ids.isEmpty()) {
            return;
        }
        refreshLock.lock();
        try {
            List<FlowApiDO> rows = flowApiRepository.findAllById(ids);
            Map<String, FlowApiDO> newExactCache = new HashMap<>(this.exactCache);
            newExactCache.values().removeIf(api -> ids.contains(api.getId()));
            List<FlowApiDO> newPatternCache = new ArrayList<>(this.patternCache);
            newPatternCache.removeIf(api -> ids.contains(api.getId()));

            int routed = 0;
            for (FlowApiDO api : rows) {
                if (Integer.valueOf(1).equals(api.getPublishStatus()) && addRoute(api, newExactCache, newPatternCache)) {
                    routed++;
                }
            }
            newPatternCache.sort(BY_ID);

            this.exactCache = newExactCache;
            this.patternCache = newPatternCache;
            log.info("[FlowApiCacheManager] 按接口刷新完成：请求 {} 条，在线 {} 条。精确路由={}, 模式路由={}",
                    ids.size(), routed, newExactCache.size(), newPatternCache.size());
        } catch (Exception e) {
            log.error("[FlowApiCacheManager] 按接口刷新失败，改为全量重载。ids={}", ids, e);
            fullReloadPending.set(true);
        } finally {
            refreshLock.unlock();
        }
        drainFullReload();
    }

    private void requestFullReload() {
        fullReloadPending.set(true);
        drainFullReload();
    }

    /**
     * 拿不到锁说明有刷新在进行，持锁方退出前会看到待执行标记并补做；
     * 解锁后再检查一次，避免标记恰好在持锁方最后一次检查之后才置上。
     */
    private void drainFullReload() {
        while (fullReloadPending.get() && refreshLock.tryLock()) {
            try {
                while (fullReloadPending.getAndSet(false)) {
                    loadAll();
                }
            } finally {
                refreshLock.unlock();
            }
        }
    }

    /** 调用方须持有 {@link #refreshLock} */
    private void loadAll() {
        try {
            List<FlowApiDO> publishedApis = flowApiRepository.findByPublishStatus(1);

            Map<String, FlowApiDO> newExactCache = new HashMap<>(publishedApis.size() * 2);
            List<FlowApiDO> newPatternCache = new ArrayList<>(16);
            for (FlowApiDO api : publishedApis) {
                addRoute(api, newExactCache, newPatternCache);
            }
            newPatternCache.sort(BY_ID);

            this.exactCache = newExactCache;
            this.patternCache = newPatternCache;

            log.info("[FlowApiCacheManager] 全量加载完成。精确路由={}, 模式路由={}, 已发布总计={}",
                    newExactCache.size(), newPatternCache.size(), publishedApis.size());
        } catch (Exception e) {
            log.error("[FlowApiCacheManager] 全量加载缓存异常，保留旧缓存继续服务。", e);
        }
    }

    /**
     * 按发布快照登记一条路由。缓存里放的是副本：发布节点在请求线程上刷新时，
     * 查出来的可能就是本次请求持久化上下文里的实体，直接改它的 url / method 有可能被后续 flush 写回草稿。
     *
     * @return 是否登记成功
     */
    private boolean addRoute(FlowApiDO source, Map<String, FlowApiDO> exact, List<FlowApiDO> pattern) {
        if (HostCatalogReserved.isReservedId(source.getId())) {
            return false;
        }
        // 路由键取发布快照，避免草稿改 URL 污染线上匹配
        String originalUrl = PublishedApiSnapshot.resolveUrl(source);
        String method = PublishedApiSnapshot.resolveMethod(source);
        if (originalUrl == null || method == null) {
            log.warn("[FlowApiCacheManager] 跳过无效 API 记录：id={}, url={}, method={}",
                    source.getId(), originalUrl, method);
            return false;
        }
        String url = originalUrl.trim();
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        // 实体的 setter 是链式的（返回 this），按字段拷贝而不是按属性
        FlowApiDO api = new FlowApiDO();
        ReflectionUtils.shallowCopyFieldState((FlowApiDO) Hibernate.unproxy(source), api);
        api.setUrl(url);
        api.setMethod(method.toUpperCase());

        if (isPatternPath(url)) {
            pattern.add(api);
        } else {
            // 构建缓存 Key：METHOD-/path（与 FlowApiInterceptor 中的格式一致）
            exact.put(api.getMethod() + "-" + url, api);
        }
        return true;
    }

    private static Set<String> cleanIds(Collection<String> apiIds) {
        Set<String> ids = new LinkedHashSet<>();
        if (apiIds != null) {
            for (String id : apiIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id.trim());
                }
            }
        }
        return ids;
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
        if (method == null) {
            return null;
        }
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
            if (api == null || api.getMethod() == null || method == null) {
                continue;
            }
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

    // ============================= 刷新广播 =============================

    /**
     * 全量刷新（批量导入、回滚等改动面不确定的场景）。
     *
     * <p>在事务内时推迟到提交后；同一事务内的多次调用（含按接口的调用）合并为一次。
     * 本节点在提交后立即生效，再通过 Redis 广播给其它节点。</p>
     */
    public void publishRefreshEvent() {
        AfterCommitExecutor.<Set<String>>collect(TX_COLLECT_KEY, null, this::dispatch);
    }

    /** 只刷新这一个接口的路由 */
    public void publishRefreshEvent(String apiId) {
        publishRefreshEvent(Collections.singletonList(apiId));
    }

    /** 只刷新这些接口的路由 */
    public void publishRefreshEvent(Collection<String> apiIds) {
        Set<String> ids = cleanIds(apiIds);
        if (ids.isEmpty()) {
            return;
        }
        AfterCommitExecutor.collect(TX_COLLECT_KEY, ids, this::dispatch);
    }

    /** 提交后执行：本节点先生效，再广播。任一请求为全量（null）或 ID 太多时整体按全量处理 */
    private void dispatch(List<Set<String>> requests) {
        boolean full = false;
        Set<String> ids = new LinkedHashSet<>();
        for (Set<String> request : requests) {
            if (request == null) {
                full = true;
            } else {
                ids.addAll(request);
            }
        }
        if (!full && ids.size() > MAX_INCREMENTAL_IDS) {
            full = true;
        }
        if (!full && ids.isEmpty()) {
            return;
        }

        if (full) {
            requestFullReload();
        } else {
            refreshApis(ids);
        }

        String body = full
                ? "REFRESH;node=" + NODE_ID
                : "API;node=" + NODE_ID + ";ids=" + String.join(",", ids);
        try {
            stringRedisTemplate.convertAndSend(REFRESH_TOPIC, body);
            log.info("[FlowApiCacheManager] 已广播路由刷新：{}", full ? "全量" : ids.size() + " 个接口");
        } catch (Exception e) {
            // 本节点已生效；其它节点要等下一次广播或重启
            log.error("[FlowApiCacheManager] 广播路由刷新失败，其它节点暂未更新。", e);
        }
    }

    /**
     * 处理集群广播。自己发出的消息跳过（发布时已本地生效）；
     * 旧版本节点只发 {@code REFRESH}，按全量处理。
     */
    public void onRefreshMessage(String body) {
        String type = null;
        String node = null;
        String ids = null;
        if (body != null) {
            String[] parts = body.trim().split(";");
            type = parts[0];
            for (int i = 1; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = parts[i].substring(0, eq);
                String value = parts[i].substring(eq + 1);
                if ("node".equals(key)) {
                    node = value;
                } else if ("ids".equals(key)) {
                    ids = value;
                }
            }
        }
        if (NODE_ID.equals(node)) {
            return;
        }
        if ("API".equals(type) && ids != null) {
            refreshApis(Arrays.asList(ids.split(",")));
        } else {
            refreshCache();
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

    /** 该接口是否在本节点路由表里 */
    public boolean isRouted(String apiId) {
        if (apiId == null) {
            return false;
        }
        for (FlowApiDO api : exactCache.values()) {
            if (apiId.equals(api.getId())) {
                return true;
            }
        }
        for (FlowApiDO api : patternCache) {
            if (apiId.equals(api.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 本节点上这条路由（按登记时的 method + url 原样比较，不做模式匹配）归哪个接口；没有返回 null。
     */
    public FlowApiDO routeOwner(String method, String url) {
        if (method == null || url == null) {
            return null;
        }
        String m = method.toUpperCase();
        String u = url.trim().startsWith("/") ? url.trim() : "/" + url.trim();
        FlowApiDO exact = exactCache.get(m + "-" + u);
        if (exact != null) {
            return exact;
        }
        for (FlowApiDO api : patternCache) {
            if (m.equals(api.getMethod()) && u.equals(api.getUrl())) {
                return api;
            }
        }
        return null;
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
