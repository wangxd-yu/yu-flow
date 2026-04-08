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
 * CollectStep - 并发汇聚屏障节点 (Scatter-Gather 模式之 Gather 端)
 *
 * 每条从 ForStep 发出的并发分支都会流经此节点。
 * 非最后一条线程到达时结束；最后一条线程到达时，聚合所有分支结果为 List
 * 并继续执行下游节点。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CollectStep extends Step {

    /**
     * 超时时间（毫秒），默认 30 秒。
     * 超时后主线程强制唤醒，收集已到达的结果并继续（携带 timeout 标记）。
     */
    private long timeoutMs = 30_000L;

    @Override
    public String getType() {
        return NodeType.COLLECT;
    }

    @Override
    public List<PortDefinition> getInputPorts() {
        return Arrays.asList(
                PortDefinition.input("item", "any", true)
        );
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(
                PortDefinition.output(PortNames.LIST),    // 数据流：最后一条线程触发，携带聚合 List
                PortDefinition.output(PortNames.FINISH)   // 控制流：收集完成信号
        );
    }
}
