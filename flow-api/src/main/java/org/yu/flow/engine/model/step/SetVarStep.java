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
 * 设置变量步骤
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SetVarStep extends Step {
    private String expression; // 赋值表达式，如 "result={name:studentInfo.name,...}"

    @Override
    public String getType() {
        return NodeType.SET;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
