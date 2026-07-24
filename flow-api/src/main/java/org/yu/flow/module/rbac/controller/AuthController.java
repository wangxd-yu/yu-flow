package org.yu.flow.module.rbac.controller;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.dto.R;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.dto.AuthMeDTO;
import org.yu.flow.module.rbac.dto.ChangePasswordDTO;
import org.yu.flow.module.rbac.service.RbacService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/auth")
public class AuthController {

    @Resource
    private RbacService rbacService;

    @GetMapping("/me")
    public R<AuthMeDTO> me() {
        String username = JwtTokenUtil.currentUsername();
        AuthMeDTO me = rbacService.buildMe(username);
        if (me == null) {
            return R.fail("未登录");
        }
        return R.ok(me);
    }

    /**
     * 当前用户修改密码（需登录；校验旧密码与复杂度）。
     */
    @PostMapping("/change-password")
    public R<Boolean> changePassword(@RequestBody ChangePasswordDTO dto) {
        String username = JwtTokenUtil.currentUsername();
        if (StrUtil.isBlank(username)) {
            return R.fail("未登录");
        }
        if (dto == null) {
            return R.fail("请求体不能为空");
        }
        if (StrUtil.isNotBlank(dto.getConfirmPassword())
                && !dto.getConfirmPassword().equals(dto.getNewPassword())) {
            return R.fail("两次输入的新密码不一致");
        }
        try {
            rbacService.changeOwnPassword(username, dto.getOldPassword(), dto.getNewPassword());
            return R.ok(true, "密码已更新，请使用新密码重新登录");
        } catch (FlowException e) {
            return R.fail(stripErrorCode(e.getMessage()));
        } catch (Exception e) {
            return R.fail("修改密码失败：" + e.getMessage());
        }
    }

    /** FlowException 消息形如 [CODE] xxx，对用户只展示可读部分 */
    private static String stripErrorCode(String message) {
        if (message == null) {
            return "操作失败";
        }
        int idx = message.indexOf("] ");
        if (message.startsWith("[") && idx > 0) {
            return message.substring(idx + 2).trim();
        }
        return message;
    }
}
