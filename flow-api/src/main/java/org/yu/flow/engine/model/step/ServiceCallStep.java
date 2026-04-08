package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Arrays;
import java.util.List;

/**
 * @author yu-flow
 * @date 2025-04-10 19:44
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ServiceCallStep extends Step {
    private String service;
    private String method;
    private List<String> args;
    private String output;

    @Override
    public String getType() {
        return NodeType.CALL;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
