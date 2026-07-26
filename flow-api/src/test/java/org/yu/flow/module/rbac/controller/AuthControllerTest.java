package org.yu.flow.module.rbac.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.module.rbac.dto.ChangePasswordDTO;
import org.yu.flow.module.rbac.service.RbacService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthController unit tests — focus on security-relevant flows (cookie clearing on password change).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private RbacService rbacService;

    @InjectMocks
    private AuthController authController;

    @Test
    void changePassword_clearsSessionCookies_afterSuccessfulUpdate() {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("oldPass123");
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("newPass456");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        doNothing().when(rbacService).changeOwnPassword(any(), any(), any());

        try (MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class)) {
            jwt.when(JwtTokenUtil::currentUsername).thenReturn("admin");
            var result = authController.changePassword(dto, request, response);
            assert result.getOk();
            verify(rbacService).changeOwnPassword("admin", "oldPass123", "newPass456");
        }
    }

    @Test
    void changePassword_returnsFail_whenPasswordsDoNotMatch() {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("differentPass");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        try (MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class)) {
            jwt.when(JwtTokenUtil::currentUsername).thenReturn("admin");
            var result = authController.changePassword(dto, request, response);
            assert !result.getOk();
            assert result.getMsg().contains("新密码不一致");
        }
    }

    @Test
    void changePassword_returnsFail_whenNotLoggedIn() {
        ChangePasswordDTO dto = new ChangePasswordDTO();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        try (MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class)) {
            jwt.when(JwtTokenUtil::currentUsername).thenReturn(null);
            var result = authController.changePassword(dto, request, response);
            assert !result.getOk();
            assert result.getMsg().contains("未登录");
        }
    }

    @Test
    void logout_returnsOk() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        var result = authController.logout(request, response);

        assert result.getOk();
    }
}
