package org.yu.flow.login.dto;

import lombok.Data;

/**
 * @author yu-flow
 * @date 2025-07-28 23:09
 */
@Data
public class LoginDto {
    private String username;
    private String password;
    /** 验证码会话 ID（由 /login/captcha 下发） */
    private String captchaId;
    /** 用户输入的验证码 */
    private String captchaCode;
}
