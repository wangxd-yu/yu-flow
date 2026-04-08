package org.yu.flow.exception;

import java.util.Map;

/**
 * 执行异常：节点执行过程中发生的错误
 */
public class ExecutionException extends FlowException {

    public ExecutionException(String message, String stepId) {
        super("EXECUTION_ERROR", message, stepId);
    }

    public ExecutionException(String message, String stepId, Throwable cause) {
        super("EXECUTION_ERROR", message, stepId, null, cause, Severity.ERROR);
    }

    public ExecutionException(String message, String stepId, Map<String, Object> context, Throwable cause) {
        super("EXECUTION_ERROR", message, stepId, context, cause, Severity.ERROR);
    }
}
