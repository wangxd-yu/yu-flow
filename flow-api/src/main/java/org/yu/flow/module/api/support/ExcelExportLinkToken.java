package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对外 Excel 短期下载链：payload.hmac（Base64URL）
 */
public final class ExcelExportLinkToken {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private ExcelExportLinkToken() {
    }

    public static String issue(YuFlowProperties props, String apiId, String uid,
                               ApiDataExportRequestDTO params, int ttlSeconds) {
        return issue(props, apiId, uid, params, ttlSeconds, "MASK");
    }

    public static String issue(YuFlowProperties props, String apiId, String uid,
                               ApiDataExportRequestDTO params, int ttlSeconds, String privacyClass) {
        long now = Instant.now().getEpochSecond();
        long exp = now + Math.max(1, ttlSeconds);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("apiId", apiId);
        payload.put("uid", StrUtil.blankToDefault(uid, ""));
        payload.put("iat", now);
        payload.put("exp", exp);
        payload.put("pc", normalizePrivacyClass(privacyClass));
        payload.put("q", params == null || params.getQueryParams() == null ? Map.of() : params.getQueryParams());
        payload.put("b", params == null || params.getBodyParams() == null ? Map.of() : params.getBodyParams());
        payload.put("p", params == null || params.getPathParams() == null ? Map.of() : params.getPathParams());
        try {
            String body = MAPPER.writeValueAsString(payload);
            String bodyPart = B64.encodeToString(body.getBytes(StandardCharsets.UTF_8));
            String sig = sign(props, bodyPart);
            return bodyPart + "." + sig;
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException("签发下载链失败");
        }
    }

    @SuppressWarnings("unchecked")
    public static Parsed verify(YuFlowProperties props, String token) {
        if (StrUtil.isBlank(token) || !token.contains(".")) {
            throw new ValidationException("下载链无效");
        }
        int dot = token.indexOf('.');
        String bodyPart = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        String expect = sign(props, bodyPart);
        if (!MessageDigestEquals(expect, sig)) {
            throw new ValidationException("下载链签名校验失败");
        }
        try {
            byte[] raw = B64D.decode(bodyPart);
            Map<String, Object> payload = MAPPER.readValue(raw, Map.class);
            Object expObj = payload.get("exp");
            long exp = expObj instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(expObj));
            if (Instant.now().getEpochSecond() > exp) {
                throw new ValidationException("下载链已过期");
            }
            String apiId = String.valueOf(payload.get("apiId"));
            if (StrUtil.isBlank(apiId) || "null".equals(apiId)) {
                throw new ValidationException("下载链缺少接口 ID");
            }
            ApiDataExportRequestDTO req = new ApiDataExportRequestDTO();
            req.setUseDraft(false);
            req.setQueryParams(toStringMap(payload.get("q")));
            req.setPathParams(toStringMap(payload.get("p")));
            req.setBodyParams(toObjectMap(payload.get("b")));
            String privacyClass = normalizePrivacyClass(payload.get("pc") == null ? null : String.valueOf(payload.get("pc")));
            return new Parsed(apiId, req, exp, privacyClass);
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException("下载链解析失败");
        }
    }

    private static String sign(YuFlowProperties props, String bodyPart) {
        String key = props != null && props.getSecurity() != null
                ? props.getSecurity().getJwtSecretKey()
                : null;
        if (key == null || key.isBlank() || "ss-flow-699".equals(key)) {
            throw new ValidationException("JWT 密钥未配置或为不安全默认值，无法签发下载链");
        }
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, key.getBytes(StandardCharsets.UTF_8));
        return B64.encodeToString(hmac.digest(bodyPart.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean MessageDigestEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> toStringMap(Object o) {
        Map<String, String> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null && v != null) {
                    out.put(String.valueOf(k), String.valueOf(v));
                }
            });
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toObjectMap(Object o) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
        }
        return out;
    }

    public static String normalizePrivacyClass(String raw) {
        if (raw != null && "REVEAL".equalsIgnoreCase(raw.trim())) {
            return "REVEAL";
        }
        return "MASK";
    }

    public record Parsed(String apiId, ApiDataExportRequestDTO request, long expEpochSec, String privacyClass) {
    }
}
