package org.yu.flow.engine.model;

import org.yu.flow.exception.FlowException;

// 重试专用异常
public class RetryStepException extends FlowException {
    private final String stepId;

    public RetryStepException(String stepId) {
        super("RETRY_STEP", "重试步骤: " + stepId);
        this.stepId = stepId;
    }

    public String getStepId() {
        return stepId;
    }
}
