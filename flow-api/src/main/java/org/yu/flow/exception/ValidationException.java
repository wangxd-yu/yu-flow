package org.yu.flow.exception;

/**
 * 验证异常：流程定义、节点配置不正确
 */
public class ValidationException extends FlowException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, null, null, null, Severity.ERROR);
    }

    public ValidationException(String message, String stepId) {
        super("VALIDATION_ERROR", message, stepId, null, null, Severity.ERROR);
    }
}
