package org.yu.flow.module.host;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 共用身份匹配段：与 OSS 访问规则同一套「人怎么勾」。
 * <p>结果（能不能调 / 明文还是脱敏 / 下载范围）由各场景自己的字段表达，不要写进这里。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrincipalMatch {

    public static final String PRINCIPALS_ANY = "ANY_AUTHENTICATED";
    public static final String PRINCIPALS_OPEN = "OPEN_APP";
    public static final String PRINCIPALS_MATCH = "MATCH";

    public static final int MAX_RULES = 8;

    private String name;

    /** {@link #PRINCIPALS_ANY} / {@link #PRINCIPALS_MATCH} / {@link #PRINCIPALS_OPEN} */
    private String principals = PRINCIPALS_MATCH;

    /** 仅 MATCH 且填写了多个维度时有意义 */
    private String match = CallerPolicy.MATCH_ALL;

    private List<String> userTypes = new ArrayList<>();
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<String> userIds = new ArrayList<>();
    /** 宿主机配置启用「部门」后，OSS / 接口访问规则也会展示 */
    private List<String> deptIds = new ArrayList<>();
    private Boolean deptIncludeChildren;
}
