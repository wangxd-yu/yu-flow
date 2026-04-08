package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.NodeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yu-flow
 * @date 2025-04-10 19:45
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ParallelStep extends Step {
    private List<Step> tasks = new ArrayList<>();
    private ErrorMode errorMode = ErrorMode.FAST_FAIL;

    @Override
    public String getType() {
        return NodeType.PARALLEL;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output("join"));
    }

    public enum ErrorMode {
        FAST_FAIL, CONTINUE
    }

    public static class ParallelResult {
        private boolean hasFailures;
        private final List<String> failedTasks = new ArrayList<>();

        public boolean containsFailures() {
            return hasFailures;
        }

        public void markFailed(String taskId) {
            hasFailures = true;
            failedTasks.add(taskId);
        }

        public void markSuccess() {
            hasFailures = false;
        }
    }
}
