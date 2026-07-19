package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.Arrays;
import java.util.List;

/**
 * 并行网关：图上从 out 拉多条边即并行扇出。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ParallelStep extends Step {
    private ErrorMode errorMode = ErrorMode.FAST_FAIL;

    @Override
    public String getType() {
        return NodeType.PARALLEL;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }

    public enum ErrorMode {
        FAST_FAIL, CONTINUE
    }
}
