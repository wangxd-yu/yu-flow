package org.yu.flow.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.yu.flow.dto.R;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YuFlow 全局异常处理器脱敏测试。
 */
class YuFlowExceptionHandlerTest {

    private final YuFlowExceptionHandler handler = new YuFlowExceptionHandler();

    private WebRequestWrapper buildRequest() {
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setRequestURI("/flow/api/test");
        return new WebRequestWrapper(new ServletWebRequest(mock));
    }

    @Test
    void unexpectedException_returnsGenericMessage() {
        ResponseEntity<R<?>> response = handler.handleException(new RuntimeException("secret internal detail"), buildRequest());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getCode());
        assertFalse(response.getBody().getMsg().contains("secret internal detail"));
        assertTrue(response.getBody().getMsg().contains("系统内部错误") || response.getBody().getMsg().contains("内部错误"));
    }

    @Test
    void runtimeExceptionSubclass_returnsGenericMessage() {
        ResponseEntity<R<?>> response = handler.handleRuntimeException(new NullPointerException("NPE detail"), buildRequest());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getCode());
        assertFalse(response.getBody().getMsg().contains("NPE detail"));
    }

    @Test
    void plainRuntimeException_withMessage_returnsBusinessMessage() {
        ResponseEntity<R<?>> response = handler.handleRuntimeException(new RuntimeException("业务提示信息"), buildRequest());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertEquals("业务提示信息", response.getBody().getMsg());
    }

    // 简单包装，用于绕过 WebRequest 的 protected 构造
    static class WebRequestWrapper extends ServletWebRequest {
        WebRequestWrapper(ServletWebRequest request) {
            super(request.getRequest());
        }
    }
}
