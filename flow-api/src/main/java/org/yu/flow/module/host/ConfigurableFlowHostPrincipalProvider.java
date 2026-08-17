package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 缺省主体解析：先按「宿主机配置」的配置式解析，取不到再回退内置 JWT。
 *
 * <p>让宿主不写 Java SPI 也能提供当前用户；Flow 管理端自身仍走 JWT，所以兜底分支不能省。
 * 宿主注册自己的 {@link FlowHostPrincipalProvider} Bean 时本类整体不装配。</p>
 */
public class ConfigurableFlowHostPrincipalProvider implements FlowHostPrincipalProvider, FlowHostBuiltinSpi {

    /** 同一请求内多次解析（OSS 先 requirePrincipal 再 resolveScope）只算一次 */
    private static final String ATTR_DONE = "yuHostPrincipalResolveDone";
    private static final String ATTR_VALUE = "yuHostPrincipalResolveValue";

    private final BuiltinJwtFlowHostPrincipalProvider builtin;
    private final HostPrincipalSettingsStore settingsStore;
    private final ObjectProvider<HostPrincipalResolver> resolver;

    private volatile Cache<String, Optional<FlowHostPrincipal>> cache;
    private volatile int cacheSeconds = -1;

    public ConfigurableFlowHostPrincipalProvider(BuiltinJwtFlowHostPrincipalProvider builtin,
                                                 HostPrincipalSettingsStore settingsStore,
                                                 ObjectProvider<HostPrincipalResolver> resolver) {
        this.builtin = builtin;
        this.settingsStore = settingsStore;
        this.resolver = resolver;
    }

    @Override
    public Optional<FlowHostPrincipal> resolve(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(request.getAttribute(ATTR_DONE))) {
            return Optional.ofNullable((FlowHostPrincipal) request.getAttribute(ATTR_VALUE));
        }
        FlowHostPrincipal resolved = doResolve(request);
        request.setAttribute(ATTR_DONE, Boolean.TRUE);
        request.setAttribute(ATTR_VALUE, resolved);
        return Optional.ofNullable(resolved);
    }

    private FlowHostPrincipal doResolve(HttpServletRequest request) {
        HostPrincipalSettings settings = settingsStore.load();
        if (settings.isEnabled()) {
            FlowHostPrincipal configured = resolveConfigured(request, settings);
            if (configured != null) {
                return configured;
            }
        }
        // 宿主会话取不到：可能是运营在访问 Flow 管理端，仍需内置 JWT 兜底
        return builtin.resolve(request).orElse(null);
    }

    private FlowHostPrincipal resolveConfigured(HttpServletRequest request, HostPrincipalSettings settings) {
        HostPrincipalResolver delegate = resolver.getIfAvailable();
        if (delegate == null) {
            return null;
        }
        int ttl = settings.resolvedCacheSeconds();
        String fingerprint = settings.isHeaderMode() ? null : fingerprint(request, settings);
        if (ttl <= 0 || fingerprint == null) {
            return delegate.resolve(request, settings, false);
        }
        Optional<FlowHostPrincipal> hit = cache(ttl)
                .get(fingerprint, ignored -> Optional.ofNullable(delegate.resolve(request, settings, false)));
        return hit != null ? hit.orElse(null) : null;
    }

    /**
     * 凭证指纹：只有同一份凭证才共享缓存结果，避免跨用户串号。
     */
    private static String fingerprint(HttpServletRequest request, HostPrincipalSettings settings) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String name : settings.resolvedForwardHeaders()) {
            String value = request.getHeader(name);
            if (StrUtil.isNotBlank(value)) {
                sb.append(name).append('=').append(value).append('\n');
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private Cache<String, Optional<FlowHostPrincipal>> cache(int ttlSeconds) {
        Cache<String, Optional<FlowHostPrincipal>> current = cache;
        if (current != null && cacheSeconds == ttlSeconds) {
            return current;
        }
        synchronized (this) {
            if (cache == null || cacheSeconds != ttlSeconds) {
                cache = Caffeine.newBuilder()
                        .maximumSize(4096)
                        .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                        .build();
                cacheSeconds = ttlSeconds;
            }
            return cache;
        }
    }
}
