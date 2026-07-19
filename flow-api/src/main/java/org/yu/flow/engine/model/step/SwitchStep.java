package org.yu.flow.engine.model.step;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Switch 多路值匹配。
 * <p>每条分支出口为 {@code case_<id>}，未命中走 {@code default}。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SwitchStep extends Step {
    private String expression;
    @JsonDeserialize(using = SwitchCasesDeserializer.class)
    private List<SwitchCase> cases = new ArrayList<>();
    private String language;

    @Override
    public String getType() {
        return NodeType.SWITCH;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        List<PortDefinition> ports = new ArrayList<>();
        if (cases != null) {
            for (SwitchCase c : cases) {
                if (c == null || c.getId() == null || c.getId().isBlank()) {
                    continue;
                }
                ports.add(PortDefinition.output("case_" + c.getId()));
            }
        }
        ports.add(PortDefinition.output(PortNames.DEFAULT));
        return ports;
    }
}
