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
 * 局部 try/catch 错误边界：执行 try 子流，异常时走 catch 口而不向上抛。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TryCatchStep extends Step {

    @Override
    public String getType() {
        return NodeType.TRY_CATCH;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(
                PortDefinition.output(PortNames.TRY),
                PortDefinition.output(PortNames.CATCH),
                PortDefinition.output(PortNames.OUT)
        );
    }
}
