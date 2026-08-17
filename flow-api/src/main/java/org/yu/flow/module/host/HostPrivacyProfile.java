package org.yu.flow.module.host;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.yu.flow.module.api.privacy.PrivacyDecryptAlg;
import org.yu.flow.module.api.privacy.PrivacyDecryptSpec;
import org.yu.flow.module.api.privacy.PrivacyMaskRule;
import org.yu.flow.module.api.privacy.PrivacyMasker;

import java.util.ArrayList;
import java.util.List;

/**
 * 一套库内解密 + 展示脱敏方案，由目录/接口 {@code privacyConfig.profileId} 引用。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostPrivacyProfile {

    private String id;
    private String name;
    /** 算法族 {@link PrivacyDecryptAlg}：SM4 / AES / PLAIN；旧值 SM4_CBC、AES_CBC 仍可解析 */
    private String decryptAlg;
    /** CBC / ECB / GCM（GCM 仅 AES） */
    private String decryptMode;
    /** HEX / BASE64 */
    private String decryptEncoding;
    /** PREPEND / NONE / FIXED */
    private String decryptIvMode;
    /** {@code FIXED} 时的 IV（16 字节或 hex；GCM 可为 12 字节） */
    private String decryptIvFixed;
    /** 16 字节明文或 32/48/64 位 hex；空则 SM4 回退 YAML {@code yu.flow.privacy.at-rest-sm4-key} */
    private String decryptKey;
    private String fieldSuffix;
    private List<String> extraFields = new ArrayList<>();
    private Boolean stripSuffix;
    private List<PrivacyMaskRule> rules = new ArrayList<>();

    public static HostPrivacyProfile builtin() {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setId(PrivacyDecryptAlg.BUILTIN_PROFILE_ID);
        p.setName("系统内置");
        p.setDecryptAlg(PrivacyDecryptAlg.SM4);
        p.setDecryptMode(PrivacyDecryptSpec.MODE_CBC);
        p.setDecryptEncoding(PrivacyDecryptSpec.ENC_HEX);
        p.setDecryptIvMode(PrivacyDecryptSpec.IV_PREPEND);
        p.setFieldSuffix("_encrypt");
        p.setStripSuffix(true);
        p.setRules(PrivacyMasker.defaultRules());
        return p;
    }

    public static HostPrivacyProfile missing(String id) {
        HostPrivacyProfile p = new HostPrivacyProfile();
        p.setId(id);
        p.setName("缺失方案");
        p.setDecryptAlg(PrivacyDecryptAlg.MISSING);
        p.setRules(List.of());
        return p;
    }
}
