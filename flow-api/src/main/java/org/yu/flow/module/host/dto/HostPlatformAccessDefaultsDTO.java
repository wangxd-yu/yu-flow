package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.host.CallerAccessRule;
import org.yu.flow.module.host.PrivacyAccessRule;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口目录树「平台默认」访问控制：入站防护写系统配置，调用方/隐私写宿主主体设置。
 */
@Data
@Accessors(chain = true)
public class HostPlatformAccessDefaultsDTO {

    /** NONE / HOST / OPEN */
    private String authMode;
    private Boolean rateLimitEnabled;
    private Integer rateLimitQps;
    /** 空串=不限制 */
    private String ipAllowlist;
    /** ≤0 不限制 */
    private Integer timeoutMs;

    private Boolean ingressCallerEnabled;
    private List<CallerAccessRule> ingressRules = new ArrayList<>();
    private List<PrivacyAccessRule> privacyRules = new ArrayList<>();

    /** 空或 builtin=系统内置 */
    private String privacyProfileId;
    private String privacyFieldSuffix;
    private List<String> privacyExtraFields = new ArrayList<>();
    private Boolean privacyStripSuffix;
}
