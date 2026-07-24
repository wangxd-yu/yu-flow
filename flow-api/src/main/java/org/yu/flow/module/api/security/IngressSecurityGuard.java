package org.yu.flow.module.api.security;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthContext;
import org.yu.flow.module.open.auth.OpenAuthException;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.open.support.IpAllowlistUtil;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 对已发布 API（非开放前缀入口）执行入站防护：IP → 鉴权 → 限流。
 */
@Slf4j
@Component
public class IngressSecurityGuard {

    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;
    @Resource
    private IngressSecurityResolver ingressSecurityResolver;
    @Resource
    private FixedWindowRateLimiter fixedWindowRateLimiter;
    @Resource
    private OpenAuthService openAuthService;
    @Resource
    private HostAuthenticationProbe hostAuthenticationProbe;

    /**
     * @return 若 OPEN 鉴权成功则返回上下文，否则 null；失败抛 {@link IngressException}
     */
    public OpenAuthContext enforce(HttpServletRequest request, FlowApiDO api,
                                   String requestPath, String requestMethod) {
        if (!yuFlowRuntimeSettings.isIngressEnabled()) {
            return null;
        }

        EffectiveSecurity sec = ingressSecurityResolver.resolve(api);

        assertIp(request, sec.getIpAllowlist());

        OpenAuthContext openCtx = null;
        switch (sec.getAuthMode()) {
            case HOST -> {
                boolean ok = hostAuthenticationProbe == null
                        || hostAuthenticationProbe.isAuthenticated(request);
                if (!ok) {
                    throw IngressException.hostAuthRequired();
                }
            }
            case OPEN -> {
                try {
                    openCtx = openAuthService.authenticate(
                            request, requestPath, requestMethod, sec.isAntiReplay());
                    openAuthService.assertApiGranted(openCtx, api.getId(), requestMethod);
                } catch (OpenAuthException e) {
                    throw IngressException.fromOpen(e);
                }
            }
            case NONE -> {
                // 运行时 fail-closed：存量接口若仍写 NONE，默认按 HOST 处理，除非显式允许匿名
                if (!yuFlowRuntimeSettings.isAllowIngressAuthNone()) {
                    boolean ok = hostAuthenticationProbe == null
                            || hostAuthenticationProbe.isAuthenticated(request);
                    if (!ok) {
                        throw IngressException.hostAuthRequired();
                    }
                }
            }
        }

        if (sec.isRateLimitEnabled() && sec.getRateLimitQps() > 0) {
            String dim = openCtx != null && StrUtil.isNotBlank(openCtx.getAppKey())
                    ? openCtx.getAppKey()
                    : "anon";
            String key = "flow:ingress:rl:" + api.getId() + ":" + dim + ":"
                    + FixedWindowRateLimiter.currentWindowSec();
            boolean failOpen = yuFlowRuntimeSettings.isIngressRateLimitFailOpen();
            if (!fixedWindowRateLimiter.tryAcquire(key, sec.getRateLimitQps(), failOpen)) {
                throw IngressException.rateLimited();
            }
        }

        return openCtx;
    }

    private void assertIp(HttpServletRequest request, String allowlistRaw) {
        if (StrUtil.isBlank(allowlistRaw)) {
            return;
        }
        List<String> allow = IpAllowlistUtil.parse(allowlistRaw);
        if (allow.isEmpty()) {
            return;
        }
        String ip = OpenAuthService.resolveClientIp(request);
        if (StrUtil.isBlank(ip) || !OpenAuthService.ipAllowed(ip, allow)) {
            throw IngressException.ipDenied();
        }
    }
}
