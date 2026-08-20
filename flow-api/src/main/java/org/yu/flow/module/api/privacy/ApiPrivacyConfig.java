package org.yu.flow.module.api.privacy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口 / 目录出站隐私策略（{@code privacyConfig} JSON）。
 * <p>{@code null} 字段表示继承上级；{@code enabled=false} 显式关闭。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiPrivacyConfig {

    public static final String DEFAULT_SUFFIX = "_encrypt";
    public static final String FAIL_MASK = "MASK_PLACEHOLDER";
    public static final String ALG_SM4 = "SM4";
    public static final String KEY_DEFAULT = "default";

    /** null=继承；false=本层关闭拦截 */
    private Boolean enabled;

    /** 未填项是否继续向上继承，默认 true */
    private Boolean inherit;

    /**
     * 宿主机隐私方案 ID；{@code builtin} 为 YAML SM4 + 默认脱敏；
     * null/空表示继续向上继承。
     */
    private String profileId;

    private String fieldSuffix;

    private List<String> extraFields = new ArrayList<>();

    private Boolean stripSuffix;

    private AtRest atRest;

    /**
     * 脱敏类型 → 字段名/别名（去后缀后匹配，忽略大小写）。
     * 键：phone / name / idCard
     */
    private Map<String, List<String>> mask = new LinkedHashMap<>();

    /** 解密失败策略，默认 {@link #FAIL_MASK} */
    private String onDecryptFail;

    /**
     * 谁看明文/脱敏。{@code null}=继承上级整表；空列表=本级明确「无规则」（未命中一律 MASK）。
     * 不要与上级逐行 merge。
     */
    private List<org.yu.flow.module.host.PrivacyAccessRule> rules;

    /**
     * 字段脱敏规则（与隐私方案 {@code rules} 同一结构）。
     * {@code null}=继承方案/上级整表；非空=本级整表覆盖。
     */
    private List<PrivacyMaskRule> maskRules;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtRest {
        private String alg;
        private String keyId;
    }
}
