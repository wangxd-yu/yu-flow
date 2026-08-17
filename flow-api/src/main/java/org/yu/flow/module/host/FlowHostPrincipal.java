package org.yu.flow.module.host;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 当前请求主体（宿主 SPI 或内置 JWT 解析结果）。
 *
 * <p>约定常用 {@link #userType}：{@code ADMIN}（运营/管理端）、{@code END_USER}（业务用户）、
 * {@code OPEN_APP}（开放平台应用）。宿主可扩展自定义码。</p>
 */
@Value
@Builder
public class FlowHostPrincipal {

    /** 运营/管理端用户（内置 JWT 默认） */
    public static final String TYPE_ADMIN = "ADMIN";
    /** 业务/C 端用户（宿主实现） */
    public static final String TYPE_END_USER = "END_USER";
    /** 开放平台 AppKey 身份 */
    public static final String TYPE_OPEN_APP = "OPEN_APP";

    String userId;
    String username;
    /** 用户类型码，见 {@link #TYPE_ADMIN} 等 */
    String userType;
    String deptId;
    @Builder.Default
    Set<String> deptIds = Collections.emptySet();
    @Builder.Default
    Set<String> roles = Collections.emptySet();
    /** 宿主或 Flow RBAC 权限码（非菜单码亦可） */
    @Builder.Default
    Set<String> permissions = Collections.emptySet();
    /** 凭证通道：FLOW_JWT / HOST_SESSION / OPEN_APP 等 */
    String authChannel;
    @Builder.Default
    Map<String, String> attributes = Collections.emptyMap();
}
