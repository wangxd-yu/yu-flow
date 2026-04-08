package org.yu.flow.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程引擎异常基类
 * 增强版：包含错误码、节点ID、上下文快照、严重等级
 *
 * @author yu-flow
 * @date 2025-05-03 16:12 (updated 2026-01-31)
 */
public class FlowException extends RuntimeException {

    /**
     * 错误严重等级
     */
    public enum Severity {
        FATAL,    // 致命错误，立即停止流程
        ERROR,    // 错误，可重试或降级
        WARNING   // 警告，可继续执行
    }

    private final String errorCode;                // 错误码
    private final String stepId;                   // 出错的节点ID
    private final Map<String, Object> context;     // 上下文快照
    private final Severity severity;               // 严重等级
    private final Instant timestamp;               // 错误时间戳

    // ========== 构造函数 ==========

    public FlowException(String errorCode, String message) {
        this(errorCode, message, null, null, null, Severity.ERROR);
    }

    public FlowException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, null, null, cause, Severity.ERROR);
    }

    public FlowException(String errorCode, String message, String stepId) {
        this(errorCode, message, stepId, null, null, Severity.ERROR);
    }

    public FlowException(String errorCode, String message, String stepId, Map<String, Object> context) {
        this(errorCode, message, stepId, context, null, Severity.ERROR);
    }

    public FlowException(String errorCode, String message, String stepId,
                         Map<String, Object> context, Throwable cause, Severity severity) {
        super(formatMessage(errorCode, message, stepId), cause);
        this.errorCode = errorCode;
        this.stepId = stepId;
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.severity = severity != null ? severity : Severity.ERROR;
        this.timestamp = Instant.now();
    }

    // ========== Getters ==========

    public String getErrorCode() {
        return errorCode;
    }

    public String getStepId() {
        return stepId;
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }

    public Severity getSeverity() {
        return severity;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    // ========== 辅助方法 ==========

    private static String formatMessage(String errorCode, String message, String stepId) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode).append("]");
        if (stepId != null) {
            sb.append(" [Step: ").append(stepId).append("]");
        }
        sb.append(" ").append(message);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "FlowException{" +
                "errorCode='" + errorCode + '\'' +
                ", stepId='" + stepId + '\'' +
                ", severity=" + severity +
                ", timestamp=" + timestamp +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
