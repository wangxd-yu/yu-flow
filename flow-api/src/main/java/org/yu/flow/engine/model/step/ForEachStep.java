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
 * 串行循环：按顺序对列表每一项执行 item 下游子流，全部完成后走 done。
 *
 * <p>与 {@link ForStep}（并发 Scatter）不同：本节点同步迭代，适合限流、
 * 依赖上一轮结果、或必须按序处理的场景。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ForEachStep extends Step {

    @Override
    public String getType() {
        return NodeType.FOR_EACH;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(
                PortDefinition.output(PortNames.ITEM),
                PortDefinition.output(PortNames.DONE)
        );
    }
}
