package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SM4;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.login.sm2.LoginSm2CryptoService;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 库内密文解密（方案可选 SM4 / AES / 明文）+ JSON 明文传输 SM4。
 * <p>库内密钥不要复用数据源 AES（{@code yu.flow.security.aes-secret-key}）或登录 SM2 私钥。</p>
 */
@Slf4j
@Component
public class PrivacyCryptoService {

    public static final String HEADER_PRIVACY_KEY = "X-Privacy-Key";
    public static final String ENC_FLAG = "__p";
    public static final String ENC_ALG = "alg";
    public static final String ENC_VAL = "v";

    private static final int BLOCK = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private LoginSm2CryptoService loginSm2CryptoService;

    public Optional<String> decryptAtRest(String cipher) {
        return decryptAtRest(cipher, PrivacyDecryptSpec.sm4CbcDefault(), null);
    }

    public Optional<String> decryptAtRest(String cipher, String alg, String keyRaw) {
        return decryptAtRest(cipher, PrivacyDecryptSpec.fromAlg(alg), keyRaw);
    }

    public Optional<String> decryptAtRest(String cipher, PrivacyDecryptSpec spec, String keyRaw) {
        if (StrUtil.isBlank(cipher)) {
            return Optional.empty();
        }
        PrivacyDecryptSpec s = spec == null ? PrivacyDecryptSpec.sm4CbcDefault() : spec.normalize();
        if (s.isMissing()) {
            return Optional.empty();
        }
        if (s.isPlain()) {
            return Optional.of(cipher);
        }
        byte[] key = resolveAtRestKey(s, keyRaw);
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(decryptPacked(s, key, cipher.trim()));
        } catch (Exception e) {
            log.debug("[Privacy] 库内密文解密失败 alg={}: {}", s.label(), e.getMessage());
            return Optional.empty();
        }
    }

    /** 单测 / 文档示例：用当前 YAML 库内 SM4 密钥加密。 */
    public String encryptAtRest(String plain) {
        return encryptAtRest(plain, PrivacyDecryptSpec.sm4CbcDefault(), null);
    }

    public String encryptAtRest(String plain, String alg, String keyRaw) {
        return encryptAtRest(plain, PrivacyDecryptSpec.fromAlg(alg), keyRaw);
    }

    public String encryptAtRest(String plain, PrivacyDecryptSpec spec, String keyRaw) {
        PrivacyDecryptSpec s = spec == null ? PrivacyDecryptSpec.sm4CbcDefault() : spec.normalize();
        if (s.isPlain()) {
            return plain == null ? "" : plain;
        }
        byte[] key = resolveAtRestKey(s, keyRaw);
        if (key == null) {
            throw new IllegalStateException("未配置库内解密密钥");
        }
        return encryptPacked(s, key, plain == null ? "" : plain);
    }

    public Optional<byte[]> unwrapTransportKey(String sm2CipherHex) {
        if (StrUtil.isBlank(sm2CipherHex) || loginSm2CryptoService == null) {
            return Optional.empty();
        }
        try {
            byte[] raw = loginSm2CryptoService.decryptRaw(sm2CipherHex.trim());
            if (raw == null || raw.length == 0) {
                return Optional.empty();
            }
            if (raw.length == BLOCK) {
                return Optional.of(raw);
            }
            String asText = new String(raw, StandardCharsets.UTF_8).trim();
            if (asText.length() == BLOCK * 2 && asText.matches("(?i)[0-9a-f]+")) {
                return Optional.of(HexUtil.decodeHex(asText));
            }
            if (raw.length > BLOCK) {
                byte[] key = new byte[BLOCK];
                System.arraycopy(raw, 0, key, 0, BLOCK);
                return Optional.of(key);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("[Privacy] 传输密钥 SM2 解密失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, Object> wrapTransport(String plain, byte[] sm4Key) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put(ENC_FLAG, 1);
        node.put(ENC_ALG, ApiPrivacyConfig.ALG_SM4);
        node.put(ENC_VAL, sm4CbcEncrypt(sm4Key, plain == null ? "" : plain));
        return node;
    }

    public boolean isTransportEnvelope(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        Object flag = map.get(ENC_FLAG);
        return flag instanceof Number n && n.intValue() == 1 && map.get(ENC_VAL) != null;
    }

    /**
     * 解析方案密钥。SM4 仅 16 字节；AES 允许 16/24/32。
     * 空白返回 null（调用方再决定是否回退 YAML）。
     */
    public static byte[] parseAtRestKey(String configured, String alg) {
        if (StrUtil.isBlank(configured)) {
            return null;
        }
        PrivacyDecryptSpec spec = PrivacyDecryptSpec.fromAlg(alg);
        if (spec.isAes()) {
            return parseSymmetricKey(configured.trim(), 16, 24, 32);
        }
        return parseSymmetricKey(configured.trim(), 16);
    }

    private byte[] resolveAtRestKey(PrivacyDecryptSpec spec, String keyRaw) {
        byte[] fromProfile = parseAtRestKey(keyRaw, spec.getFamily());
        if (fromProfile != null) {
            return fromProfile;
        }
        if (spec.yamlKeyFallback()) {
            byte[] yaml = yamlSm4Key();
            if (yaml == null) {
                log.warn("[Privacy] 未配置方案密钥且未配置 yu.flow.privacy.at-rest-sm4-key，无法解密");
            }
            return yaml;
        }
        if (spec.isAes()) {
            log.warn("[Privacy] AES 方案未配置独立密钥（不会回退 YAML SM4 或数据源 AES）");
        }
        return null;
    }

    private byte[] yamlSm4Key() {
        YuFlowProperties.Privacy privacy = yuFlowProperties == null ? null : yuFlowProperties.getPrivacy();
        String configured = privacy == null ? null : StrUtil.trim(privacy.getAtRestSm4Key());
        byte[] key = parseAtRestKey(configured, PrivacyDecryptAlg.SM4);
        if (StrUtil.isNotBlank(configured) && key == null) {
            log.warn("[Privacy] at-rest-sm4-key 须为 16 字节明文或 32 位 hex");
        }
        return key;
    }

    static byte[] parseSymmetricKey(String configured, int... allowedUtf8Lens) {
        if (StrUtil.isBlank(configured) || allowedUtf8Lens == null || allowedUtf8Lens.length == 0) {
            return null;
        }
        String t = configured.trim();
        if (t.matches("(?i)[0-9a-f]+") && t.length() % 2 == 0) {
            try {
                byte[] hex = HexUtil.decodeHex(t);
                if (lengthAllowed(hex.length, allowedUtf8Lens)) {
                    return hex;
                }
            } catch (Exception ignore) {
                return null;
            }
        }
        byte[] utf8 = t.getBytes(StandardCharsets.UTF_8);
        return lengthAllowed(utf8.length, allowedUtf8Lens) ? utf8 : null;
    }

    private static boolean lengthAllowed(int len, int... allowed) {
        for (int a : allowed) {
            if (len == a) {
                return true;
            }
        }
        return false;
    }

    static String sm4CbcEncrypt(byte[] key, String plain) {
        return encryptPacked(PrivacyDecryptSpec.sm4CbcDefault(), key, plain);
    }

    static String sm4CbcDecrypt(byte[] key, String hex) {
        return decryptPacked(PrivacyDecryptSpec.sm4CbcDefault(), key, hex);
    }

    static String aesCbcEncrypt(byte[] key, String plain) {
        return encryptPacked(PrivacyDecryptSpec.fromAlg(PrivacyDecryptAlg.AES_CBC), key, plain);
    }

    static String aesCbcDecrypt(byte[] key, String hex) {
        return decryptPacked(PrivacyDecryptSpec.fromAlg(PrivacyDecryptAlg.AES_CBC), key, hex);
    }

    static String encryptPacked(PrivacyDecryptSpec spec, byte[] key, String plain) {
        PrivacyDecryptSpec s = spec.normalize();
        byte[] iv = null;
        int ivLen = s.ivLength();
        if (PrivacyDecryptSpec.IV_FIXED.equals(s.getIvMode())) {
            iv = PrivacyDecryptSpec.parseIv(s.getIvFixed(), ivLen);
            if (iv == null) {
                throw new IllegalArgumentException("固定 IV 无效");
            }
        } else if (PrivacyDecryptSpec.IV_PREPEND.equals(s.getIvMode())) {
            iv = new byte[ivLen];
            RANDOM.nextBytes(iv);
        } else if (!s.isEcb()) {
            iv = PrivacyDecryptSpec.zeros(ivLen > 0 ? ivLen : PrivacyDecryptSpec.CBC_IV_LEN);
        }
        byte[] body = cipherBytes(s, key, iv, plain.getBytes(StandardCharsets.UTF_8), true);
        if (PrivacyDecryptSpec.IV_PREPEND.equals(s.getIvMode()) && iv != null) {
            byte[] packed = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(body, 0, packed, iv.length, body.length);
            return PrivacyDecryptSpec.encodePacked(packed, s.getEncoding());
        }
        return PrivacyDecryptSpec.encodePacked(body, s.getEncoding());
    }

    static String decryptPacked(PrivacyDecryptSpec spec, byte[] key, String text) {
        PrivacyDecryptSpec s = spec.normalize();
        byte[] packed = PrivacyDecryptSpec.decodePacked(text, s.getEncoding());
        byte[] iv;
        byte[] body;
        int ivLen = s.ivLength();
        if (s.isEcb() || PrivacyDecryptSpec.IV_NONE.equals(s.getIvMode())) {
            iv = s.isEcb() ? null : PrivacyDecryptSpec.zeros(ivLen > 0 ? ivLen : PrivacyDecryptSpec.CBC_IV_LEN);
            body = packed;
        } else if (PrivacyDecryptSpec.IV_FIXED.equals(s.getIvMode())) {
            iv = PrivacyDecryptSpec.parseIv(s.getIvFixed(), ivLen);
            if (iv == null) {
                throw new IllegalArgumentException("固定 IV 无效");
            }
            body = packed;
        } else {
            int split = ivLen > 0 ? ivLen : PrivacyDecryptSpec.CBC_IV_LEN;
            if (packed.length <= split) {
                throw new IllegalArgumentException("密文过短");
            }
            iv = new byte[split];
            body = new byte[packed.length - split];
            System.arraycopy(packed, 0, iv, 0, split);
            System.arraycopy(packed, split, body, 0, body.length);
        }
        byte[] plain = cipherBytes(s, key, iv, body, false);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] cipherBytes(PrivacyDecryptSpec spec, byte[] key, byte[] iv, byte[] input, boolean encrypt) {
        String mode = PrivacyDecryptSpec.normalizeMode(spec.getMode());
        String padding = spec.isGcm() ? "NoPadding" : "PKCS5Padding";
        if (spec.isAes()) {
            AES aes = iv == null
                    ? new AES(mode, padding, key)
                    : new AES(mode, padding, key, iv);
            return encrypt ? aes.encrypt(input) : aes.decrypt(input);
        }
        SM4 sm4 = iv == null
                ? new SM4(mode, padding, key)
                : new SM4(mode, padding, key, iv);
        return encrypt ? sm4.encrypt(input) : sm4.decrypt(input);
    }
}
