package org.yu.flow.module.rbac.support;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.service.RbacService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RBAC 权限切面 {@link RequirePermAspect} 单测：
 * 覆盖未登录 401（RBAC_UNAUTHORIZED）、无权限 403（RBAC_FORBIDDEN）、
 * 放行分支，以及 view/write 按 HTTP 方法分流的 {@code resolveRequiredPerms}。
 */
@DisplayName("RequirePermAspect 权限切面")
@ExtendWith(MockitoExtension.class)
class RequirePermAspectTest {

    @Mock
    private RbacService rbacService;
    @Mock
    private JoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    @InjectMocks
    private RequirePermAspect aspect;

    /** 切面反射目标：方法级注解 / 无注解两种形态 */
    static class GuardedTarget {
        @RequirePerm({"api:view"})
        public void guarded() {
        }

        public void plain() {
        }
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void stubJoinPoint(String methodName) throws NoSuchMethodException {
        Method method = GuardedTarget.class.getMethod(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
    }

    private void bindRequest(String httpMethod) {
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/flow/api/test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ==================== check() ====================

    @Test
    @DisplayName("RBAC 未启用：直接放行，不做任何鉴权")
    void rbacDisabled_skipsCheck() {
        when(rbacService.isRbacEnabled()).thenReturn(false);

        assertDoesNotThrow(() -> aspect.check(joinPoint));
        verify(rbacService, never()).hasAnyPerm(anyString(), any(String[].class));
    }

    @Test
    @DisplayName("方法与类均无 @RequirePerm：放行")
    void noAnnotation_skipsCheck() throws NoSuchMethodException {
        when(rbacService.isRbacEnabled()).thenReturn(true);
        stubJoinPoint("plain");

        assertDoesNotThrow(() -> aspect.check(joinPoint));
        verify(rbacService, never()).hasAnyPerm(anyString(), any(String[].class));
    }

    @Test
    @DisplayName("未登录：抛 RBAC_UNAUTHORIZED")
    void notLoggedIn_throwsUnauthorized() throws NoSuchMethodException {
        when(rbacService.isRbacEnabled()).thenReturn(true);
        stubJoinPoint("guarded");

        try (MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class)) {
            jwt.when(JwtTokenUtil::currentUsername).thenReturn(null);

            FlowException ex = assertThrows(FlowException.class, () -> aspect.check(joinPoint));
            assertEquals("RBAC_UNAUTHORIZED", ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("已登录但无权限：抛 RBAC_FORBIDDEN（403）")
    void noPermission_throwsForbidden() throws NoSuchMethodException {
        when(rbacService.isRbacEnabled()).thenReturn(true);
        stubJoinPoint("guarded");
        when(rbacService.hasAnyPerm(eq("alice"), any(String[].class))).thenReturn(false);

        try (MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class)) {
            jwt.when(JwtTokenUtil::currentUsername).thenReturn("alice");

            FlowException ex = assertThrows(FlowException.class, () -> aspect.check(joinPoint));
            assertEquals("RBAC_FORBIDDEN", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("api:view"));
        }
    }

    @Test
    @DisplayName("已登录且有权限：放行")
    void hasPermission_passes() throws NoSuchMethodException {
        when(rbacService.isRbacEnabled()).thenReturn(true);
        stubJoinPoint("guarded");
        when(rbacService.hasAnyPerm(eq("alice"), any(String[].class))).thenReturn(true);

        try (MockedStatic<JwtTokenUtil> jwt = mockStatic(JwtTokenUtil.class)) {
            jwt.when(JwtTokenUtil::currentUsername).thenReturn("alice");

            assertDoesNotThrow(() -> aspect.check(joinPoint));
        }
    }

    // ==================== resolveRequiredPerms 分流 ====================

    @Test
    @DisplayName("仅声明 view：保持原 OR 语义不分流")
    void onlyView_keepsOriginal() {
        String[] annotated = {"api:view"};
        assertArrayEquals(annotated, RequirePermAspect.resolveRequiredPerms(annotated));
    }

    @Test
    @DisplayName("非 view/write 后缀权限码：原样返回")
    void otherCodes_keepOriginal() {
        String[] annotated = {"api:manage", "task:run"};
        assertArrayEquals(annotated, RequirePermAspect.resolveRequiredPerms(annotated));
    }

    @Test
    @DisplayName("view+write 且 GET 请求：只要求 view")
    void viewAndWrite_getRequest_requiresView() {
        bindRequest("GET");
        String[] resolved = RequirePermAspect.resolveRequiredPerms(
                new String[]{"api:view", "api:write"});
        assertArrayEquals(new String[]{"api:view"}, resolved);
    }

    @Test
    @DisplayName("view+write 且 POST 请求：必须持有 write")
    void viewAndWrite_postRequest_requiresWrite() {
        bindRequest("POST");
        String[] resolved = RequirePermAspect.resolveRequiredPerms(
                new String[]{"api:view", "api:write"});
        assertArrayEquals(new String[]{"api:write"}, resolved);
    }

    @Test
    @DisplayName("view+write 但无请求上下文：按写方法处理（保守要求 write）")
    void viewAndWrite_noRequestContext_requiresWrite() {
        RequestContextHolder.resetRequestAttributes();
        String[] resolved = RequirePermAspect.resolveRequiredPerms(
                new String[]{"api:view", "api:write"});
        assertArrayEquals(new String[]{"api:write"}, resolved);
    }

    @Test
    @DisplayName("空白权限码被忽略，不影响分流")
    void blankCodes_ignored() {
        bindRequest("GET");
        String[] resolved = RequirePermAspect.resolveRequiredPerms(
                new String[]{"  ", "api:view", "api:write"});
        assertArrayEquals(new String[]{"api:view"}, resolved);
    }

    @Test
    @DisplayName("null / 空数组：原样返回")
    void nullOrEmpty_returnsAsIs() {
        assertNull(RequirePermAspect.resolveRequiredPerms(null));
        String[] empty = new String[0];
        assertArrayEquals(empty, RequirePermAspect.resolveRequiredPerms(empty));
    }
}
