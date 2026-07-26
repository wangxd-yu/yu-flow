package org.yu.flow.integration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.yu.flow.exception.FlowException;
import org.yu.flow.exception.YuFlowExceptionHandler;
import org.yu.flow.module.rbac.controller.AuthController;
import org.yu.flow.module.rbac.service.RbacService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 全局异常处理器（YuFlowExceptionHandler）独立单元集成测试。
 *
 * <p>无需 Redis / 数据库，用 MockMvcBuilders.standaloneSetup 创建隔离测试环境，
 * 只加载 AuthController + YuFlowExceptionHandler，精准验证异常响应结构。</p>
 *
 * <p>安全验证重点（Phase 2.1）：
 * <ul>
 *   <li>RuntimeException 子类（NPE）响应中不暴露内部堆栈细节（脱敏）</li>
 *   <li>FlowException errorCode 映射到正确 HTTP 状态码</li>
 *   <li>IllegalArgumentException 消息透传到 400 响应</li>
 * </ul>
 * </p>
 */
@DisplayName("Phase 1.3 链路A - 全局异常处理器集成测试（Standalone MockMvc）")
class YuFlowExceptionHandlerIntegrationTest {

    private final RbacService rbacService = mock(RbacService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController())
            // 注入自定义异常处理器，使它生效于独立测试上下文
            .setControllerAdvice(new YuFlowExceptionHandler())
            .build();

    // ========== 登出接口——正常路径 ==========

    @Test
    @DisplayName("登出成功 → HTTP 200 + ok=true")
    void logout_returns200WithOkTrue() throws Exception {
        mockMvc.perform(post("/flow-api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    // ========== 异常处理器 HTTP 状态码与响应格式（通过 standaloneSetup ControllerAdvice 验证） ==========

    @Test
    @DisplayName("YuFlowExceptionHandler - RBAC_FORBIDDEN → HTTP 403 + ok=false")
    void flowException_rbacForbidden_returns403() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController(() ->
                        new FlowException("RBAC_FORBIDDEN", "权限不足")))
                .setControllerAdvice(new YuFlowExceptionHandler())
                .build();

        mvc.perform(get("/test-throw").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("YuFlowExceptionHandler - RBAC_UNAUTHORIZED → HTTP 401 + ok=false")
    void flowException_rbacUnauthorized_returns401() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController(() ->
                        new FlowException("RBAC_UNAUTHORIZED", "未认证")))
                .setControllerAdvice(new YuFlowExceptionHandler())
                .build();

        mvc.perform(get("/test-throw"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("YuFlowExceptionHandler - IllegalArgumentException → HTTP 400 + 消息透传")
    void illegalArgumentException_returns400WithMessage() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController(() ->
                        new IllegalArgumentException("旧密码不正确")))
                .setControllerAdvice(new YuFlowExceptionHandler())
                .build();

        mvc.perform(get("/test-throw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.msg").value("旧密码不正确"));
    }

    @Test
    @DisplayName("YuFlowExceptionHandler - RuntimeException子类(NPE) → HTTP 500 + 脱敏消息")
    void runtimeException_subclass_returns500WithSanitizedMessage() throws Exception {
        // 安全加固重点验证：NullPointerException 内部细节不应暴露给调用方
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController(() ->
                        new NullPointerException("internal db pointer null at row 42")))
                .setControllerAdvice(new YuFlowExceptionHandler())
                .build();

        mvc.perform(get("/test-throw"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.ok").value(false))
                // 脱敏验证：响应 msg 不含内部细节
                .andExpect(jsonPath("$.msg").value(
                        Matchers.not(Matchers.containsString("db pointer"))))
                // 统一友好提示（YuFlowExceptionHandler 兜底逻辑）
                .andExpect(jsonPath("$.msg").value(
                        Matchers.containsString("系统内部错误")));
    }

    @Test
    @DisplayName("YuFlowExceptionHandler - PUBLISH_GATE_BLOCKED → HTTP 400 + ok=false")
    void flowException_publishGateBlocked_returns400() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController(() ->
                        new FlowException("PUBLISH_GATE_BLOCKED", "发布门禁检查失败")))
                .setControllerAdvice(new YuFlowExceptionHandler())
                .build();

        mvc.perform(get("/test-throw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("YuFlowExceptionHandler - FlowException(FATAL severity) → HTTP 500 + ok=false")
    void flowException_fatalSeverity_returns500() throws Exception {
        FlowException ex = new FlowException(
                "ENGINE_FATAL", "引擎崩溃", null, null, null,
                FlowException.Severity.FATAL);

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController(() -> ex))
                .setControllerAdvice(new YuFlowExceptionHandler())
                .build();

        mvc.perform(get("/test-throw"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.ok").value(false));
    }

    // ========== 辅助：可配置抛异常的测试 Controller ==========

    /**
     * 测试专用 Controller：每次 GET /test-throw 抛出指定异常，
     * 用于单独验证 YuFlowExceptionHandler 的处理逻辑。
     */
    @org.springframework.web.bind.annotation.RestController
    @org.yu.flow.annotation.YuFlowApi
    static class ThrowingController {

        private final java.util.function.Supplier<RuntimeException> exceptionSupplier;

        ThrowingController(java.util.function.Supplier<RuntimeException> supplier) {
            this.exceptionSupplier = supplier;
        }

        @org.springframework.web.bind.annotation.GetMapping("/test-throw")
        public void throwException() {
            throw exceptionSupplier.get();
        }
    }
}
