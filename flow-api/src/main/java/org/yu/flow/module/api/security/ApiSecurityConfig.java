package org.yu.flow.module.api.security;

import lombok.Data;
import org.yu.flow.module.host.CallerPolicy;

/**
 * 接口草稿 / 发布快照中的入站防护覆盖项。
 * <p>{@code null} 字段表示继承全局 {@code yu.flow.ingress} 默认值；
 * {@code authMode=INHERIT} 同理。</p>
 */
@Data
public class ApiSecurityConfig {

    /** INHERIT | NONE | HOST | OPEN */
    private String authMode = "INHERIT";

    /** null=继承全局；仅 OPEN 时生效 */
    private Boolean antiReplay;

    private Boolean rateLimitEnabled;

    private Integer rateLimitQps;

    /**
     * null=继承全局；空串=明确不限制；非空=本接口白名单（CSV / JSON 数组均可）。
     */
    private String ipAllowlist;

    /**
     * 接口执行超时（毫秒）。null=继承全局；≤0=不限制（不推荐生产）。
     */
    private Integer timeoutMs;

    /**
     * 调用方策略（用户类型 / 角色 / 权限等）。仅对 {@code HOST}（及 fail-closed 的 NONE→HOST）生效；
     * {@code OPEN} 以开放平台 grant 为准，运行时忽略本策略匹配。
     */
    private CallerPolicy callerPolicy;
}
