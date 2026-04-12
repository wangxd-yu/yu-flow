package org.yu.flow.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.yu.flow.dto.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器
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
@ControllerAdvice
public class GlobalExceptionHandler {

    // ========== 流程引擎异常 ==========

    @ExceptionHandler(FlowException.class)
    public ResponseEntity<R<?>> handleFlowException(FlowException ex, WebRequest request) {
        log.warn("[GlobalExceptionHandler] 流程异常: path={}, code={}, stepId={}",
                getRequestPath(request), ex.getErrorCode(), ex.getStepId());

        HttpStatus status = getHttpStatus(ex.getSeverity());
        R<?> response = R.fail(status.value(), ex.getMessage(), buildFlowDetail(ex));
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<R<?>> handleValidationException(ValidationException ex, WebRequest request) {
        log.warn("[GlobalExceptionHandler] 验证异常: path={}, stepId={}",
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
        log.warn("[GlobalExceptionHandler] 入参 Schema 校验失败: path={}, errors={}",
                getRequestPath(request), ex.getErrors());

        R<?> response = R.fail(400, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<R<?>> handleExecutionException(ExecutionException ex, WebRequest request) {
        log.error("[GlobalExceptionHandler] 执行异常: path={}, stepId={}",
                getRequestPath(request), ex.getStepId());

        R<?> response = R.fail(500, ex.getMessage(), buildFlowDetail(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========== 通用异常 ==========

    /**
     * 处理参数校验失败（如 ValidationRule 主动抛出的 IllegalArgumentException）
     * 此类异常由业务方主动抛出，错误信息已为用户可读，允许透传。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<?>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("[GlobalExceptionHandler] 参数校验失败: path={}, message={}",
                getRequestPath(request), ex.getMessage());

        R<?> response = R.fail(400, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 兜底处理器：捕获所有未预期的 RuntimeException。
     * 安全要求：原始异常消息（可能包含 SQL、表名等敏感信息）绝不透传给客户端。
     * 完整堆栈仅在服务端日志中记录。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<R<?>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        // 服务端记录完整堆栈，便于排查，不向外暴露
        log.error("[GlobalExceptionHandler] 未预期的运行时异常: path={}", getRequestPath(request), ex);

        R<?> response = R.fail(500, "系统内部错误，请联系管理员");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========== 辅助方法 ==========

    private HttpStatus getHttpStatus(FlowException.Severity severity) {
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

    /**
     * 从 FlowException 中提取 stepId / context 等调试信息，放入 R.data。
     * 仅包含非空字段，避免前端收到大量 null 值。
     */
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
