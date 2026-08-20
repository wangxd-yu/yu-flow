package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 配置式主体解析设置（系统配置 {@code HOST_PRINCIPAL_RESOLVER}）。
 *
 * <p>让宿主不写 Java SPI 也能提供「当前请求是谁」：{@code API} 模式执行保留接口
 * {@code /__sys/host-principal/resolve} 反查宿主会话，{@code HEADER} 模式直接读网关注入的请求头。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostPrincipalSettings {

    public static final String SETTINGS_KEY = "HOST_PRINCIPAL_RESOLVER";

    /** 执行保留接口反查宿主会话 */
    public static final String MODE_API = "API";
    /** 直接读取可信网关注入的请求头 */
    public static final String MODE_HEADER = "HEADER";

    public static final String CHANNEL_API = "HOST_RESOLVER_API";
    public static final String CHANNEL_HEADER = "HOST_GATEWAY_HEADER";

    /** 可映射的 Principal 字段，顺序即管理端展示顺序 */
    public static final List<String> FIELDS = List.of(
            "userId", "username", "userType", "deptId", "deptIds", "roles", "permissions");

    private static final Map<String, String> DEFAULT_HEADER_NAMES = Map.of(
            "userId", "X-User-Id",
            "username", "X-User-Name",
            "userType", "X-User-Type",
            "deptId", "X-Dept-Id",
            "deptIds", "X-Dept-Ids",
            "roles", "X-User-Roles",
            "permissions", "X-User-Perms");

    private static final int MAX_CACHE_SECONDS = 600;

    private boolean enabled;

    private String mode = MODE_API;

    /** 主体缓存秒数（按凭证指纹），0 = 每次请求都解析 */
    private int cacheSeconds = 30;

    /** API 模式：转发给解析接口的请求头白名单 */
    private List<String> forwardHeaders = new ArrayList<>(List.of("Authorization", "Cookie"));

    /** API 模式：解析接口返回字段名 → Principal 字段 */
    private Map<String, String> fields = new LinkedHashMap<>();

    /** HEADER 模式：请求头名 → Principal 字段 */
    private Map<String, String> headerNames = new LinkedHashMap<>();

    /** HEADER 模式必须显式确认：Flow 不直接暴露公网，网关会剥离客户端伪造的身份头 */
    private boolean trustProxyHeaders;

    /** 数据范围视为管理员（可见全部）的宿主用户类型码，其余用户仅可见本人数据 */
    private List<String> adminUserTypes = new ArrayList<>();

    /**
     * 平台默认隐私规则（未覆盖的目录/接口继承）。非空时优先于 {@link #privacyRevealRoles}。
     */
    private List<PrivacyAccessRule> privacyRules = new ArrayList<>();

    /** 平台默认解密/脱敏方案 ID；空或 builtin=系统内置 */
    private String privacyProfileId;

    /** 平台默认密文列后缀；空则用方案或系统默认 _encrypt */
    private String privacyFieldSuffix;

    private List<String> privacyExtraFields = new ArrayList<>();

    /** 平台默认输出是否去掉后缀；null=跟方案/系统默认 */
    private Boolean privacyStripSuffix;

    /** @deprecated 一期起由 {@link #privacyRules} 替代；读入时升成一条 MATCH + REVEAL */
    private List<String> privacyRevealRoles = new ArrayList<>();

    /** @deprecated 后端从未按此名单脱敏；未命中明文规则一律 MASK */
    private List<String> privacyMaskRoles = new ArrayList<>();

    /** 平台默认入站：目录/接口未启用 callerPolicy 时生效 */
    private boolean ingressCallerEnabled;

    private List<CallerAccessRule> ingressRules = new ArrayList<>();

    @JsonIgnore
    public boolean isHeaderMode() {
        return MODE_HEADER.equalsIgnoreCase(StrUtil.trim(mode));
    }

    @JsonIgnore
    public String resolvedMode() {
        return isHeaderMode() ? MODE_HEADER : MODE_API;
    }

    @JsonIgnore
    public int resolvedCacheSeconds() {
        if (cacheSeconds <= 0) {
            return 0;
        }
        return Math.min(cacheSeconds, MAX_CACHE_SECONDS);
    }

    /** 结果字段名，未配置时与 Principal 字段同名。 */
    public String resolvedField(String field) {
        String configured = fields != null ? StrUtil.trim(fields.get(field)) : null;
        return StrUtil.isNotBlank(configured) ? configured : field;
    }

    /** 请求头名，未配置时用约定的 {@code X-} 头。 */
    public String resolvedHeaderName(String field) {
        String configured = headerNames != null ? StrUtil.trim(headerNames.get(field)) : null;
        return StrUtil.isNotBlank(configured) ? configured : DEFAULT_HEADER_NAMES.get(field);
    }

    @JsonIgnore
    public List<String> resolvedForwardHeaders() {
        if (forwardHeaders == null || forwardHeaders.isEmpty()) {
            return List.of("Authorization", "Cookie");
        }
        List<String> out = new ArrayList<>(forwardHeaders.size());
        for (String h : forwardHeaders) {
            if (StrUtil.isNotBlank(h)) {
                out.add(h.trim());
            }
        }
        return out.isEmpty() ? List.of("Authorization", "Cookie") : out;
    }

    /** 该用户类型是否按管理员放开数据范围。 */
    public boolean isAdminUserType(String userType) {
        if (StrUtil.isBlank(userType) || adminUserTypes == null || adminUserTypes.isEmpty()) {
            return false;
        }
        String target = userType.trim().toLowerCase(Locale.ROOT);
        for (String t : adminUserTypes) {
            if (StrUtil.isNotBlank(t) && target.equals(t.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> defaultHeaderNames() {
        return DEFAULT_HEADER_NAMES;
    }

    /**
     * 平台默认隐私规则。旧配置只有 {@link #privacyRevealRoles} 时升成一条 MATCH + REVEAL。
     */
    @JsonIgnore
    public List<PrivacyAccessRule> resolvedPrivacyRules() {
        if (privacyRules != null && !privacyRules.isEmpty()) {
            return privacyRules;
        }
        if (privacyRevealRoles == null || privacyRevealRoles.stream().noneMatch(StrUtil::isNotBlank)) {
            return List.of();
        }
        PrivacyAccessRule rule = new PrivacyAccessRule();
        rule.setName("隐私明文角色");
        rule.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        rule.setMatch(CallerPolicy.MATCH_ALL);
        List<String> roles = new ArrayList<>();
        for (String role : privacyRevealRoles) {
            if (StrUtil.isNotBlank(role)) {
                roles.add(role.trim());
            }
        }
        rule.setRoles(roles);
        rule.setPrivacy(PrivacyAccessRule.PRIVACY_REVEAL);
        return List.of(rule);
    }

    /** 平台默认入站策略；未启用时返回 null。 */
    @JsonIgnore
    public CallerPolicy toIngressCallerPolicy() {
        if (!ingressCallerEnabled) {
            return null;
        }
        CallerPolicy policy = new CallerPolicy();
        policy.setEnabled(true);
        policy.setRules(ingressRules == null ? new ArrayList<>() : new ArrayList<>(ingressRules));
        return policy;
    }
}
