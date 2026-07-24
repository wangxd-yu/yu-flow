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

        HttpStatus status = resolveHttpStatus(ex);
        R<?> response = R.failWithErrorCode(status.value(), ex.getErrorCode(), ex.getMessage());
        // 附带流程上下文（若有）
        if (ex.getStepId() != null || (ex.getContext() != null && !ex.getContext().isEmpty())) {
            response = R.fail(status.value(), ex.getMessage(), buildFlowDetail(ex));
        }
        return ResponseEntity.status(status).body(response);
    }

    private HttpStatus resolveHttpStatus(FlowException ex) {
        String code = ex.getErrorCode();
        if ("RBAC_FORBIDDEN".equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if ("RBAC_UNAUTHORIZED".equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        return getHttpStatus(ex.getSeverity());
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
     * 处理参数校验失败（如业务主动抛出的 IllegalArgumentException）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<?>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("[YuFlowExceptionHandler] 参数校验失败: path={}, message={}",
                getRequestPath(request), ex.getMessage());

        R<?> response = R.fail(400, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<?>> handleMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex, WebRequest request) {
        log.warn("[YuFlowExceptionHandler] Content-Type 不支持: path={}, contentType={}, message={}",
                getRequestPath(request), ex.getContentType(), ex.getMessage());
        String ct = ex.getContentType() != null ? ex.getContentType().toString() : "unknown";
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(R.fail(415, "不支持的 Content-Type: " + ct + "，请使用 application/json"));
    }

    @ExceptionHandler(org.yu.flow.module.mail.FlowMailException.class)
    public ResponseEntity<R<?>> handleFlowMailException(
            org.yu.flow.module.mail.FlowMailException ex, WebRequest request) {
        log.warn("[YuFlowExceptionHandler] 邮件异常: path={}, message={}",
                getRequestPath(request), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<R<?>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        log.error("[YuFlowExceptionHandler] 运行时异常: path={}", getRequestPath(request), ex);

        // 如果是直接抛出的 RuntimeException，通常是业务校验（如：“该目录下还有子目录”），将消息返回前端并返回 400
        // 如果是其他子类（如 NullPointerException），为了安全，依然模糊提示并返回 500
        if (ex.getClass() == RuntimeException.class && ex.getMessage() != null) {
            R<?> response = R.fail(400, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else {
            R<?> response = R.fail(500, "YuFlow 引擎系统内部错误，请联系管理员");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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
