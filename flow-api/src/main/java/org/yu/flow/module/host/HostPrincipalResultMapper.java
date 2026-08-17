package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把主体解析接口的返回值映射为 {@link FlowHostPrincipal}。
 */
final class HostPrincipalResultMapper {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    private HostPrincipalResultMapper() {
    }

    /** 取首行并规范成 Map；解析接口返回单对象、数组、R、PageBean 均可。 */
    static Map<String, Object> firstRow(Object raw) {
        List<?> rows = HostCatalogResultMapper.unwrapList(raw);
        for (Object row : rows) {
            Map<String, Object> map = toMap(row);
            if (map != null && !map.isEmpty()) {
                return map;
            }
        }
        return null;
    }

    static FlowHostPrincipal toPrincipal(Map<String, Object> row, HostPrincipalSettings settings, String channel) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String userId = text(row, settings.resolvedField("userId"));
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        String username = text(row, settings.resolvedField("username"));
        return FlowHostPrincipal.builder()
                .userId(userId)
                .username(StrUtil.blankToDefault(username, userId))
                .userType(text(row, settings.resolvedField("userType")))
                .deptId(text(row, settings.resolvedField("deptId")))
                .deptIds(set(row.get(settings.resolvedField("deptIds"))))
                .roles(set(row.get(settings.resolvedField("roles"))))
                .permissions(set(row.get(settings.resolvedField("permissions"))))
                .authChannel(channel)
                .attributes(Map.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object row) {
        if (row == null) {
            return null;
        }
        if (row instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
            return out;
        }
        if (row instanceof CharSequence) {
            return null;
        }
        try {
            return MAPPER.convertValue(row, Map.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 字段名大小写不敏感，兼容宿主返回下划线风格。 */
    private static String text(Map<String, Object> row, String field) {
        Object v = row.get(field);
        if (v == null) {
            v = caseInsensitive(row, field);
        }
        if (v == null) {
            v = caseInsensitive(row, snake(field));
        }
        return v == null ? null : StrUtil.trimToNull(String.valueOf(v));
    }

    private static Object caseInsensitive(Map<String, Object> row, String field) {
        if (StrUtil.isBlank(field)) {
            return null;
        }
        String target = field.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && target.equals(e.getKey().toLowerCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String snake(String camel) {
        if (StrUtil.isBlank(camel)) {
            return camel;
        }
        StringBuilder sb = new StringBuilder(camel.length() + 4);
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 集合字段兼容数组与逗号分隔字符串。 */
    private static Set<String> set(Object value) {
        if (value == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        if (value instanceof Collection<?> col) {
            for (Object item : col) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    out.add(String.valueOf(item).trim());
                }
            }
            return out;
        }
        for (String part : StrUtil.split(String.valueOf(value), ',')) {
            if (StrUtil.isNotBlank(part)) {
                out.add(part.trim());
            }
        }
        return out;
    }
}
