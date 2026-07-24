package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.yu.flow.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 回归用例输入消毒：防 SSRF/密钥入库/JSONPath 滥用/DoS。
 */
public final class RegressionSecurity {

    public static final int MAX_CASES_PER_SUITE = 20;
    public static final int MAX_BODY_CHARS = 32 * 1024;
    public static final int MAX_HEADER_KEYS = 20;
    public static final int MAX_QUERY_KEYS = 20;
    public static final int MAX_TIMEOUT_MS = 10_000;
    public static final int MAX_CONCURRENT_RUNS = 3;
    public static final int MAX_BATCH_ASSETS = 20;
    public static final int MAX_DETAIL_CHARS = 500;
    public static final int MAX_JSON_PATH_LEN = 128;

    /** 仅允许简单路径：$.a.b[0].c — 禁止 filter/script */
    private static final Pattern SAFE_JSON_PATH = Pattern.compile(
            "^\\$[a-zA-Z0-9_\\.\\[\\]\\-]*$");

    private static final Pattern FORBIDDEN_HEADER = Pattern.compile(
            "^(authorization|cookie|set-cookie|proxy-authorization|x-api-key|x-auth-token|.*secret.*|.*token.*|.*password.*)$",
            Pattern.CASE_INSENSITIVE);

    private RegressionSecurity() {
    }

    public static void validateJsonPath(String path) {
        if (StrUtil.isBlank(path)) {
            return;
        }
        String p = path.trim();
        if (p.length() > MAX_JSON_PATH_LEN) {
            throw new ValidationException("expectJsonPath 过长（≤" + MAX_JSON_PATH_LEN + "）");
        }
        if (p.contains("..") || p.contains("(") || p.contains(")") || p.contains("@") || p.contains("?")) {
            throw new ValidationException("expectJsonPath 仅支持简单路径，禁止 filter/脚本表达式");
        }
        if (!SAFE_JSON_PATH.matcher(p).matches()) {
            throw new ValidationException("expectJsonPath 含非法字符");
        }
    }

    public static String sanitizeBody(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() > MAX_BODY_CHARS) {
            throw new ValidationException("请求体过大（≤32KB）");
        }
        return body;
    }

    public static String sanitizeHeadersJson(String headersJson) {
        if (StrUtil.isBlank(headersJson)) {
            return null;
        }
        Map<String, String> map = parseStringMap(headersJson, "headersJson");
        if (map.size() > MAX_HEADER_KEYS) {
            throw new ValidationException("请求头键数量过多（≤" + MAX_HEADER_KEYS + "）");
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            String key = StrUtil.trim(e.getKey());
            if (StrUtil.isBlank(key)) {
                continue;
            }
            if (FORBIDDEN_HEADER.matcher(key).matches()) {
                throw new ValidationException("禁止在回归用例中保存敏感请求头: " + key);
            }
            String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
            if (val.length() > 1024) {
                throw new ValidationException("请求头值过长: " + key);
            }
            cleaned.put(key, val);
        }
        return cleaned.isEmpty() ? null : JSONUtil.toJsonStr(cleaned);
    }

    public static String sanitizeQueryJson(String queryJson) {
        if (StrUtil.isBlank(queryJson)) {
            return null;
        }
        Map<String, String> map = parseStringMap(queryJson, "queryJson");
        if (map.size() > MAX_QUERY_KEYS) {
            throw new ValidationException("Query 键数量过多（≤" + MAX_QUERY_KEYS + "）");
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            String key = StrUtil.trim(e.getKey());
            if (StrUtil.isBlank(key)) {
                continue;
            }
            String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
            if (val.length() > 2048) {
                throw new ValidationException("Query 值过长: " + key);
            }
            cleaned.put(key, val);
        }
        return cleaned.isEmpty() ? null : JSONUtil.toJsonStr(cleaned);
    }

    public static int clampTimeout(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return MAX_TIMEOUT_MS;
        }
        return Math.min(timeoutMs, MAX_TIMEOUT_MS);
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    public static Map<String, String> parseStringMap(String json, String field) {
        try {
            Object parsed = JSONUtil.parse(json);
            if (!(parsed instanceof Map)) {
                throw new ValidationException(field + " 必须是 JSON 对象");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) parsed;
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                out.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
            return out;
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException(field + " 不是合法 JSON");
        }
    }

    public static String normalizeAssetType(String assetType) {
        if (StrUtil.isBlank(assetType)) {
            throw new ValidationException("assetType 不能为空");
        }
        String t = assetType.trim().toUpperCase(Locale.ROOT);
        if (!"API".equals(t) && !"TASK".equals(t) && !"SERVICE".equals(t)) {
            throw new ValidationException("assetType 仅支持 API/TASK/SERVICE");
        }
        return t;
    }

    public static String normalizeEnvCode(String envCode) {
        if (StrUtil.isBlank(envCode)) {
            return "DEV";
        }
        return envCode.trim().toUpperCase(Locale.ROOT);
    }
}
