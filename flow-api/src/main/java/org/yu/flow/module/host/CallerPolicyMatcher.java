package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 {@link CallerPolicy} 与 {@link FlowHostPrincipal} 做集合匹配。
 */
public final class CallerPolicyMatcher {

    private CallerPolicyMatcher() {
    }

    /**
     * @return null 表示通过；非 null 为拒绝原因（短文案）
     */
    public static String denyReason(CallerPolicy policy, FlowHostPrincipal principal) {
        if (policy == null || !policy.isEnabled()) {
            return null;
        }
        if (principal == null) {
            return "缺少调用方身份";
        }
        if (!policy.hasAnyConstraint()) {
            return null;
        }

        List<Boolean> dimensionHits = new java.util.ArrayList<>(5);
        if (notEmpty(policy.getUserTypes())) {
            dimensionHits.add(matchUserType(policy.getUserTypes(), principal.getUserType()));
        }
        if (notEmpty(policy.getRoles())) {
            dimensionHits.add(matchAny(policy.getRoles(), principal.getRoles()));
        }
        if (notEmpty(policy.getPermissions())) {
            dimensionHits.add(matchPermissions(policy.getPermissions(), principal.getPermissions()));
        }
        if (notEmpty(policy.getDeptIds())) {
            dimensionHits.add(matchDept(policy.getDeptIds(), principal));
        }
        if (notEmpty(policy.getUserIds())) {
            dimensionHits.add(matchExact(policy.getUserIds(), principal.getUserId()));
        }
        if (dimensionHits.isEmpty()) {
            return null;
        }

        boolean any = CallerPolicy.MATCH_ANY.equalsIgnoreCase(StrUtil.trim(policy.getMatch()));
        boolean ok = any
                ? dimensionHits.stream().anyMatch(Boolean::booleanValue)
                : dimensionHits.stream().allMatch(Boolean::booleanValue);
        return ok ? null : "调用方不满足访问策略";
    }

    public static boolean matches(CallerPolicy policy, FlowHostPrincipal principal) {
        return denyReason(policy, principal) == null;
    }

    public static void assertMatchValid(CallerPolicy policy) {
        if (policy == null || !policy.isEnabled()) {
            return;
        }
        String match = StrUtil.trim(policy.getMatch());
        if (StrUtil.isNotBlank(match)
                && !CallerPolicy.MATCH_ALL.equalsIgnoreCase(match)
                && !CallerPolicy.MATCH_ANY.equalsIgnoreCase(match)) {
            throw new IllegalArgumentException("callerPolicy.match 仅支持 ALL 或 ANY");
        }
    }

    private static boolean matchUserType(List<String> required, String actual) {
        if (StrUtil.isBlank(actual)) {
            return false;
        }
        String a = actual.trim().toUpperCase(Locale.ROOT);
        for (String r : required) {
            if (StrUtil.isNotBlank(r) && a.equals(r.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchPermissions(List<String> required, Set<String> actual) {
        if (actual != null && actual.contains("*")) {
            return true;
        }
        return matchAny(required, actual);
    }

    private static boolean matchAny(List<String> required, Collection<String> actual) {
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        Set<String> have = normalizeSet(actual);
        for (String r : required) {
            if (StrUtil.isBlank(r)) {
                continue;
            }
            if (have.contains(r.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchDept(List<String> required, FlowHostPrincipal principal) {
        Set<String> have = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(principal.getDeptId())) {
            have.add(principal.getDeptId().trim().toLowerCase(Locale.ROOT));
        }
        if (principal.getDeptIds() != null) {
            for (String d : principal.getDeptIds()) {
                if (StrUtil.isNotBlank(d)) {
                    have.add(d.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return matchAny(required, have);
    }

    private static boolean matchExact(List<String> required, String actual) {
        if (StrUtil.isBlank(actual)) {
            return false;
        }
        String a = actual.trim().toLowerCase(Locale.ROOT);
        for (String r : required) {
            if (StrUtil.isNotBlank(r) && a.equals(r.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizeSet(Collection<String> actual) {
        return actual.stream()
                .filter(StrUtil::isNotBlank)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && list.stream().anyMatch(StrUtil::isNotBlank);
    }
}
