package org.yu.flow.exception;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理流程引擎的所有异常
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FlowException.class)
    public ResponseEntity<ErrorResponse> handleFlowException(FlowException ex, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .stepId(ex.getStepId())
                .context(ex.getContext())
                .severity(ex.getSeverity().name())
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .build();

        HttpStatus status = getHttpStatus(ex.getSeverity());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .stepId(ex.getStepId())
                .severity(ex.getSeverity().name())
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ResponseEntity<ErrorResponse> handleSchemaValidationException(SchemaValidationException ex, WebRequest request) {
        log.warn("[GlobalExceptionHandler] 入参 Schema 校验失败: path={}, errors={}",
                getRequestPath(request), ex.getErrors());
        ErrorResponse response = ErrorResponse.builder()
                .code("SCHEMA_VALIDATION_ERROR")
                .message(ex.getMessage())
                .severity("WARNING")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<ErrorResponse> handleExecutionException(ExecutionException ex, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .stepId(ex.getStepId())
                .context(ex.getContext())
                .severity(ex.getSeverity().name())
                .timestamp(ex.getTimestamp())
                .path(getRequestPath(request))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理参数校验失败（如 ValidationRule 主动抛出的 IllegalArgumentException）
     * 此类异常由业务方主动抛出，错误信息已为用户可读，允许透传。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("[GlobalExceptionHandler] 参数校验失败: path={}, message={}",
                getRequestPath(request), ex.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .code("PARAM_INVALID")
                .message(ex.getMessage())
                .severity("WARNING")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 兜底处理器：捕获所有未预期的 RuntimeException。
     * 安全要求：原始异常消息（可能包含 SQL、表名等敏感信息）绝不透传给客户端。
     * 完整堆栈仅在服务端日志中记录。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, WebRequest request) {
        // 服务端记录完整堆栈，便于排查，不向外暴露
        log.error("[GlobalExceptionHandler] 未预期的运行时异常: path={}", getRequestPath(request), ex);
        ErrorResponse response = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("系统内部错误，请联系管理员")
                .severity("ERROR")
                .timestamp(Instant.now())
                .path(getRequestPath(request))
                .build();
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
     * 错误响应结构
     */
    @Data
    @Builder
    public static class ErrorResponse {
        private String code;                    // 错误码
        private String message;                 // 错误消息
        private String stepId;                  // 出错节点ID
        private Map<String, Object> context;    // 上下文信息
        private String severity;                // 严重等级
        private Instant timestamp;              // 时间戳
        private String path;                    // 请求路径
    }
}
