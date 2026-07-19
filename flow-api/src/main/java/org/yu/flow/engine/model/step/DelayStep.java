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
 * 延迟 / 等待节点：阻塞当前线程指定毫秒后继续。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DelayStep extends Step {
    /** 延迟毫秒数；也可通过 inputs.delayMs 动态覆盖 */
    private Long delayMs = 1000L;

    @Override
    public String getType() {
        return NodeType.DELAY;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
