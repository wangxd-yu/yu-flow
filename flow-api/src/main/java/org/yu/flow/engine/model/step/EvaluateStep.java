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
 * Evaluate 表达式计算节点
 * 用于执行表达式并将结果存储到上下文
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class EvaluateStep extends Step {
    private String expression;  // 表达式或多行脚本
    private String language;    // aviator(默认)、spel、javascript、python 或 groovy

    @Override
    public String getType() {
        return NodeType.EVALUATE;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
