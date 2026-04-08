package org.yu.flow.engine.model.step;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Arrays;
import java.util.List;

/**
 * IF 条件分支 (二选一)
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class IfStep extends Step {
    @JsonAlias("condition")  // 兼容 'condition' 和 'expression' 两种字段名
    private String expression; // 布尔表达式，如 age > 18 (Aviator) 或 #age > 18 (SpEL)
    private String language;   // 表达式语言: "aviator" (默认) 或 "spel"

    @Override
    public String getType() {
        return NodeType.IF;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        // If 节点有两个输出端口: true 和 false
        return Arrays.asList(
                PortDefinition.output(PortNames.TRUE),
                PortDefinition.output(PortNames.FALSE)
        );
    }
}
