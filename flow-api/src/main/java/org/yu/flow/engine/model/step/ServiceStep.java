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
 * Service 内部服务编排入口节点。
 *
 * <p>作为服务流程的起点，执行时将调用元信息与入参注入 {@code $.service.*}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ServiceStep extends Step {

    /** 服务名称（调用方透传，供下游引用） */
    private String serviceName;

    @Override
    public String getType() {
        return NodeType.SERVICE;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
