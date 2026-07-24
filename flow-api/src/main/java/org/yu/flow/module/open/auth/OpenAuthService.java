package org.yu.flow.module.open.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.open.domain.FlowOpenCredentialDO;
import org.yu.flow.module.open.support.CachedBodyHttpServletRequest;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 开放平台入站鉴权（HMAC 或可选明文 Secret）。
 */
@Slf4j
@Service
public class OpenAuthService {

    public static final String HDR_APP_KEY = "X-Yu-App-Key";
    public static final String HDR_TIMESTAMP = "X-Yu-Timestamp";
    public static final String HDR_NONCE = "X-Yu-Nonce";
    public static final String HDR_SIGNATURE = "X-Yu-Signature";
    public static final String HDR_APP_SECRET = "X-Yu-App-Secret";
    public static final String ATTR_BODY_SHA256 = "yuOpenBodySha256";

    @Resource
    private OpenPlatformCache openPlatformCache;
    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;
    @Resource
    private OpenAuthValidator openAuthValidator;

    public OpenAuthContext authenticate(HttpServletRequest request, String realPath, String method) {
        return authenticate(request, realPath, method, true);
    }

    /**
     * @param antiReplay 为 false 时仍校验 HMAC 时间窗与签名，但不写入 nonce（防重放关闭）
     */
    public OpenAuthContext authenticate(HttpServletRequest request, String realPath, String method,
                                        boolean antiReplay) {
        YuFlowProperties.Open cfg = yuFlowRuntimeSettings.resolveOpen();
        String appKey = header(request, HDR_APP_KEY);
        if (StrUtil.isBlank(appKey)) {
            throw OpenAuthException.missing();
        }

        OpenPlatformCache.CachedCredential cred = openPlatformCache.getByAppKey(appKey.trim());
        if (cred == null) {
            throw OpenAuthException.invalid();
        }
        assertUsable(cred);
        assertIp(request, cred);

        String signature = header(request, HDR_SIGNATURE);
        String plainSecret = header(request, HDR_APP_SECRET);
        if (StrUtil.isNotBlank(signature)) {
            verifyHmac(request, realPath, method, cred, signature, cfg, antiReplay);
        } else if (cfg.isAllowPlainSecret() && StrUtil.isNotBlank(plainSecret)) {
            if (!constantTimeEquals(plainSecret, cred.getAppSecret())) {
                throw OpenAuthException.invalid();
            }
        } else {
            throw OpenAuthException.missing();
        }

        OpenAuthContext ctx = OpenAuthContext.builder()
                .platformId(cred.getPlatformId())
                .platformName(cred.getPlatformName())
                .appKey(cred.getAppKey())
                .credentialId(cred.getCredentialId())
                .openCallLogEnabled(cred.getOpenCallLogEnabled())
                .rateLimitQps(cred.getRateLimitQps())
                .build();
        if (openAuthValidator != null) {
            openAuthValidator.afterAuthenticated(ctx, request, realPath, method);
        }
        return ctx;
    }

    /**
     * 鉴权失败时仍尽量解析平台 ID（用于计量 / 摘要日志，fail-open）。
     */
    public OpenAuthContext peekByAppKey(String appKey) {
        if (StrUtil.isBlank(appKey)) {
            return null;
        }
        OpenPlatformCache.CachedCredential cred = openPlatformCache.getByAppKey(appKey.trim());
        if (cred == null) {
            return null;
        }
        return OpenAuthContext.builder()
                .platformId(cred.getPlatformId())
                .platformName(cred.getPlatformName())
                .appKey(cred.getAppKey())
                .credentialId(cred.getCredentialId())
                .openCallLogEnabled(cred.getOpenCallLogEnabled())
                .rateLimitQps(cred.getRateLimitQps())
                .build();
    }

    public void assertApiGranted(OpenAuthContext ctx, String apiId) {
        assertApiGranted(ctx, apiId, null);
    }

    /**
     * 校验接口授权；若授权配置了 allow_methods，则同时校验 HTTP method。
     * <p>allow_methods 为空 = 跟随接口发布 method（由网关另行校验）。</p>
     */
    public void assertApiGranted(OpenAuthContext ctx, String apiId, String requestMethod) {
        String allow = openPlatformCache.getAllowMethods(ctx.getPlatformId(), apiId);
        if (allow == null) {
            throw OpenAuthException.denied("未授权访问该接口");
        }
        if (StrUtil.isNotBlank(requestMethod) && StrUtil.isNotBlank(allow)
                && !methodAllowed(requestMethod, allow)) {
            throw OpenAuthException.methodDenied(
                    "未授权使用 " + requestMethod.toUpperCase() + " 方法（允许: " + allow + "）");
        }
    }

    /** allowMethods 如 {@code GET,POST}；空串视为不限制（跟随接口 method）。 */
    public static boolean methodAllowed(String requestMethod, String allowMethods) {
        if (StrUtil.isBlank(allowMethods)) {
            return true;
        }
        if (StrUtil.isBlank(requestMethod)) {
            return false;
        }
        String upper = requestMethod.trim().toUpperCase();
        for (String part : allowMethods.split("[,;\\s]+")) {
            if (StrUtil.isNotBlank(part) && part.trim().equalsIgnoreCase(upper)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldWriteCallLog(OpenAuthContext ctx) {
        if (!yuFlowRuntimeSettings.isOpenCallLogEnabled()) {
            return false;
        }
        if (ctx == null) {
            return true;
        }
        Integer p = ctx.getOpenCallLogEnabled();
        return p == null || p != 0;
    }

    private void verifyHmac(HttpServletRequest request, String realPath, String method,
                            OpenPlatformCache.CachedCredential cred, String signature,
                            YuFlowProperties.Open cfg, boolean antiReplay) {
        String ts = header(request, HDR_TIMESTAMP);
        String nonce = header(request, HDR_NONCE);
        if (StrUtil.isBlank(ts) || StrUtil.isBlank(nonce)) {
            throw OpenAuthException.missing();
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(ts.trim());
        } catch (Exception e) {
            throw OpenAuthException.invalid();
        }
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - timestamp) > Math.max(30, cfg.getSkewSeconds())) {
            throw OpenAuthException.expired();
        }
        if (antiReplay) {
            String nonceKey = "flow:open:nonce:" + cred.getAppKey() + ":" + nonce.trim();
            try {
                boolean ok = FlowRedisUtil.setIfAbsent(nonceKey, "1",
                        Math.max(60, cfg.getSkewSeconds() * 2L), TimeUnit.SECONDS);
                if (!ok) {
                    throw OpenAuthException.expired();
                }
            } catch (OpenAuthException e) {
                throw e;
            } catch (Exception e) {
                if (cfg.isNonceFailClosed()) {
                    log.error("[OpenAuth] nonce 写入失败（fail-closed）: {}", e.getMessage());
                    throw OpenAuthException.invalid();
                }
                log.warn("[OpenAuth] nonce 写入失败（fail-open）: {}", e.getMessage());
            }
        }

        String bodyHash = resolveBodyHash(request, cfg);
        String expect = OpenAuthSignatures.sign(
                cred.getAppSecret(), method, realPath, ts, nonce, bodyHash);
        if (!constantTimeEquals(expect, signature.trim().toLowerCase())) {
            if (!constantTimeEquals(expect, signature.trim())) {
                throw OpenAuthException.invalid();
            }
        }
    }

    private static String resolveBodyHash(HttpServletRequest request, YuFlowProperties.Open cfg) {
        if (!cfg.isIncludeBodyHash()) {
            return "";
        }
        Object attr = request.getAttribute(ATTR_BODY_SHA256);
        if (attr instanceof String s) {
            return s;
        }
        if (request instanceof CachedBodyHttpServletRequest cached) {
            byte[] body = cached.getCachedBody();
            if (body == null || body.length == 0) {
                return "";
            }
            return DigestUtil.sha256Hex(body);
        }
        return "";
    }

    private void assertUsable(OpenPlatformCache.CachedCredential cred) {
        if (cred.getPlatformStatus() == null || cred.getPlatformStatus() != 1) {
            throw OpenAuthException.denied("开放平台已停用");
        }
        Integer cs = cred.getCredentialStatus();
        if (cs == null || (cs != FlowOpenCredentialDO.STATUS_ENABLED && cs != FlowOpenCredentialDO.STATUS_ROTATED)) {
            throw OpenAuthException.denied("凭证已停用");
        }
        if (cs == FlowOpenCredentialDO.STATUS_ROTATED) {
            int grace = yuFlowRuntimeSettings.getOpenRotateGraceHours();
            if (grace <= 0) {
                throw OpenAuthException.denied("凭证已轮换失效");
            }
            if (cred.getCredentialExpireAt() != null && cred.getCredentialExpireAt().before(new Date())) {
                throw OpenAuthException.expired();
            }
        }
        Date now = new Date();
        if (cred.getPlatformExpireAt() != null && cred.getPlatformExpireAt().before(now)) {
            throw OpenAuthException.expired();
        }
        if (cs == FlowOpenCredentialDO.STATUS_ENABLED
                && cred.getCredentialExpireAt() != null
                && cred.getCredentialExpireAt().before(now)) {
            throw OpenAuthException.expired();
        }
    }

    private void assertIp(HttpServletRequest request, OpenPlatformCache.CachedCredential cred) {
        List<String> allow = cred.getIpAllowlist();
        if (allow == null || allow.isEmpty()) {
            return;
        }
        String ip = clientIp(request);
        if (StrUtil.isBlank(ip) || !ipAllowed(ip, allow)) {
            throw OpenAuthException.ipDenied();
        }
    }

    public static boolean ipAllowed(String ip, List<String> allow) {
        for (String rule : allow) {
            if (StrUtil.isBlank(rule)) {
                continue;
            }
            String r = rule.trim();
            if (r.contains("/")) {
                if (ipv4InCidr(ip, r)) {
                    return true;
                }
            } else if (r.equals(ip)) {
                return true;
            }
        }
        return false;
    }

    /** 简易 IPv4 CIDR 匹配（非法规则视为不匹配） */
    public static boolean ipv4InCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            int prefix = Integer.parseInt(parts[1].trim());
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] netBytes = InetAddress.getByName(parts[0].trim()).getAddress();
            if (ipBytes.length != 4 || netBytes.length != 4) {
                return false;
            }
            int ipInt = ((ipBytes[0] & 0xff) << 24) | ((ipBytes[1] & 0xff) << 16)
                    | ((ipBytes[2] & 0xff) << 8) | (ipBytes[3] & 0xff);
            int netInt = ((netBytes[0] & 0xff) << 24) | ((netBytes[1] & 0xff) << 16)
                    | ((netBytes[2] & 0xff) << 8) | (netBytes[3] & 0xff);
            int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
            return (ipInt & mask) == (netInt & mask);
        } catch (Exception e) {
            return false;
        }
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String clientIp(HttpServletRequest request) {
        return resolveClientIp(request);
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v == null ? null : v.trim();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(x, y);
    }
}
