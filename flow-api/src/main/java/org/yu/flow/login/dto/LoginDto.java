package org.yu.flow.login.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 登录请求。
 *
 * <p>口令须经 SM2 加密后放入 {@link #passwordCipher}，服务端解密后再验密。
 * 不再接受明文 {@code password} 字段。</p>
 *
 * @author yu-flow
 * @date 2025-07-28 23:09
 */
@Data
public class LoginDto {
    private String username;
    /**
     * SM2 密文（hex）。明文载荷：{@code {epochMillis}:{password}}。
     */
    private String passwordCipher;
    /** 验证码会话 ID（由 /login/captcha 下发） */
    private String captchaId;
    /** 用户输入的验证码 */
    private String captchaCode;

    /**
     * 服务端解密后的口令明文（仅进程内使用；JSON 忽略，客户端勿传）。
     */
    @JsonIgnore
    private String password;
}
