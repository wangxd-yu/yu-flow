package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.HexUtil;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.login.sm2.LoginSm2CryptoService;
import org.yu.flow.module.api.dto.PrivacyDevSessionDTO;

import java.security.SecureRandom;

/**
 * 非生产环境签发隐私传输会话（与前端 {@code createPrivacySession} 同一套 SM2/SM4 约定）。
 */
@Service
public class PrivacyDevSessionService {

    private static final Profiles PRODUCTION = Profiles.of("prod", "production");
    private static final Profiles LOCAL_LIKE = Profiles.of("dev", "local", "test");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final YuFlowProperties yuFlowProperties;
    private final Environment environment;
    private final LoginSm2CryptoService loginSm2CryptoService;

    public PrivacyDevSessionService(YuFlowProperties yuFlowProperties,
                                    Environment environment,
                                    LoginSm2CryptoService loginSm2CryptoService) {
        this.yuFlowProperties = yuFlowProperties;
        this.environment = environment;
        this.loginSm2CryptoService = loginSm2CryptoService;
    }

    public boolean isAvailable() {
        if (yuFlowProperties.isDemoMode()) {
            return false;
        }
        if (environment.acceptsProfiles(PRODUCTION)) {
            return false;
        }
        if (yuFlowProperties.getPrivacy().isDevSessionEnabled()) {
            return true;
        }
        return environment.acceptsProfiles(LOCAL_LIKE);
    }

    /**
     * 生成 16 字节 SM4，明文载荷为 32 位 hex 字符串（与 sm-crypto 前端一致）。
     */
    public PrivacyDevSessionDTO createSession() {
        if (!isAvailable()) {
            throw new IllegalStateException("当前环境未开放隐私调试会话");
        }
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        String sm4KeyHex = HexUtil.encodeHexStr(key);
        String headerValue = loginSm2CryptoService.encryptUtf8(sm4KeyHex);
        return PrivacyDevSessionDTO.builder()
                .headerName(PrivacyCryptoService.HEADER_PRIVACY_KEY)
                .headerValue(headerValue)
                .sm4KeyHex(sm4KeyHex)
                .cipherMode(loginSm2CryptoService.getCipherMode())
                .build();
    }
}
