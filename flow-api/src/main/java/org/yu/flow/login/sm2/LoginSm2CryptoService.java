package org.yu.flow.login.sm2;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yu.flow.config.YuFlowProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

/**
 * 登录口令 SM2 加解密（与前端 sm-crypto cipherMode=1 / C1C3C2 对齐）。
 *
 * <p>明文载荷格式：{@code {epochMillis}:{password}}，并校验时间窗防重放。</p>
 */
@Service
public class LoginSm2CryptoService {

    private static final Logger log = LoggerFactory.getLogger(LoginSm2CryptoService.class);

    /** 与 sm-crypto {@code cipherMode: 1} 一致 */
    public static final int CIPHER_MODE_C1C3C2 = 1;

    private static final long CLOCK_SKEW_MS = 60_000L;

    private final YuFlowProperties properties;

    private SM2 sm2;
    private String publicKeyHex;
    private long passwordTtlSeconds = 300L;

    public LoginSm2CryptoService(YuFlowProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        YuFlowProperties.Security security = properties.getSecurity();
        this.passwordTtlSeconds = security != null && security.getSm2PasswordTtlSeconds() > 0
                ? security.getSm2PasswordTtlSeconds()
                : 300L;

        String privateKeyHex = security != null ? StrUtil.trim(security.getSm2PrivateKey()) : null;
        String publicKeyHexCfg = security != null ? StrUtil.trim(security.getSm2PublicKey()) : null;
        boolean hasPrivate = StrUtil.isNotBlank(privateKeyHex);
        boolean hasPublic = StrUtil.isNotBlank(publicKeyHexCfg);
        if (hasPrivate ^ hasPublic) {
            throw new IllegalStateException(
                    "[SM2] yu.flow.security.sm2-private-key 与 sm2-public-key 须同时配置或同时留空");
        }

        if (hasPrivate) {
            this.publicKeyHex = ensureUncompressedPublicKeyHex(normalizeHex(publicKeyHexCfg));
            this.sm2 = SmUtil.sm2(normalizePrivateKeyHex(privateKeyHex), this.publicKeyHex);
        } else {
            KeyPair keyPair = SecureUtil.generateKeyPair("SM2");
            this.sm2 = SmUtil.sm2(keyPair.getPrivate(), keyPair.getPublic());
            this.publicKeyHex = toUncompressedPublicKeyHex(keyPair.getPublic());
            log.warn("[SM2] 未配置固定密钥，已生成进程内临时密钥对；多节点部署请设置 YU_FLOW_SM2_PRIVATE_KEY / YU_FLOW_SM2_PUBLIC_KEY");
            if (log.isDebugEnabled()) {
                log.debug("[SM2] 临时私钥 fingerprint={}", fingerprint(toPrivateKeyHex(keyPair.getPrivate())));
            }
        }
        this.sm2.setMode(SM2Engine.Mode.C1C3C2);
        log.info("[SM2] 登录口令加密已启用，publicKeyFingerprint={}, ttlSeconds={}",
                fingerprint(this.publicKeyHex), this.passwordTtlSeconds);
    }

    public String getPublicKeyHex() {
        return publicKeyHex;
    }

    public long getPasswordTtlSeconds() {
        return passwordTtlSeconds;
    }

    public int getCipherMode() {
        return CIPHER_MODE_C1C3C2;
    }

    /**
     * 解密前端 SM2 密文并校验时间窗，返回口令明文。
     *
     * @param passwordCipher hex 密文（sm-crypto 输出，可无 {@code 04} 前缀）
     */
    public String decryptLoginPassword(String passwordCipher) {
        if (StrUtil.isBlank(passwordCipher)) {
            throw new IllegalArgumentException("密码密文不能为空");
        }
        String hex = normalizeHex(passwordCipher);
        if (hex.length() < 194 || (hex.length() % 2) != 0) {
            throw new IllegalArgumentException("密码密文格式非法");
        }
        // sm-crypto 密文通常不含 04 前缀；Hutool/BC 解密需要未压缩点前缀
        if (!hex.startsWith("04")) {
            hex = "04" + hex;
        }

        final String payload;
        try {
            byte[] plainBytes = sm2.decrypt(HexUtil.decodeHex(hex), KeyType.PrivateKey);
            payload = new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("[SM2] 登录口令解密失败: {}", e.getMessage());
            throw new IllegalArgumentException("密码解密失败");
        }

        return parseAndValidatePayload(payload);
    }

    /**
     * 解密前端 SM2 密文为原始字节（无时间窗）。用于隐私传输会话密钥。
     */
    public byte[] decryptRaw(String cipherHex) {
        if (StrUtil.isBlank(cipherHex)) {
            throw new IllegalArgumentException("密文不能为空");
        }
        String hex = normalizeHex(cipherHex);
        if ((hex.length() % 2) != 0) {
            throw new IllegalArgumentException("密文格式非法");
        }
        if (!hex.startsWith("04")) {
            hex = "04" + hex;
        }
        try {
            return sm2.decrypt(HexUtil.decodeHex(hex), KeyType.PrivateKey);
        } catch (Exception e) {
            throw new IllegalArgumentException("会话密钥解密失败");
        }
    }

    /**
     * 供单测：用当前公钥加密载荷（C1C3C2），输出与 sm-crypto 相近的无 04 前缀 hex。
     */
    public String encryptForTest(String payload) {
        byte[] cipher = sm2.encrypt(payload.getBytes(StandardCharsets.UTF_8), KeyType.PublicKey);
        String hex = HexUtil.encodeHexStr(cipher);
        return hex.startsWith("04") ? hex.substring(2) : hex;
    }

    private String parseAndValidatePayload(String payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("密码解密失败");
        }
        int sep = payload.indexOf(':');
        if (sep <= 0 || sep >= payload.length() - 1) {
            throw new IllegalArgumentException("密码载荷格式非法");
        }
        String tsPart = payload.substring(0, sep);
        String password = payload.substring(sep + 1);
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        final long ts;
        try {
            ts = Long.parseLong(tsPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("密码载荷时间戳非法");
        }
        long now = System.currentTimeMillis();
        long ttlMs = passwordTtlSeconds * 1000L;
        if (ts > now + CLOCK_SKEW_MS) {
            throw new IllegalArgumentException("密码密文尚未生效，请校准客户端时间");
        }
        if (now - ts > ttlMs + CLOCK_SKEW_MS) {
            throw new IllegalArgumentException("密码密文已过期，请重新登录");
        }
        return password;
    }

    private static String normalizeHex(String hex) {
        String h = hex.trim();
        if (h.startsWith("0x") || h.startsWith("0X")) {
            h = h.substring(2);
        }
        return h.toLowerCase();
    }

    /** 私钥 D 值固定为 32 字节 hex（64 字符），去掉 BigInteger 符号位多余 00。 */
    private static String normalizePrivateKeyHex(String hex) {
        String h = normalizeHex(hex);
        if (h.length() == 66 && h.startsWith("00")) {
            h = h.substring(2);
        }
        if (h.length() < 64) {
            h = StrUtil.padPre(h, 64, '0');
        }
        if (h.length() != 64) {
            throw new IllegalStateException("[SM2] 私钥须为 32 字节 hex（64 字符），当前长度=" + h.length());
        }
        return h;
    }

    private static String ensureUncompressedPublicKeyHex(String hex) {
        String h = normalizeHex(hex);
        if (!h.startsWith("04")) {
            h = "04" + h;
        }
        if (h.length() != 130) {
            throw new IllegalStateException(
                    "[SM2] 公钥须为未压缩格式 hex（04 + X + Y，共 130 字符），当前长度=" + h.length());
        }
        return h;
    }

    private static String toUncompressedPublicKeyHex(java.security.PublicKey publicKey) {
        if (!(publicKey instanceof BCECPublicKey becPublicKey)) {
            throw new IllegalStateException("[SM2] 公钥类型不是 BCECPublicKey: " + publicKey.getClass());
        }
        // false = 未压缩点（04||X||Y），与 sm-crypto 一致
        return HexUtil.encodeHexStr(becPublicKey.getQ().getEncoded(false));
    }

    private static String toPrivateKeyHex(java.security.PrivateKey privateKey) {
        if (!(privateKey instanceof BCECPrivateKey becPrivateKey)) {
            throw new IllegalStateException("[SM2] 私钥类型不是 BCECPrivateKey: " + privateKey.getClass());
        }
        return normalizePrivateKeyHex(HexUtil.encodeHexStr(becPrivateKey.getD().toByteArray()));
    }

    private static String fingerprint(String publicKeyHex) {
        if (publicKeyHex == null || publicKeyHex.length() < 16) {
            return "n/a";
        }
        return publicKeyHex.substring(0, 8) + "…" + publicKeyHex.substring(publicKeyHex.length() - 8);
    }
}
