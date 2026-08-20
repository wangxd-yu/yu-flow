package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 {@link PrincipalMatch} 与 {@link FlowHostPrincipal} 做身份匹配。
 * OSS / 入站 / 隐私共用；开放应用不会被「任何已登录」覆盖。
 */
public final class PrincipalMatchEngine {

    private PrincipalMatchEngine() {
    }

    public static boolean matches(PrincipalMatch rule, FlowHostPrincipal principal) {
        if (rule == null || principal == null) {
            return false;
        }
        boolean open = isOpenApp(principal);
        String mode = normalizePrincipals(rule.getPrincipals());
        if (PrincipalMatch.PRINCIPALS_ANY.equals(mode)) {
            return !open;
        }
        if (PrincipalMatch.PRINCIPALS_OPEN.equals(mode)) {
            return open && matchesOpenAppIds(rule, principal);
        }
        CallerPolicy policy = toMatchPolicy(rule);
        if (!policy.hasAnyConstraint()) {
            return false;
        }
        return matchesDimensions(policy, principal);
    }

    /**
     * 仅按 MATCH 维度匹配（假定策略已启用且已有约束）。
     */
    public static boolean matchesDimensions(CallerPolicy policy, FlowHostPrincipal principal) {
        if (policy == null || principal == null) {
            return false;
        }
        List<Boolean> dimensionHits = new ArrayList<>(5);
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
            return true;
        }
        boolean any = CallerPolicy.MATCH_ANY.equalsIgnoreCase(StrUtil.trim(policy.getMatch()));
        return any
                ? dimensionHits.stream().anyMatch(Boolean::booleanValue)
                : dimensionHits.stream().allMatch(Boolean::booleanValue);
    }

    public static String normalizePrincipals(String raw) {
        String value = StrUtil.trim(raw);
        if (PrincipalMatch.PRINCIPALS_ANY.equalsIgnoreCase(value)) {
            return PrincipalMatch.PRINCIPALS_ANY;
        }
        if (PrincipalMatch.PRINCIPALS_OPEN.equalsIgnoreCase(value)) {
            return PrincipalMatch.PRINCIPALS_OPEN;
        }
        return PrincipalMatch.PRINCIPALS_MATCH;
    }

    public static boolean isOpenApp(FlowHostPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (FlowHostPrincipal.TYPE_OPEN_APP.equalsIgnoreCase(StrUtil.trim(principal.getUserType()))) {
            return true;
        }
        return StrUtil.isNotBlank(principal.getUserId()) && principal.getUserId().startsWith("open:");
    }

    public static boolean isFlowConsole(FlowHostPrincipal principal) {
        return principal != null && "FLOW_JWT".equals(principal.getAuthChannel());
    }

    public static void assertMatchValid(PrincipalMatch rule) {
        if (rule == null) {
            return;
        }
        String match = StrUtil.trim(rule.getMatch());
        if (StrUtil.isNotBlank(match)
                && !CallerPolicy.MATCH_ALL.equalsIgnoreCase(match)
                && !CallerPolicy.MATCH_ANY.equalsIgnoreCase(match)) {
            throw new IllegalArgumentException("match 仅支持 ALL 或 ANY");
        }
        if (PrincipalMatch.PRINCIPALS_MATCH.equals(normalizePrincipals(rule.getPrincipals()))
                && !hasMatchConstraint(rule)) {
            throw new IllegalArgumentException("指定身份的规则请至少填写用户类型、角色、权限、部门或用户");
        }
    }

    public static List<String> cleanList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String item : raw) {
            String value = StrUtil.trim(item);
            if (StrUtil.isBlank(value)) {
                continue;
            }
            if (seen.add(value.toLowerCase(Locale.ROOT))) {
                out.add(value);
            }
        }
        return out;
    }

    public static boolean hasMatchConstraint(PrincipalMatch rule) {
        if (rule == null) {
            return false;
        }
        return !cleanList(rule.getUserTypes()).isEmpty()
                || !cleanList(rule.getRoles()).isEmpty()
                || !cleanList(rule.getPermissions()).isEmpty()
                || !cleanList(rule.getUserIds()).isEmpty()
                || !cleanList(rule.getDeptIds()).isEmpty();
    }

    public static CallerPolicy toMatchPolicy(PrincipalMatch rule) {
        CallerPolicy policy = new CallerPolicy();
        policy.setEnabled(true);
        policy.setMatch(rule.getMatch());
        policy.setUserTypes(cleanList(rule.getUserTypes()));
        policy.setRoles(cleanList(rule.getRoles()));
        policy.setPermissions(cleanList(rule.getPermissions()));
        policy.setUserIds(cleanList(rule.getUserIds()));
        policy.setDeptIds(cleanList(rule.getDeptIds()));
        policy.setDeptIncludeChildren(rule.getDeptIncludeChildren());
        return policy;
    }

    static boolean matchesOpenAppIds(PrincipalMatch rule, FlowHostPrincipal principal) {
        List<String> ids = cleanList(rule.getUserIds());
        if (ids.isEmpty()) {
            return true;
        }
        String userId = StrUtil.trim(principal.getUserId());
        String username = StrUtil.trim(principal.getUsername());
        for (String id : ids) {
            if (StrUtil.equalsIgnoreCase(id, userId) || StrUtil.equalsIgnoreCase(id, username)) {
                return true;
            }
            String prefixed = id.toLowerCase(Locale.ROOT).startsWith("open:") ? id : "open:" + id;
            if (StrUtil.equalsIgnoreCase(prefixed, userId)) {
                return true;
            }
        }
        return false;
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
