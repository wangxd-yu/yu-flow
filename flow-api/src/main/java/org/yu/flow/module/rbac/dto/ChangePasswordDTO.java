package org.yu.flow.module.rbac.dto;

import lombok.Data;

@Data
public class ChangePasswordDTO {
    /** 当前密码 */
    private String oldPassword;
    /** 新密码 */
    private String newPassword;
    /** 确认新密码（可选，前端主校验；后端若传入则再核一次） */
    private String confirmPassword;
}
