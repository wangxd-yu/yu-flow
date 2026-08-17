package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把接口执行结果拆成目录选项（JSON 数组 / PageBean / R / Map.records）。
 */
final class HostCatalogResultMapper {

    private HostCatalogResultMapper() {
    }

    static List<HostIdentityCatalogItemDTO> map(Object raw, String valueField, String labelField,
                                                String keyword, int limit) {
        return map(raw, valueField, labelField, "parentId", keyword, limit);
    }

    static List<HostIdentityCatalogItemDTO> map(Object raw, String valueField, String labelField,
                                                String parentField, String keyword, int limit) {
        List<?> rows = unwrapList(raw);
        LinkedHashMap<String, HostIdentityCatalogItemDTO> seen = new LinkedHashMap<>();
        String vf = StrUtil.blankToDefault(valueField, "value").trim();
        String lf = StrUtil.blankToDefault(labelField, "label").trim();
        String pf = StrUtil.blankToDefault(parentField, "parentId").trim();
        String kw = StrUtil.trim(keyword);
        for (Object row : rows) {
            HostIdentityCatalogItemDTO item = toItem(row, vf, lf, pf);
            if (item == null || !keywordMatches(item, kw)) {
                continue;
            }
            seen.putIfAbsent(item.getValue().toLowerCase(Locale.ROOT), item);
            if (seen.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(seen.values());
    }

    static List<?> unwrapList(Object raw) {
        Object cur = raw;
        if (cur instanceof R<?> r) {
            cur = r.getData();
        }
        if (cur instanceof PageBean<?> page) {
            return page.getItems() != null ? page.getItems() : List.of();
        }
        if (cur instanceof Collection<?> col) {
            return new ArrayList<>(col);
        }
        if (cur instanceof Map<?, ?> map) {
            for (String key : List.of("items", "records", "list", "data", "rows", "content")) {
                Object nested = map.get(key);
                if (nested instanceof Collection<?> col) {
                    return new ArrayList<>(col);
                }
            }
        }
        if (cur != null) {
            return List.of(cur);
        }
        return List.of();
    }

    static HostIdentityCatalogItemDTO toItem(Object row, String valueField, String labelField) {
        return toItem(row, valueField, labelField, "parentId");
    }

    static HostIdentityCatalogItemDTO toItem(Object row, String valueField, String labelField, String parentField) {
        if (row == null) {
            return null;
        }
        if (row instanceof CharSequence cs) {
            String v = cs.toString().trim();
            if (v.isEmpty()) {
                return null;
            }
            return new HostIdentityCatalogItemDTO().setValue(v).setLabel(v).setSource("HOST");
        }
        if (row instanceof Map<?, ?> map) {
            String value = stringify(firstPresent(map, valueField, "value", "id", "code", "key"));
            if (StrUtil.isBlank(value)) {
                return null;
            }
            String label = stringify(firstPresent(map, labelField, "label", "name", "title", "text"));
            String parent = stringify(firstPresent(map, parentField, "parentId", "parent_id", "pid", "parent"));
            if (StrUtil.isNotBlank(parent) && parent.trim().equalsIgnoreCase(value.trim())) {
                parent = null;
            }
            return new HostIdentityCatalogItemDTO()
                    .setValue(value.trim())
                    .setLabel(StrUtil.blankToDefault(label, value).trim())
                    .setHint(stringify(firstPresent(map, "hint", "remark", "desc")))
                    .setParentId(StrUtil.trim(parent))
                    .setDisabled(asBool(map.get("disabled")))
                    .setSource("HOST");
        }
        return null;
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (StrUtil.isBlank(key)) {
                continue;
            }
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && key.equalsIgnoreCase(String.valueOf(e.getKey())) && e.getValue() != null) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static String stringify(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean asBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private static boolean keywordMatches(HostIdentityCatalogItemDTO item, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return true;
        }
        String k = keyword.toLowerCase(Locale.ROOT);
        return (item.getValue() != null && item.getValue().toLowerCase(Locale.ROOT).contains(k))
                || (item.getLabel() != null && item.getLabel().toLowerCase(Locale.ROOT).contains(k));
    }
}
