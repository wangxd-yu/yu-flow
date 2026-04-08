package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.ConditionCase;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yu-flow
 * @date 2025-04-10 19:45
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ConditionStep extends Step {
    private String check;
    private List<ConditionCase> cases = new ArrayList<>();
    private String expression;    // Spring EL条件表达式

    @Override
    public String getType() {
        return NodeType.CONDITION;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        // 每个 case 对应一个输出端口，加一个 default 端口
        List<PortDefinition> ports = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            ports.add(PortDefinition.output("case_" + i));
        }
        ports.add(PortDefinition.output(PortNames.DEFAULT));
        return ports;
    }
}
