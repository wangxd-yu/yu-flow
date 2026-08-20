package org.yu.flow.module.api.security;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.host.CallerPolicy;
import org.yu.flow.module.host.CallerPolicyMatcher;
import org.yu.flow.module.host.FlowHostIdentityCatalogService;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.FlowHostPrincipalProvider;
import org.yu.flow.module.host.FlowHostRequestAttrs;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;
import org.yu.flow.module.open.auth.OpenAuthContext;
import org.yu.flow.module.open.auth.OpenAuthException;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.open.support.IpAllowlistUtil;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 对已发布 API（非开放前缀入口）执行入站防护：IP → 鉴权 → 调用方策略 → 限流。
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
    @Resource
    private FlowHostPrincipalProvider flowHostPrincipalProvider;
    @Resource
    private FlowHostIdentityCatalogService hostIdentityCatalogService;

    /**
     * @return 若 OPEN 鉴权成功则返回上下文，否则 null；失败抛 {@link IngressException}
     */
    public OpenAuthContext enforce(HttpServletRequest request, FlowApiDO api,
                                   String requestPath, String requestMethod) {
        if (!yuFlowRuntimeSettings.isIngressEnabled()) {
            return null;
        }

        EffectiveSecurity sec = ingressSecurityResolver.resolve(api);
        CallerPolicy callerPolicy = sec.getCallerPolicy();

        assertIp(request, sec.getIpAllowlist());

        OpenAuthContext openCtx = null;
        switch (sec.getAuthMode()) {
            case HOST -> {
                boolean ok = hostAuthenticationProbe == null
                        || hostAuthenticationProbe.isAuthenticated(request);
                if (!ok) {
                    throw IngressException.hostAuthRequired();
                }
                bindHostPrincipal(request, callerPolicy);
            }
            case OPEN -> {
                try {
                    openCtx = openAuthService.authenticate(
                            request, requestPath, requestMethod, sec.isAntiReplay());
                    openAuthService.assertApiGranted(openCtx, api.getId(), requestMethod);
                } catch (OpenAuthException e) {
                    throw IngressException.fromOpen(e);
                }
                // OPEN 以 grant 为准；挂载合成主体供流程读取，不跑 callerPolicy 匹配
                FlowHostRequestAttrs.bind(request, openAppPrincipal(openCtx));
                if (callerPolicy != null && callerPolicy.isEnabled()) {
                    log.debug("[Ingress] callerPolicy 在 authMode=OPEN 下忽略匹配, apiId={}",
                            api != null ? api.getId() : null);
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
                    bindHostPrincipal(request, callerPolicy);
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

    private void bindHostPrincipal(HttpServletRequest request, CallerPolicy callerPolicy) {
        FlowHostPrincipal principal = resolveHostPrincipal(request);
        if (principal == null) {
            log.warn("[Ingress] HOST 已登录但未解析到主体，出站隐私将按 MASK");
        }
        FlowHostRequestAttrs.bind(request, principal);
        assertCallerPolicyForHost(callerPolicy, principal);
    }

    private FlowHostPrincipal resolveHostPrincipal(HttpServletRequest request) {
        if (flowHostPrincipalProvider == null) {
            return null;
        }
        return flowHostPrincipalProvider.resolve(request).orElse(null);
    }

    private void assertCallerPolicyForHost(CallerPolicy callerPolicy, FlowHostPrincipal principal) {
        if (callerPolicy == null || !callerPolicy.isEnabled()) {
            return;
        }
        CallerPolicy effective = hostIdentityCatalogService != null
                ? hostIdentityCatalogService.effectivePolicy(callerPolicy)
                : callerPolicy;
        String deny = CallerPolicyMatcher.denyReason(effective, principal);
        if (deny != null) {
            if (principal == null) {
                throw IngressException.hostAuthRequired();
            }
            throw IngressException.callerDenied(deny);
        }
    }

    private static FlowHostPrincipal openAppPrincipal(OpenAuthContext openCtx) {
        if (openCtx == null) {
            return null;
        }
        String appKey = openCtx.getAppKey();
        return FlowHostPrincipal.builder()
                .userId(appKey)
                .username(StrUtil.blankToDefault(openCtx.getPlatformName(), appKey))
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .roles(Collections.emptySet())
                .permissions(Collections.emptySet())
                .authChannel("OPEN_APP")
                .attributes(Map.of(
                        "platformId", StrUtil.blankToDefault(openCtx.getPlatformId(), ""),
                        "appKey", StrUtil.blankToDefault(appKey, "")
                ))
                .build();
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
