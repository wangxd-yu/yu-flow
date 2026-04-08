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
import org.yu.flow.engine.model.validation.ValidationRule;

/**
 * Start 起点节点
 * 流程的入口点，接收输入参数
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class StartStep extends Step {
    // Start 节点接收的参数会存储到上下文中

    /**
     * 参数校验规则
     * key: 参数名
     * value: 校验规则
     */
    private Map<String, ValidationRule> validations;

    @Override
    public String getType() {
        return NodeType.START;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
