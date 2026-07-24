package org.yu.flow.auto.util;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSignerUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 管理端 JWT 工具。
 *
 * <p>密钥通过 {@link #init(String, long)} 注入（见 {@code yu.flow.security.jwt-secret-key}），
 * 校验时拒绝 {@code alg=none} 及非 HMAC 算法。</p>
 *
 * @author yu-flow
 */
public final class JwtTokenUtil {

    /** 历史默认密钥；生产务必通过环境变量覆盖 */
    public static final String LEGACY_DEFAULT_SECRET = "ss-flow-699";

    private static volatile String secretKey = LEGACY_DEFAULT_SECRET;
    private static volatile long expireSeconds = 60L * 120;
    private static final String TOKEN_HEADER = "Bearer ";

    private JwtTokenUtil() {
    }

    /**
     * 由 Spring 启动时注入密钥与过期时间。
     */
    public static void init(String secret, long expireSec) {
        if (secret != null && !secret.isBlank()) {
            secretKey = secret.trim();
        }
        if (expireSec > 0) {
            expireSeconds = expireSec;
        }
    }

    public static String getSecretKeyFingerprint() {
        // 仅用于日志提示，不输出原文
        String s = secretKey == null ? "" : secretKey;
        return "len=" + s.length() + ",legacyDefault=" + LEGACY_DEFAULT_SECRET.equals(s);
    }

    public static boolean isUsingLegacyDefaultSecret() {
        return LEGACY_DEFAULT_SECRET.equals(secretKey);
    }

    public static String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader("Flow-Authorization");
        if (bearerToken != null && bearerToken.startsWith(TOKEN_HEADER)) {
            return bearerToken.substring(TOKEN_HEADER.length()).trim();
        }
        return null;
    }

    public static boolean validateToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new ValidateException("token 不合法");
            }
            assertAllowedAlgorithm(token);
            byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);
            JWT jwt = JWT.of(token).setSigner(JWTSignerUtil.hs256(key));
            if (!jwt.verify()) {
                throw new ValidateException("token 不合法");
            }
            JWTValidator.of(token).validateDate();
        } catch (ValidateException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidateException("token 不合法");
        }
        return true;
    }

    /**
     * 拒绝 alg=none / 空算法 / 非 HS* 算法，防止无签名伪造。
     */
    static void assertAllowedAlgorithm(String token) {
        JWT parsed = JWT.of(token);
        Object algObj = parsed.getHeader(JWTHeader.ALGORITHM);
        if (algObj == null) {
            throw new ValidateException("token 不合法");
        }
        String alg = String.valueOf(algObj).trim().toUpperCase(Locale.ROOT);
        if (alg.isEmpty() || "NONE".equals(alg) || "NULL".equals(alg)) {
            throw new ValidateException("token 不合法");
        }
        if (!alg.startsWith("HS")) {
            throw new ValidateException("token 不合法");
        }
    }

    public static String generateToken(String username, String password) {
        return generateToken(username, null, null);
    }

    /**
     * @param userId 可为 null（yml 兜底账号）
     * @param roles  可为 null
     */
    public static String generateToken(String username, String userId, java.util.List<String> roles) {
        long expiration = System.currentTimeMillis() / 1000 + expireSeconds;
        Map<String, Object> info = new HashMap<>();
        info.put(JWTPayload.EXPIRES_AT, expiration);
        info.put("username", username);
        if (userId != null && !userId.isBlank()) {
            info.put("userId", userId);
        }
        if (roles != null && !roles.isEmpty()) {
            info.put("roles", roles);
        }
        byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);
        return JWTUtil.createToken(info, JWTSignerUtil.hs256(key));
    }

    public static String getUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Object userId = JWT.of(token).getPayload("userId");
            return userId != null ? userId.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 JWT 解析 username（不校验签名；调用方应先 validateToken） */
    public static String getUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Object username = JWT.of(token).getPayload("username");
            return username != null ? username.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从当前请求解析登录用户名；无请求 / 无 Token / 解析失败时返回 null。
     */
    public static String currentUsername() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            String token = resolveToken(attrs.getRequest());
            return getUsername(token);
        } catch (Exception e) {
            return null;
        }
    }
}
