package org.yu.flow.module.host;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 入站「谁可以调」一行：身份匹配 + 允许。未命中默认拒绝。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallerAccessRule extends PrincipalMatch {

    public static final String EFFECT_ALLOW = "ALLOW";

    /** 目前仅 ALLOW；预留字段便于以后扩展 DENY */
    private String effect = EFFECT_ALLOW;
}
