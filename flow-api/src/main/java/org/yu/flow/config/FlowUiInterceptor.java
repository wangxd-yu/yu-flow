package org.yu.flow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Flow 前端 UI 动态访问控制拦截器（History 路由增强版）
 *
 * <h3>核心职责</h3>
 * <p>在 Browser History 路由模式下，根据 {@code requestURI} 的真实路径
 * 区分「对外发布页面」和「内部管理页面」，实施差异化的安全策略：</p>
 *
 * <h3>路由分发逻辑</h3>
 * <pre>
 *   请求 /flow-ui/**
 *       │
 *       ├── 静态资源（.js / .css / .png 等）
 *       │       → 不经过此拦截器（由 FlowWebConfig 的 excludePathPatterns 排除）
 *       │
 *       ├── 对外发布页面（始终放行，无需鉴权）
 *       │   ├── /flow-ui/page-manage/preview/**   （Amis 生成的预览/发布页面）
 *       │   └── /flow-ui/page-manage/designer/**  （页面设计器）
 *       │
 *       └── 内部管理页面（需要 UI 开关控制）
 *           ├── /flow-ui/home
 *           ├── /flow-ui/flow/dataSource
 *           ├── /flow-ui/flow/controller
 *           └── ... 等所有其他 /flow-ui/** 路径
 *                   │
 *                   ├── UI 开关已开启 → 放行
 *                   └── UI 开关已关闭 → 返回 403 Forbidden
 * </pre>
 *
 * <h3>性能保护：5 秒本地短缓存</h3>
 * <p>前端 SPA 页面加载时会并发请求几十个静态资源（.js, .css, 图片等）。
 * 若每次请求都查 Redis，会产生大量不必要的网络 I/O。
 * 通过 5 秒的本地缓存窗口，同一波页面加载只需 1 次 Redis 调用，
 * 其余全部走纯内存判断，延迟在纳秒级别。</p>
 *
 * <h3>动态控制方式</h3>
 * <ul>
 *   <li><b>开启 UI</b>：在 Redis 中执行 {@code SET flow:uiEnable true}</li>
 *   <li><b>关闭 UI</b>：在 Redis 中执行 {@code SET flow:uiEnable false}</li>
 *   <li><b>恢复默认</b>：在 Redis 中执行 {@code DEL flow:uiEnable}（将降级使用 yml 配置）</li>
 * </ul>
 * <p>变更后最迟 5 秒内在所有节点上自动生效。</p>
 *
 * yu-flow
 * @see FlowUiAutoConfiguration
 * @see FlowWebConfig
 */
@Slf4j
@Component
public class FlowUiInterceptor implements HandlerInterceptor {

    /** Redis 键名：控制前端 UI 是否允许访问 */
    private static final String REDIS_KEY_UI_ENABLED = "flow:uiEnable";

    /** 本地缓存有效期（毫秒）：5 秒 */
    private static final long CACHE_TTL_MS = 5_000L;

    // ==================== 对外发布页面的路径前缀（始终放行） ====================
    /**
     * Amis 生成的预览/发布页面路径前缀。
     * <p>对外发布的页面无需管理权限，任何用户均可访问。</p>
     */
    private static final String PUBLIC_PATH_PREVIEW = "/flow-ui/page-manage/preview";

    /**
     * 页面设计器路径前缀。
     * <p>设计器通常由已登录的设计人员使用，但其访问控制
     * 由前端自身的 auth 逻辑负责，后端不在此拦截器中干预。</p>
     */
    private static final String PUBLIC_PATH_DESIGNER = "/flow-ui/page-manage/designer";

    /** 本地缓存的 UI 开关状态（volatile 保证多线程可见性） */
    private volatile boolean cachedUiEnabled = true;

    /** 上次查询 Redis 的时间戳 */
    private volatile long lastCheckTime = 0L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private YuFlowProperties flowProperties;

    /**
     * 请求预处理：根据路径分发安全策略
     *
     * <p>执行顺序：</p>
     * <ol>
     *   <li>判断是否为对外发布页面（preview / designer） → 直接放行</li>
     *   <li>判断 UI 开关是否开启 → 放行或拦截</li>
     * </ol>
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 使用 getServletPath() 而非 getRequestURI()，因为：
        // - getRequestURI() = "/flow/flow-ui/home"（包含 contextPath）
        // - getServletPath() = "/flow-ui/home"（不含 contextPath）
        // 这样 PUBLIC_PATH_PREVIEW 等常量的 startsWith 判断在有无 contextPath 时行为一致
        String requestURI = request.getServletPath();

        // ---- 第一优先级：对外发布页面，无条件放行 ----
        // preview 和 designer 是面向终端用户的页面，不受管理后台 UI 开关控制
        if (requestURI.startsWith(PUBLIC_PATH_PREVIEW) || requestURI.startsWith(PUBLIC_PATH_DESIGNER)) {
            log.debug("[FlowUiInterceptor] 对外发布页面，直接放行：{}", requestURI);
            return true;
        }

        // ---- 第二优先级：内部管理页面，检查 UI 开关 ----
        // 所有其他 /flow-ui/** 路径视为后台管理页面，需要通过 UI 开关控制访问
        if (isUiEnabled()) {
            return true;
        }

        // UI 已关闭，返回 403 Forbidden
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.getWriter().write("{\"error\": \"Flow UI 管理页面已关闭。请联系管理员开放访问权限。\"}");

        log.debug("[FlowUiInterceptor] 已拦截管理页面访问请求：{}", requestURI);
        return false;
    }

    /**
     * 判断 UI 是否启用（带 5 秒本地缓存保护）。
     *
     * <p>在缓存有效期内直接返回本地缓存值，避免频繁的 Redis 网络调用。
     * 超过有效期后才查询一次 Redis 并刷新缓存。</p>
     *
     * @return true 表示 UI 已启用，允许访问
     */
    private boolean isUiEnabled() {
        long now = System.currentTimeMillis();

        // 缓存未过期，直接返回本地值（零网络 I/O）
        if (now - lastCheckTime < CACHE_TTL_MS) {
            return cachedUiEnabled;
        }

        // 缓存过期，查询 Redis 并刷新
        try {
            String redisValue = stringRedisTemplate.opsForValue().get(REDIS_KEY_UI_ENABLED);

            if (redisValue != null) {
                // Redis 有值，以 Redis 为准
                cachedUiEnabled = "true".equalsIgnoreCase(redisValue.trim())
                        || "1".equals(redisValue.trim());
            } else {
                // Redis 无值，降级使用 application.yml 的静态配置
                cachedUiEnabled = flowProperties.isEnableUi();
            }
        } catch (Exception e) {
            // Redis 不可用时，降级使用 application.yml 配置
            log.warn("[FlowUiInterceptor] 查询 Redis UI 开关失败，降级使用本地配置。error={}", e.getMessage());
            cachedUiEnabled = flowProperties.isEnableUi();
        }

        lastCheckTime = now;
        return cachedUiEnabled;
    }
}
