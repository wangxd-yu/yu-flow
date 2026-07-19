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
 * 统一错误处理入口：不接入主流程控制流；引擎捕获异常后跳转到本节点，
 * 再从其 out 继续补偿 / 响应。画布上建议单例。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ErrorHandlerStep extends Step {

    @Override
    public String getType() {
        return NodeType.ERROR_HANDLER;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
