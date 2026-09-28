package org.yu.flow.module.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 非生产联调：一次 SM4 会话密钥 + 已用登录 SM2 公钥加密的请求头值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyDevSessionDTO {

    /** 固定 {@code X-Privacy-Key} */
    private String headerName;

    /** 放入该头的 SM2 密文（hex，无 {@code 04} 前缀，cipherMode=C1C3C2） */
    private String headerValue;

    /** 16 字节 SM4 密钥的 32 位 hex，用于解开响应 {@code __p} 信封；勿写入日志或提交仓库 */
    private String sm4KeyHex;

    /** 与 {@code GET /flow-api/login/public-key} 一致，固定 1（C1C3C2） */
    private int cipherMode;
}
