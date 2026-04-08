package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * SWITCH 分支 (多路匹配)
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SwitchStep extends Step {
    private String expression; // 计算值的表达式，如 r (变量名)
    private List<String> cases = new ArrayList<>(); // 匹配项列表，如 ["ADMIN", "USER", "GUEST"]
    private String language;   // 表达式语言: "aviator" (默认) 或 "spel"

    @Override
    public String getType() {
        return NodeType.SWITCH;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        // Switch 节点的输出端口根据 cases 动态生成
        List<PortDefinition> ports = new ArrayList<>();

        if (cases != null) {
            for (String caseValue : cases) {
                ports.add(PortDefinition.output("case_" + caseValue));
            }
        }

        // 默认分支
        ports.add(PortDefinition.output(PortNames.DEFAULT));

        return ports;
    }
}
