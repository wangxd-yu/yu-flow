package org.yu.flow.auto.util;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author yu-flow
 * @date 2021-12-10 15:50
 */
public class JwtTokenUtil {

    private static String secretKey = "ss-flow-699";
    private static String tokenHeader = "Bearer ";

    public static String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader("Flow-Authorization");
        if (bearerToken != null && bearerToken.startsWith(tokenHeader)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public static boolean validateToken(String token) {
        try {
            if (!JWTUtil.verify(token, secretKey.getBytes(StandardCharsets.UTF_8))) {
                throw new ValidateException("token 不合法");
            }
            JWTValidator.of(token).validateDate();
        } catch (Exception e) {
            throw new ValidateException("token 不合法");
        }
        return true;
    }

    public static String generateToken(String username, String password) {
        long expiresIn = 60L * 120;
        long expiration = System.currentTimeMillis() / 1000 + expiresIn;
        Map<String, Object> info = new HashMap<>();
        info.put(JWTPayload.EXPIRES_AT, expiration);
        info.put("username", username);
        return JWTUtil.createToken(info, secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /** 从 JWT 解析 username（不校验签名；调用方应先 validateToken） */
    public static String getUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Object username = JWTUtil.parseToken(token).getPayload("username");
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
