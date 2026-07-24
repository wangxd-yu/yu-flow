package org.yu.flow.module.open.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;

import java.nio.charset.StandardCharsets;

/**
 * 开放平台 HMAC 签名串构造与验签辅助（供网关与单测 / 联调脚本共用）。
 *
 * <pre>
 * METHOD\nrealPath\ntimestamp\nnonce\nbodySha256OrEmpty
 * </pre>
 */
public final class OpenAuthSignatures {

    private OpenAuthSignatures() {
    }

    public static String buildPayload(String method, String realPath,
                                      String timestamp, String nonce, String bodySha256OrEmpty) {
        String m = method == null ? "" : method.trim().toUpperCase();
        String path = realPath == null ? "" : realPath;
        String ts = timestamp == null ? "" : timestamp.trim();
        String n = nonce == null ? "" : nonce.trim();
        String body = bodySha256OrEmpty == null ? "" : bodySha256OrEmpty;
        return m + "\n" + path + "\n" + ts + "\n" + n + "\n" + body;
    }

    public static String signHex(String appSecret, String payload) {
        if (StrUtil.isBlank(appSecret)) {
            throw new IllegalArgumentException("appSecret 不能为空");
        }
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, appSecret.getBytes(StandardCharsets.UTF_8));
        return hmac.digestHex(payload == null ? "" : payload);
    }

    public static String sign(String appSecret, String method, String realPath,
                              String timestamp, String nonce, String bodySha256OrEmpty) {
        return signHex(appSecret, buildPayload(method, realPath, timestamp, nonce, bodySha256OrEmpty));
    }
}
