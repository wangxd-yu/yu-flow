package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.NodeType;

import java.util.Collections;
import java.util.List;

/**
 * End 终点节点
 * 流程的终点，设置输出结果
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class EndStep extends Step {
    private String responseBody; // 响应体，支持 ${nodeId.result} 模板

    @Override
    public String getType() {
        return NodeType.END;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Collections.emptyList(); // End 节点没有输出端口
    }
}
