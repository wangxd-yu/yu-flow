package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.CallerPolicy;
import org.yu.flow.module.host.CallerPolicyMatcher;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 访问规则的解析、校验、匹配与可见范围合并。不依赖 Spring。
 */
public final class OssAccessSupport {

    public static final String CODE_DENIED = "OSS_CALLER_DENIED";
    public static final String CHANNEL_FLOW_JWT = "FLOW_JWT";

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    private OssAccessSupport() {
    }

    public static boolean isOpenPrincipal(FlowHostPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (FlowHostPrincipal.TYPE_OPEN_APP.equalsIgnoreCase(StrUtil.trim(principal.getUserType()))) {
            return true;
        }
        return StrUtil.isNotBlank(principal.getUserId()) && principal.getUserId().startsWith("open:");
    }

    public static boolean isFlowConsole(FlowHostPrincipal principal) {
        return principal != null && CHANNEL_FLOW_JWT.equals(principal.getAuthChannel());
    }

    public static OssAccessRules parse(OssUploadProfileDO profile) {
        return parseJson(profile != null ? profile.getCallerPolicy() : null);
    }

    public static OssAccessRules parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return new OssAccessRules();
        }
        try {
            JsonNode root = MAPPER.readTree(json.trim());
            if (root == null || root.isNull()) {
                return new OssAccessRules();
            }
            if (root.has("rules")) {
                OssAccessRules parsed = MAPPER.treeToValue(root, OssAccessRules.class);
                return parsed != null ? parsed : new OssAccessRules();
            }
            if (root.has("upload") || root.has("download")) {
                return migrateLegacy(root);
            }
            OssAccessRules parsed = MAPPER.treeToValue(root, OssAccessRules.class);
            return parsed != null ? parsed : new OssAccessRules();
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID", "访问规则 JSON 非法");
        }
    }

    public static String toJson(OssAccessRules rules) {
        try {
            OssAccessRules safe = rules != null ? rules : new OssAccessRules();
            if (safe.getRules() == null) {
                safe.setRules(new ArrayList<>());
            }
            return MAPPER.writeValueAsString(safe);
        } catch (Exception e) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID", "访问规则序列化失败");
        }
    }

    public static void validateOnSave(OssUploadProfileDO profile) {
        OssAccessRules rules = parse(profile);
        List<OssAccessRule> list = normalize(rules);
        rules.setRules(list);
        profile.setCallerPolicy(toJson(rules));

        boolean requireAuth = profile.getRequireAuth() == null || profile.getRequireAuth();
        if (!requireAuth && StrUtil.isNotBlank(profile.getUploadPerm())) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID",
                    "匿名上传（不要求登录）时不能配置 uploadPerm");
        }
        boolean pub = isPublic(profile);
        if (pub) {
            for (OssAccessRule rule : list) {
                rule.setDownloadScope(OssAccessRule.SCOPE_OFF);
            }
            profile.setCallerPolicy(toJson(rules));
        }
        if (!pub && requireAuth && list.isEmpty()) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID",
                    "私有且要求登录时请至少配置一条访问规则");
        }
        if (!pub && !list.isEmpty() && list.stream().noneMatch(OssAccessSupport::hasDownload)) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID",
                    "私有场景请至少给一行配置下载范围，否则宿主用户无法查看文件");
        }
        for (OssAccessRule rule : list) {
            validateRule(rule);
        }
        if (list.size() > OssAccessRules.MAX_RULES) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID",
                    "访问规则最多 " + OssAccessRules.MAX_RULES + " 条");
        }
    }

    public static boolean canUpload(OssAccessRules rules, FlowHostPrincipal principal) {
        if (isFlowConsole(principal)) {
            return true;
        }
        if (principal == null) {
            return false;
        }
        for (OssAccessRule rule : safeRules(rules)) {
            if (rule.isUpload() && matches(rule, principal)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canDownload(OssAccessRules rules, FlowHostPrincipal principal) {
        if (isFlowConsole(principal)) {
            return true;
        }
        return !mergeDownload(rules, principal).none();
    }

    public static OssDownloadGrant mergeDownload(OssAccessRules rules, FlowHostPrincipal principal) {
        OssDownloadGrant grant = new OssDownloadGrant();
        if (principal == null) {
            return grant;
        }
        for (OssAccessRule rule : safeRules(rules)) {
            if (!matches(rule, principal)) {
                continue;
            }
            String scope = normalizeScope(rule.getDownloadScope());
            if (OssAccessRule.SCOPE_ALL.equals(scope)) {
                grant.all = true;
            } else if (OssAccessRule.SCOPE_DEPT.equals(scope)) {
                grant.dept = true;
            } else if (OssAccessRule.SCOPE_SELF.equals(scope)) {
                grant.self = true;
            }
        }
        return grant;
    }

    public static boolean matches(OssAccessRule rule, FlowHostPrincipal principal) {
        if (rule == null || principal == null) {
            return false;
        }
        boolean open = isOpenPrincipal(principal);
        String mode = normalizePrincipals(rule.getPrincipals());
        if (OssAccessRule.PRINCIPALS_ANY.equals(mode)) {
            return !open && !isFlowConsole(principal);
        }
        if (OssAccessRule.PRINCIPALS_OPEN.equals(mode)) {
            return open && matchesOpenAppIds(rule, principal);
        }
        CallerPolicy policy = toMatchPolicy(rule);
        if (!policy.hasAnyConstraint()) {
            return false;
        }
        return CallerPolicyMatcher.matches(policy, principal);
    }

    public static boolean allowsObject(OssObjectDO object, OssDownloadGrant grant,
                                       FlowHostPrincipal principal, Set<String> expandedDepts) {
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            return false;
        }
        if (OssObjectDO.VISIBILITY_PUBLIC.equals(object.getVisibility())) {
            return true;
        }
        if (grant == null || grant.none()) {
            return false;
        }
        if (grant.all) {
            return true;
        }
        if (grant.self && OssUploaderIdentity.isSelf(object, principal)) {
            return true;
        }
        if (grant.dept && StrUtil.isNotBlank(object.getDeptId())
                && containsIgnoreCase(expandedDepts, object.getDeptId())) {
            return true;
        }
        return false;
    }

    public static void assertHostUpload(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        boolean requireAuth = profile.getRequireAuth() == null || profile.getRequireAuth();
        if (!requireAuth) {
            if (isOpenPrincipal(principal) && !canUpload(parse(profile), principal)) {
                throw new FlowException(CODE_DENIED,
                        "开放应用上传必须在访问规则中显式授权 OPEN_APP 或指定应用");
            }
            return;
        }
        if (isFlowConsole(principal)) {
            return;
        }
        if (principal == null) {
            throw new FlowException("RBAC_UNAUTHORIZED", "未登录或凭证无效");
        }
        if (!canUpload(parse(profile), principal)) {
            if (isOpenPrincipal(principal)) {
                throw new FlowException(CODE_DENIED,
                        "开放应用上传必须在访问规则中显式授权 OPEN_APP 或指定应用");
            }
            throw new FlowException(CODE_DENIED, "调用方不满足访问规则");
        }
    }

    public static void assertHostDownload(OssUploadProfileDO profile, FlowHostPrincipal principal) {
        if (profile == null) {
            throw new FlowException(CODE_DENIED, "私有文件所属上传场景不存在");
        }
        if (OssUploadProfileDO.VISIBILITY_PUBLIC.equalsIgnoreCase(StrUtil.trim(profile.getVisibility()))) {
            return;
        }
        if (isFlowConsole(principal)) {
            return;
        }
        if (principal == null) {
            throw new FlowException("RBAC_UNAUTHORIZED", "未登录或凭证无效");
        }
        if (!canDownload(parse(profile), principal)) {
            throw new FlowException(CODE_DENIED, "调用方不满足访问规则");
        }
    }

    static List<OssAccessRule> normalize(OssAccessRules rules) {
        List<OssAccessRule> out = new ArrayList<>();
        for (OssAccessRule rule : safeRules(rules)) {
            if (rule == null) {
                continue;
            }
            rule.setPrincipals(normalizePrincipals(rule.getPrincipals()));
            rule.setMatch(CallerPolicy.MATCH_ANY.equalsIgnoreCase(StrUtil.trim(rule.getMatch()))
                    ? CallerPolicy.MATCH_ANY : CallerPolicy.MATCH_ALL);
            rule.setUserTypes(cleanList(rule.getUserTypes()));
            rule.setRoles(cleanList(rule.getRoles()));
            rule.setPermissions(cleanList(rule.getPermissions()));
            rule.setUserIds(cleanList(rule.getUserIds()));
            rule.setDownloadScope(normalizeScope(rule.getDownloadScope()));
            if (!rule.isUpload() && OssAccessRule.SCOPE_OFF.equals(rule.getDownloadScope())) {
                continue;
            }
            out.add(rule);
        }
        return out;
    }

    private static void validateRule(OssAccessRule rule) {
        if (OssAccessRule.PRINCIPALS_MATCH.equals(rule.getPrincipals())
                && !hasMatchConstraint(rule)) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID",
                    "指定身份的规则请至少填写用户类型、角色、权限或用户");
        }
        if (OssAccessRule.PRINCIPALS_OPEN.equals(rule.getPrincipals())) {
            return;
        }
        try {
            CallerPolicyMatcher.assertMatchValid(toMatchPolicy(rule));
        } catch (IllegalArgumentException e) {
            throw new FlowException("OSS_ACCESS_RULES_INVALID", e.getMessage());
        }
    }

    private static OssAccessRules migrateLegacy(JsonNode root) {
        OssAccessRules out = new OssAccessRules();
        List<OssAccessRule> rules = new ArrayList<>();
        CallerPolicy upload = readLegacyPolicy(root.get("upload"));
        CallerPolicy download = readLegacyPolicy(root.get("download"));
        if (upload != null && upload.isEnabled()) {
            OssAccessRule rule = fromLegacy("上传", upload);
            rule.setUpload(true);
            rule.setDownloadScope(OssAccessRule.SCOPE_OFF);
            rules.add(rule);
        }
        if (download != null && download.isEnabled()) {
            OssAccessRule rule = fromLegacy("下载", download);
            rule.setUpload(false);
            rule.setDownloadScope(OssAccessRule.SCOPE_SELF);
            rules.add(rule);
        }
        out.setRules(rules);
        return out;
    }

    private static CallerPolicy readLegacyPolicy(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return MAPPER.treeToValue(node, CallerPolicy.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static OssAccessRule fromLegacy(String name, CallerPolicy policy) {
        OssAccessRule rule = new OssAccessRule();
        rule.setName(name);
        rule.setPrincipals(OssAccessRule.PRINCIPALS_MATCH);
        rule.setMatch(policy.getMatch());
        rule.setUserTypes(cleanList(policy.getUserTypes()));
        rule.setRoles(cleanList(policy.getRoles()));
        rule.setPermissions(cleanList(policy.getPermissions()));
        rule.setUserIds(cleanList(policy.getUserIds()));
        return rule;
    }

    private static CallerPolicy toMatchPolicy(OssAccessRule rule) {
        CallerPolicy policy = new CallerPolicy();
        policy.setEnabled(true);
        policy.setMatch(rule.getMatch());
        policy.setUserTypes(cleanList(rule.getUserTypes()));
        policy.setRoles(cleanList(rule.getRoles()));
        policy.setPermissions(cleanList(rule.getPermissions()));
        policy.setUserIds(cleanList(rule.getUserIds()));
        return policy;
    }

    private static boolean hasDownload(OssAccessRule rule) {
        String scope = normalizeScope(rule.getDownloadScope());
        return OssAccessRule.SCOPE_SELF.equals(scope)
                || OssAccessRule.SCOPE_DEPT.equals(scope)
                || OssAccessRule.SCOPE_ALL.equals(scope);
    }

    private static boolean hasMatchConstraint(OssAccessRule rule) {
        return !cleanList(rule.getUserTypes()).isEmpty()
                || !cleanList(rule.getRoles()).isEmpty()
                || !cleanList(rule.getPermissions()).isEmpty()
                || !cleanList(rule.getUserIds()).isEmpty();
    }

    private static String normalizePrincipals(String raw) {
        String value = StrUtil.trim(raw);
        if (OssAccessRule.PRINCIPALS_ANY.equalsIgnoreCase(value)) {
            return OssAccessRule.PRINCIPALS_ANY;
        }
        if (OssAccessRule.PRINCIPALS_OPEN.equalsIgnoreCase(value)) {
            return OssAccessRule.PRINCIPALS_OPEN;
        }
        return OssAccessRule.PRINCIPALS_MATCH;
    }

    private static boolean matchesOpenAppIds(OssAccessRule rule, FlowHostPrincipal principal) {
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

    static String normalizeScope(String raw) {
        String scope = StrUtil.trim(raw).toUpperCase(Locale.ROOT);
        if (OssAccessRule.SCOPE_SELF.equals(scope)
                || OssAccessRule.SCOPE_DEPT.equals(scope)
                || OssAccessRule.SCOPE_ALL.equals(scope)) {
            return scope;
        }
        return OssAccessRule.SCOPE_OFF;
    }

    private static boolean isPublic(OssUploadProfileDO profile) {
        return profile != null && OssUploadProfileDO.VISIBILITY_PUBLIC.equalsIgnoreCase(
                StrUtil.trim(profile.getVisibility()));
    }

    private static List<OssAccessRule> safeRules(OssAccessRules rules) {
        if (rules == null || rules.getRules() == null) {
            return List.of();
        }
        return rules.getRules();
    }

    private static List<String> cleanList(List<String> raw) {
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

    private static boolean containsIgnoreCase(Set<String> values, String target) {
        if (values == null || values.isEmpty() || StrUtil.isBlank(target)) {
            return false;
        }
        String key = target.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && key.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 合并后的下载可见范围。 */
    public static final class OssDownloadGrant {
        boolean all;
        boolean self;
        boolean dept;

        public boolean none() {
            return !all && !self && !dept;
        }
    }
}
