package org.yu.flow.module.oss.support;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.module.host.PrincipalMatch;

/**
 * 上传场景的一条访问规则：一类人 + 能否上传 + 下载可见范围。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OssAccessRule extends PrincipalMatch {

    public static final String PRINCIPALS_ANY = PrincipalMatch.PRINCIPALS_ANY;
    public static final String PRINCIPALS_OPEN = PrincipalMatch.PRINCIPALS_OPEN;
    public static final String PRINCIPALS_MATCH = PrincipalMatch.PRINCIPALS_MATCH;

    public static final String SCOPE_OFF = "OFF";
    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_DEPT = "DEPT";
    public static final String SCOPE_ALL = "ALL";

    private boolean upload;
    /** OFF / SELF / DEPT / ALL */
    private String downloadScope = SCOPE_OFF;
}
