package org.yu.flow.auto.util;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;

import javax.servlet.http.HttpServletRequest;
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
}
