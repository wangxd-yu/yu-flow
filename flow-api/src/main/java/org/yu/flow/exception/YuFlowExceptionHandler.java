package org.yu.flow.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * YuFlow 专属全局异常处理器
 * 统一处理流程引擎的所有异常，返回值统一使用 {@link R} 包装。
 *
 * <p>响应格式：
 * <pre>
 * {
 *   "ok": false,
 *   "code": 400/500,
 *   "msg": "用户可读消息",
 *   "data": { "stepId": "...", "context": {...} },  // 仅有额外上下文时才有
 *   "timestamp": 1729827392811
 * }
 * </pre>
 */
@Slf4j
@RestControllerAdvice(annotations = YuFlowApi.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class YuFlowExceptionHandler {

    // ========== 流程引擎异常 ==========

    @ExceptionHandler(FlowException.class)
    public ResponseEntity<R<?>> handleFlowException(FlowException ex, WebRequest request) {
        log.warn("[YuFlowExceptionHandler] 流程异常: path={}, code={}, stepId={}",
                getRequestPath(request), ex.getErrorCode(), ex.getStepId());

        HttpStatus status = getHttpStatus(ex.getSeverity());
        R<?> response = R.fail(status.value(), ex.getMessage(), buildFlowDetail(ex));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<R<?>> handleValidationException(ValidationException ex, WebRequest request) {
        log.warn("[YuFlowExceptionHandler] 验证异常: path={}, stepId={}",
                getRequestPath(request), ex.getStepId());

        Map<String, Object> detail = new LinkedHashMap<>();
        if (ex.getStepId() != null) {
            detail.put("stepId", ex.getStepId());
        }

        R<?> response = detail.isEmpty()
                ? R.fail(400, ex.getMessage())
                : R.fail(400, ex.getMessage(), detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ResponseEntity<R<?>> handleSchemaValidationException(SchemaValidationException ex, WebRequest request) {
        log.warn("[YuFlowExceptionHandler] 入参 Schema 校验失败: path={}, errors={}",
                getRequestPath(request), ex.getErrors());

        R<?> response = R.fail(400, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<R<?>> handleExecutionException(ExecutionException ex, WebRequest request) {
        log.error("[YuFlowExceptionHandler] 执行异常: path={}, stepId={}",
                getRequestPath(request), ex.getStepId());

        R<?> response = R.fail(500, ex.getMessage(), buildFlowDetail(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========== 通用异常 ==========

    /**
     * 处理参数校验失败（如 ValidationRule 主动抛出的 IllegalArgumentException）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<?>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("[YuFlowExceptionHandler] 参数校验失败: path={}, message={}",
                getRequestPath(request), ex.getMessage());

        R<?> response = R.fail(400, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 兜底处理器：捕获所有未预期的异常（只拦截引擎本身的 Controller）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<?>> handleException(Exception ex, WebRequest request) {
        log.error("[YuFlowExceptionHandler] 引擎内部未预期的异常: path={}", getRequestPath(request), ex);

        R<?> response = R.fail(500, "YuFlow 引擎系统内部错误，请联系管理员");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========== 辅助方法 ==========

    private HttpStatus getHttpStatus(FlowException.Severity severity) {
        if (severity == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        switch (severity) {
            case FATAL:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case ERROR:
                return HttpStatus.BAD_REQUEST;
            case WARNING:
                return HttpStatus.OK;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String getRequestPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private Map<String, Object> buildFlowDetail(FlowException ex) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (ex.getStepId() != null) {
            detail.put("stepId", ex.getStepId());
        }
        if (ex.getContext() != null && !ex.getContext().isEmpty()) {
            detail.put("context", ex.getContext());
        }
        if (ex.getSeverity() != null) {
            detail.put("severity", ex.getSeverity().name());
        }
        return detail;
    }
}
