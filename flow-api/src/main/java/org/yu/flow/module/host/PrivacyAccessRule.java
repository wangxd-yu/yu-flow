package org.yu.flow.module.host;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出站隐私「谁看什么」一行：身份匹配 + 明文/脱敏 + 可选按字段动作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrivacyAccessRule extends PrincipalMatch {

    public static final String PRIVACY_MASK = "MASK";
    public static final String PRIVACY_REVEAL = "REVEAL";

    public static final String FIELD_REVEAL = "REVEAL";
    public static final String FIELD_MASK = "MASK";
    public static final String FIELD_DROP = "DROP";

    /** {@link #PRIVACY_MASK} / {@link #PRIVACY_REVEAL}；未命中任何行时一律 MASK */
    private String privacy = PRIVACY_MASK;

    /**
     * 输出 JSON 键名 → {@link #FIELD_REVEAL} / {@link #FIELD_MASK} / {@link #FIELD_DROP}。
     * 按键名递归匹配任意嵌套层，不支持 {@code a.b.c} 路径。
     * 键可用逗号一次写多个（如 {@code createBy,updateBy}）。
     * {@link #FIELD_DROP} 对任意字段生效；明文/脱敏只作用于隐私列。
     * 空则本行所有隐私列走 {@link #privacy}。
     */
    private Map<String, String> fields = new LinkedHashMap<>();
}
