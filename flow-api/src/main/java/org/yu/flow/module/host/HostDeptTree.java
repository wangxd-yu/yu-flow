package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按 parentId 展开部门：选中上级时覆盖其全部下级。无树或出现环时，已选码仍保留。
 */
public final class HostDeptTree {

    private HostDeptTree() {
    }

    public static boolean hasTree(List<HostIdentityCatalogItemDTO> items) {
        if (items == null) {
            return false;
        }
        return items.stream().anyMatch(i -> i != null && StrUtil.isNotBlank(i.getParentId()));
    }

    public static Set<String> expand(List<HostIdentityCatalogItemDTO> items, Collection<String> selected) {
        Set<String> out = new LinkedHashSet<>();
        if (selected != null) {
            for (String s : selected) {
                if (StrUtil.isNotBlank(s)) {
                    out.add(s.trim());
                }
            }
        }
        if (out.isEmpty() || items == null || items.isEmpty()) {
            return out;
        }
        Map<String, String> canon = new LinkedHashMap<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (HostIdentityCatalogItemDTO item : items) {
            if (item == null || StrUtil.isBlank(item.getValue())) {
                continue;
            }
            String value = item.getValue().trim();
            String key = norm(value);
            canon.putIfAbsent(key, value);
            if (StrUtil.isBlank(item.getParentId())) {
                continue;
            }
            String parentKey = norm(item.getParentId());
            if (parentKey.equals(key)) {
                continue;
            }
            children.computeIfAbsent(parentKey, k -> new java.util.ArrayList<>()).add(value);
        }
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> q = new ArrayDeque<>();
        for (String sel : List.copyOf(out)) {
            q.add(norm(sel));
        }
        while (!q.isEmpty()) {
            String key = q.poll();
            if (!visited.add(key)) {
                continue;
            }
            String canonical = canon.get(key);
            if (canonical != null) {
                out.add(canonical);
            }
            List<String> kids = children.get(key);
            if (kids == null) {
                continue;
            }
            for (String kid : kids) {
                q.add(norm(kid));
            }
        }
        return out;
    }

    private static String norm(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
