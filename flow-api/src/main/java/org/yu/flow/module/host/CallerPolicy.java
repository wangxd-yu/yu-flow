package org.yu.flow.module.host;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用方策略：按用户类型 / 角色 / 权限 / 部门 / 用户匹配 {@link FlowHostPrincipal}。
 *
 * <p>用于已发布 API 入站（{@code securityConfig.callerPolicy}，接口未启用时沿目录链继承）。
 * OSS 上传场景已改为 {@code caller_policy.rules}（{@link org.yu.flow.module.oss.support.OssAccessRule}），不再使用本组结构。
 * {@code enabled=false} 时不校验。</p>
 */
@Data
public class CallerPolicy {

    public static final String MATCH_ALL = "ALL";
    public static final String MATCH_ANY = "ANY";

    /** 是否启用调用方匹配；默认 false */
    private boolean enabled = false;

    /**
     * 多维度组合：{@code ALL}=所列非空维度均需命中；{@code ANY}=任一非空维度命中即可。
     */
    private String match = MATCH_ALL;

    private List<String> userTypes = new ArrayList<>();
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<String> deptIds = new ArrayList<>();
    /**
     * 选中部门是否包含下级。{@code null} 视为 true（组织树的默认预期）。
     * 目录无父子关系时展开无效果。
     */
    private Boolean deptIncludeChildren;
    private List<String> userIds = new ArrayList<>();

    /**
     * 多行允许规则。非空时优先于上方单块字段；空则把单块字段当成一条 MATCH（兼容旧 JSON）。
     */
    private List<CallerAccessRule> rules = new ArrayList<>();

    public boolean includeDeptChildren() {
        return deptIncludeChildren == null || Boolean.TRUE.equals(deptIncludeChildren);
    }

    public boolean hasAnyConstraint() {
        return notEmpty(userTypes) || notEmpty(roles) || notEmpty(permissions)
                || notEmpty(deptIds) || notEmpty(userIds);
    }

    /** 运行时求值用的允许行：新格式 {@code rules}，否则把旧单块升成一行。 */
    public List<CallerAccessRule> effectiveRules() {
        if (rules != null && !rules.isEmpty()) {
            return rules;
        }
        if (!hasAnyConstraint()) {
            return List.of();
        }
        CallerAccessRule rule = new CallerAccessRule();
        rule.setName("调用方");
        rule.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        rule.setMatch(match);
        rule.setUserTypes(userTypes);
        rule.setRoles(roles);
        rule.setPermissions(permissions);
        rule.setDeptIds(deptIds);
        rule.setDeptIncludeChildren(deptIncludeChildren);
        rule.setUserIds(userIds);
        rule.setEffect(CallerAccessRule.EFFECT_ALLOW);
        return List.of(rule);
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && list.stream().anyMatch(s -> s != null && !s.isBlank());
    }
}
