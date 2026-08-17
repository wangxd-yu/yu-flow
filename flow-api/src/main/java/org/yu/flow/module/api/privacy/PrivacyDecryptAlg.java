package org.yu.flow.module.api.privacy;

/**
 * 库内密文解密算法族。传输层 JSON 信封仍固定 SM4，与此无关。
 * <p>新配置存 {@link #SM4}/{@link #AES}/{@link #PLAIN}；{@link #SM4_CBC}/{@link #AES_CBC} 仅兼容旧 JSON。</p>
 */
public final class PrivacyDecryptAlg {

    public static final String SM4 = PrivacyDecryptSpec.FAMILY_SM4;
    public static final String AES = PrivacyDecryptSpec.FAMILY_AES;
    /** @deprecated 旧 JSON；等价 SM4 + CBC + HEX + IV 前置 */
    public static final String SM4_CBC = "SM4_CBC";
    /** @deprecated 旧 JSON；等价 AES + CBC + HEX + IV 前置 */
    public static final String AES_CBC = "AES_CBC";
    public static final String PLAIN = PrivacyDecryptSpec.FAMILY_PLAIN;
    public static final String MISSING = PrivacyDecryptSpec.FAMILY_MISSING;

    public static final String BUILTIN_PROFILE_ID = "builtin";

    private PrivacyDecryptAlg() {
    }

    /** 归一化为算法族：SM4 / AES / PLAIN / MISSING，未知值原样大写。 */
    public static String normalize(String raw) {
        return PrivacyDecryptSpec.fromAlg(raw).getFamily();
    }

    public static boolean isConfigurable(String alg) {
        String n = normalize(alg);
        return SM4.equals(n) || AES.equals(n) || PLAIN.equals(n);
    }
}
