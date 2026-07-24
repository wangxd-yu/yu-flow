package org.yu.flow.module.open.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenAuthContext {
    private String platformId;
    private String platformName;
    private String appKey;
    private String credentialId;
    /** 平台级入站日志：null/1=开，0=关 */
    private Integer openCallLogEnabled;
    private Integer rateLimitQps;
}
