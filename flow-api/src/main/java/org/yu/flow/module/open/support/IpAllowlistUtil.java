package org.yu.flow.module.open.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * IP 白名单解析与校验（单 IP / IPv4 CIDR）。
 */
public final class IpAllowlistUtil {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final Pattern IPV4 = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private IpAllowlistUtil() {
    }

    /**
     * 校验并规范化为 JSON 数组字符串；空/空白 → null（表示不限）。
     */
    public static String normalizeAndValidate(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        List<String> list = parse(raw.trim());
        if (list.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String item : list) {
            String r = item.trim();
            if (StrUtil.isBlank(r)) {
                continue;
            }
            if (r.contains("/")) {
                if (!isValidCidr(r)) {
                    throw new FlowException("OPEN_PLATFORM_INVALID_IP", "非法 CIDR: " + r);
                }
                normalized.add(r);
            } else {
                if (!IPV4.matcher(r).matches()) {
                    throw new FlowException("OPEN_PLATFORM_INVALID_IP", "非法 IPv4: " + r);
                }
                normalized.add(r);
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new FlowException("OPEN_PLATFORM_INVALID_IP", "IP 白名单序列化失败");
        }
    }

    public static List<String> parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        String s = raw.trim();
        try {
            if (s.startsWith("[")) {
                List<?> arr = MAPPER.readValue(s, List.class);
                List<String> out = new ArrayList<>();
                for (Object o : arr) {
                    if (o != null && StrUtil.isNotBlank(String.valueOf(o))) {
                        out.add(String.valueOf(o).trim());
                    }
                }
                return out;
            }
        } catch (Exception ignored) {
            // fall through CSV
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : s.split("[,\\n]")) {
            String p = part.trim().replace("\"", "");
            if (StrUtil.isNotBlank(p)) {
                set.add(p);
            }
        }
        return new ArrayList<>(set);
    }

    public static boolean isValidCidr(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return false;
        }
        if (!IPV4.matcher(parts[0].trim()).matches()) {
            return false;
        }
        try {
            int prefix = Integer.parseInt(parts[1].trim());
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            // 用现有匹配器做自洽校验
            return OpenAuthService.ipv4InCidr(parts[0].trim(), cidr);
        } catch (Exception e) {
            return false;
        }
    }
}
