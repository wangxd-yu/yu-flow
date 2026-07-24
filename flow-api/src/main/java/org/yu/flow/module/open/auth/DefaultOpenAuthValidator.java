package org.yu.flow.module.open.auth;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.security.FixedWindowRateLimiter;

/**
 * OSS 默认限流：按平台 {@code rateLimitQps} 做秒级固定窗口；未配置则不限。
 */
@Slf4j
@Component
public class DefaultOpenAuthValidator implements OpenAuthValidator {

    @Resource
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Override
    public void afterAuthenticated(OpenAuthContext ctx, HttpServletRequest request, String realPath, String method) {
        if (ctx == null || ctx.getRateLimitQps() == null || ctx.getRateLimitQps() <= 0) {
            return;
        }
        int qps = ctx.getRateLimitQps();
        String key = "flow:open:rl:" + ctx.getPlatformId() + ":" + FixedWindowRateLimiter.currentWindowSec();
        if (!fixedWindowRateLimiter.tryAcquire(key, qps, true)) {
            throw OpenAuthException.rateLimited();
        }
    }
}
