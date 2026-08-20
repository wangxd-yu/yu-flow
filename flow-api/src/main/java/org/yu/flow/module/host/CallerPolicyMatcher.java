package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;

import java.util.List;

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
        List<CallerAccessRule> rules = policy.effectiveRules();
        if (rules.isEmpty()) {
            return null;
        }
        for (CallerAccessRule rule : rules) {
            if (rule == null) {
                continue;
            }
            if (PrincipalMatchEngine.matches(rule, principal)) {
                return null;
            }
        }
        return "调用方不满足访问策略";
    }

    public static boolean matches(CallerPolicy policy, FlowHostPrincipal principal) {
        return denyReason(policy, principal) == null;
    }

    /**
     * 仅按 MATCH 维度匹配（假定策略已启用且已有约束）。
     */
    public static boolean matchesDimensions(CallerPolicy policy, FlowHostPrincipal principal) {
        return PrincipalMatchEngine.matchesDimensions(policy, principal);
    }

    public static void assertMatchValid(CallerPolicy policy) {
        if (policy == null || !policy.isEnabled()) {
            return;
        }
        if (policy.getRules() != null && !policy.getRules().isEmpty()) {
            if (policy.getRules().size() > PrincipalMatch.MAX_RULES) {
                throw new IllegalArgumentException("调用方策略最多 " + PrincipalMatch.MAX_RULES + " 条");
            }
            for (CallerAccessRule rule : policy.getRules()) {
                PrincipalMatchEngine.assertMatchValid(rule);
            }
            return;
        }
        String match = StrUtil.trim(policy.getMatch());
        if (StrUtil.isNotBlank(match)
                && !CallerPolicy.MATCH_ALL.equalsIgnoreCase(match)
                && !CallerPolicy.MATCH_ANY.equalsIgnoreCase(match)) {
            throw new IllegalArgumentException("callerPolicy.match 仅支持 ALL 或 ANY");
        }
    }
}
