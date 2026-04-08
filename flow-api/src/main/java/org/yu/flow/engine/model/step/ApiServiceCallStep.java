package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author yu-flow
 * @date 2025-04-10 19:44
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ApiServiceCallStep extends Step {
    private String serviceId;
    private String type;
    private Map<String, String> args;
    private String output;

    @Override
    public String getType() {
        return NodeType.API;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
