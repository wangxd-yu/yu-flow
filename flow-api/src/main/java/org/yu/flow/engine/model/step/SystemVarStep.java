package org.yu.flow.engine.model.step;

import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 系统变量获取节点 (System Variable Node)
 * 该节点作为数据源使用，执行预先配置好的 SpEL 表达式，将获取到的系统级数据（如雪花算法ID、当前用户信息、YML配置项等）
 * 作为结果向流的下游传递。
 *
 * @author yu-flow
 */
public class SystemVarStep extends Step {

    /**
     * 系统变量存入上下文中的变量标识
     */
    private String variableCode;

    /**
     * 是否为易变变量：默认 false 走缓存；true 则不走缓存每次重新执行 SpEL
     */
    private boolean volatileVar = false;

    @Override
    public String getType() {
        return NodeType.SYSTEM_VAR;
    }

    @Override
    public List<PortDefinition> getInputPorts() {
        // 作为数据源，此节点无输入端口
        return new ArrayList<>();
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        // 固定输出端口，名称为 out
        return Collections.singletonList(PortDefinition.output(PortNames.OUT));
    }

    public String getVariableCode() {
        return variableCode;
    }

    public void setVariableCode(String variableCode) {
        this.variableCode = variableCode;
    }

    public boolean isVolatileVar() {
        return volatileVar;
    }

    public void setVolatileVar(boolean volatileVar) {
        this.volatileVar = volatileVar;
    }
}
