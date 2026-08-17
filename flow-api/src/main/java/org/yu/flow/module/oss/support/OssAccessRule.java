package org.yu.flow.module.oss.support;

import lombok.Data;
import org.yu.flow.module.host.CallerPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传场景的一条访问规则：一类人 + 能否上传 + 下载可见范围。
 */
@Data
public class OssAccessRule {

    public static final String PRINCIPALS_ANY = "ANY_AUTHENTICATED";
    public static final String PRINCIPALS_OPEN = "OPEN_APP";
    public static final String PRINCIPALS_MATCH = "MATCH";

    public static final String SCOPE_OFF = "OFF";
    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_DEPT = "DEPT";
    public static final String SCOPE_ALL = "ALL";

    private String name;
    /** {@link #PRINCIPALS_ANY} / {@link #PRINCIPALS_OPEN} / {@link #PRINCIPALS_MATCH} */
    private String principals = PRINCIPALS_MATCH;
    /** 仅 MATCH 且填写了多个维度时有意义 */
    private String match = CallerPolicy.MATCH_ALL;
    private List<String> userTypes = new ArrayList<>();
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<String> userIds = new ArrayList<>();
    private boolean upload;
    /** OFF / SELF / DEPT / ALL */
    private String downloadScope = SCOPE_OFF;
}
