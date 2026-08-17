package org.yu.flow.module.api.privacy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条脱敏规则：字段别名命中后按 method 处理。
 * <p>method：{@code KEEP_HEAD_TAIL} / {@code KEEP_HEAD} / {@code KEEP_TAIL} /
 * {@code NAME_KEEP_ENDS} / {@code FULL} / {@code PHONE} / {@code ID_CARD}。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrivacyMaskRule {

    public static final String KEEP_HEAD_TAIL = "KEEP_HEAD_TAIL";
    public static final String KEEP_HEAD = "KEEP_HEAD";
    public static final String KEEP_TAIL = "KEEP_TAIL";
    public static final String NAME_KEEP_ENDS = "NAME_KEEP_ENDS";
    public static final String FULL = "FULL";
    public static final String PHONE = "PHONE";
    public static final String ID_CARD = "ID_CARD";

    public static final String MATCH_EXACT = "EXACT";
    /** 字段名包含别名（忽略大小写），如 phone → userPhone */
    public static final String MATCH_CONTAINS = "CONTAINS";
    /** 仅 JSON 高级配置；界面不再提供 */
    public static final String MATCH_REGEX = "REGEX";

    private String id;
    /** 去后缀后的字段名；精确 / 包含，见 {@link #matchMode} */
    private List<String> aliases = new ArrayList<>();
    /** {@link #MATCH_EXACT}（默认）、{@link #MATCH_CONTAINS}，或 JSON 中的 {@link #MATCH_REGEX} */
    private String matchMode;
    private String method;
    /** KEEP_HEAD / KEEP_HEAD_TAIL：保留开头字符数 */
    private Integer keepHead;
    /** KEEP_TAIL / KEEP_HEAD_TAIL：保留末尾字符数 */
    private Integer keepTail;
    /** 中间遮罩长度；空则按实际中间长度 */
    private Integer maskLen;
    /** 遮罩字符，默认 * */
    private String maskChar;
}
