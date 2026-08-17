package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.yu.flow.module.host.HostPrivacyProfile;

import java.util.Locale;

/**
 * 库内对称解密参数。旧配置仅有 {@code SM4_CBC}/{@code AES_CBC} 时按 CBC + HEX + IV 前置补齐。
 */
@Data
public class PrivacyDecryptSpec {

    public static final String FAMILY_SM4 = "SM4";
    public static final String FAMILY_AES = "AES";
    public static final String FAMILY_PLAIN = "PLAIN";
    public static final String FAMILY_MISSING = "MISSING";

    public static final String MODE_CBC = "CBC";
    public static final String MODE_ECB = "ECB";
    public static final String MODE_GCM = "GCM";

    public static final String ENC_HEX = "HEX";
    public static final String ENC_BASE64 = "BASE64";

    public static final String IV_PREPEND = "PREPEND";
    public static final String IV_NONE = "NONE";
    public static final String IV_FIXED = "FIXED";

    public static final int CBC_IV_LEN = 16;
    public static final int GCM_IV_LEN = 12;

    private String family;
    private String mode;
    private String encoding;
    private String ivMode;
    private String ivFixed;

    public static PrivacyDecryptSpec sm4CbcDefault() {
        return of(FAMILY_SM4, MODE_CBC, ENC_HEX, IV_PREPEND, null);
    }

    public static PrivacyDecryptSpec plain() {
        return of(FAMILY_PLAIN, null, null, null, null);
    }

    public static PrivacyDecryptSpec missing() {
        return of(FAMILY_MISSING, null, null, null, null);
    }

    public static PrivacyDecryptSpec of(String family, String mode, String encoding, String ivMode, String ivFixed) {
        PrivacyDecryptSpec spec = new PrivacyDecryptSpec();
        spec.family = family;
        spec.mode = mode;
        spec.encoding = encoding;
        spec.ivMode = ivMode;
        spec.ivFixed = ivFixed;
        return spec;
    }

    public static PrivacyDecryptSpec fromAlg(String raw) {
        if (StrUtil.isBlank(raw)) {
            return sm4CbcDefault();
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (t) {
            case FAMILY_PLAIN, "NONE", "IDENTITY" -> plain();
            case FAMILY_MISSING -> missing();
            case "SM4_ECB", "SM4ECB" -> of(FAMILY_SM4, MODE_ECB, ENC_HEX, IV_NONE, null);
            case "AES_ECB", "AESECB" -> of(FAMILY_AES, MODE_ECB, ENC_HEX, IV_NONE, null);
            case "AES_GCM", "AESGCM" -> of(FAMILY_AES, MODE_GCM, ENC_HEX, IV_PREPEND, null);
            case FAMILY_AES, PrivacyDecryptAlg.AES_CBC, "AESCBC" ->
                    of(FAMILY_AES, MODE_CBC, ENC_HEX, IV_PREPEND, null);
            case FAMILY_SM4, PrivacyDecryptAlg.SM4_CBC, "SM4CBC" -> sm4CbcDefault();
            default -> of(t, MODE_CBC, ENC_HEX, IV_PREPEND, null);
        };
    }

    public static PrivacyDecryptSpec fromProfile(HostPrivacyProfile profile) {
        if (profile == null) {
            return sm4CbcDefault();
        }
        PrivacyDecryptSpec spec = fromAlg(profile.getDecryptAlg());
        if (StrUtil.isNotBlank(profile.getDecryptMode())) {
            spec.setMode(profile.getDecryptMode());
        }
        if (StrUtil.isNotBlank(profile.getDecryptEncoding())) {
            spec.setEncoding(profile.getDecryptEncoding());
        }
        if (StrUtil.isNotBlank(profile.getDecryptIvMode())) {
            spec.setIvMode(profile.getDecryptIvMode());
        }
        if (profile.getDecryptIvFixed() != null) {
            spec.setIvFixed(profile.getDecryptIvFixed());
        }
        return spec.normalize();
    }

    public PrivacyDecryptSpec copy() {
        return of(family, mode, encoding, ivMode, ivFixed);
    }

    public PrivacyDecryptSpec normalize() {
        PrivacyDecryptSpec spec = copy();
        if (spec.isPlain() || spec.isMissing()) {
            spec.mode = null;
            spec.encoding = null;
            spec.ivMode = null;
            spec.ivFixed = null;
            return spec;
        }
        spec.family = StrUtil.blankToDefault(spec.family, FAMILY_SM4).trim().toUpperCase(Locale.ROOT);
        spec.mode = normalizeMode(spec.mode);
        spec.encoding = normalizeEncoding(spec.encoding);
        spec.ivMode = normalizeIvMode(spec.ivMode);
        if (MODE_ECB.equals(spec.mode)) {
            spec.ivMode = IV_NONE;
            spec.ivFixed = null;
        } else if (StrUtil.isBlank(spec.ivMode)) {
            spec.ivMode = IV_PREPEND;
        }
        spec.ivFixed = IV_FIXED.equals(spec.ivMode) ? StrUtil.trimToNull(spec.ivFixed) : null;
        return spec;
    }

    /**
     * @return 人类可读错误；通过则为 {@code null}
     */
    public String validateMessage() {
        PrivacyDecryptSpec spec = normalize();
        if (spec.isPlain() || spec.isMissing()) {
            return null;
        }
        if (!FAMILY_SM4.equals(spec.family) && !FAMILY_AES.equals(spec.family)) {
            return "不支持的解密算法: " + spec.family;
        }
        if (MODE_GCM.equals(spec.mode) && !FAMILY_AES.equals(spec.family)) {
            return "SM4 不支持 GCM，请改用 AES 或 CBC/ECB";
        }
        if (MODE_GCM.equals(spec.mode) && IV_NONE.equals(spec.ivMode)) {
            return "GCM 必须提供 IV（前置或固定）";
        }
        if (IV_FIXED.equals(spec.ivMode)) {
            if (StrUtil.isBlank(spec.ivFixed)) {
                return "固定 IV 不能为空";
            }
            if (parseIv(spec.ivFixed, spec.ivLength()) == null) {
                return MODE_GCM.equals(spec.mode)
                        ? "固定 IV 须为 12 或 16 字节明文或对应 hex"
                        : "固定 IV 须为 16 字节明文或 32 位 hex";
            }
        }
        return null;
    }

    public void applyTo(HostPrivacyProfile profile) {
        if (profile == null) {
            return;
        }
        PrivacyDecryptSpec spec = normalize();
        profile.setDecryptAlg(spec.family);
        profile.setDecryptMode(spec.mode);
        profile.setDecryptEncoding(spec.encoding);
        profile.setDecryptIvMode(spec.ivMode);
        profile.setDecryptIvFixed(spec.ivFixed);
    }

    public boolean isPlain() {
        return FAMILY_PLAIN.equalsIgnoreCase(family);
    }

    public boolean isMissing() {
        return FAMILY_MISSING.equalsIgnoreCase(family);
    }

    public boolean yamlKeyFallback() {
        return FAMILY_SM4.equalsIgnoreCase(family);
    }

    public boolean isAes() {
        return FAMILY_AES.equalsIgnoreCase(family);
    }

    public boolean isGcm() {
        return MODE_GCM.equalsIgnoreCase(mode);
    }

    public boolean isEcb() {
        return MODE_ECB.equalsIgnoreCase(mode);
    }

    public int ivLength() {
        if (isEcb()) {
            return 0;
        }
        return isGcm() ? GCM_IV_LEN : CBC_IV_LEN;
    }

    public String label() {
        if (isPlain()) {
            return "明文";
        }
        if (isMissing()) {
            return "缺失";
        }
        String m = StrUtil.blankToDefault(mode, MODE_CBC);
        String e = StrUtil.blankToDefault(encoding, ENC_HEX);
        return family + "/" + m + " · " + e;
    }

    static String normalizeMode(String raw) {
        if (StrUtil.isBlank(raw)) {
            return MODE_CBC;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case MODE_ECB -> MODE_ECB;
            case MODE_GCM -> MODE_GCM;
            default -> MODE_CBC;
        };
    }

    static String normalizeEncoding(String raw) {
        if (StrUtil.isBlank(raw)) {
            return ENC_HEX;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("BASE64".equals(t) || "B64".equals(t)) {
            return ENC_BASE64;
        }
        return ENC_HEX;
    }

    static String normalizeIvMode(String raw) {
        if (StrUtil.isBlank(raw)) {
            return IV_PREPEND;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case IV_NONE, "ZERO", "ZEROS" -> IV_NONE;
            case IV_FIXED, "CONST", "CONSTANT" -> IV_FIXED;
            default -> IV_PREPEND;
        };
    }

    static byte[] parseIv(String configured, int preferredLen) {
        if (StrUtil.isBlank(configured)) {
            return null;
        }
        if (preferredLen == GCM_IV_LEN) {
            byte[] gcm = PrivacyCryptoService.parseSymmetricKey(configured.trim(), GCM_IV_LEN, CBC_IV_LEN);
            if (gcm != null) {
                return gcm;
            }
        }
        int len = preferredLen > 0 ? preferredLen : CBC_IV_LEN;
        return PrivacyCryptoService.parseSymmetricKey(configured.trim(), len);
    }

    static byte[] zeros(int len) {
        return new byte[Math.max(0, len)];
    }

    public static byte[] decodePacked(String text, String encoding) {
        String t = text.trim();
        if (ENC_BASE64.equals(normalizeEncoding(encoding))) {
            return cn.hutool.core.codec.Base64.decode(t);
        }
        String h = t.startsWith("0x") || t.startsWith("0X") ? t.substring(2) : t;
        if (h.length() < 2 || (h.length() % 2) != 0) {
            throw new IllegalArgumentException("密文长度非法");
        }
        return HexUtil.decodeHex(h);
    }

    public static String encodePacked(byte[] raw, String encoding) {
        if (ENC_BASE64.equals(normalizeEncoding(encoding))) {
            return cn.hutool.core.codec.Base64.encode(raw);
        }
        return HexUtil.encodeHexStr(raw);
    }
}
