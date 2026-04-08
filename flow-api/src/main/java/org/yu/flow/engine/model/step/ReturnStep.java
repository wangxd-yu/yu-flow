package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.NodeType;

import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ReturnStep extends Step {
    private String value;

    @Override
    public String getType() {
        return NodeType.RETURN;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Collections.emptyList(); // Return 节点没有输出端口
    }
}
