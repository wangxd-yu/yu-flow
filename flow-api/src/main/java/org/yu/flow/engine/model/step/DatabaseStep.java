package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Collections;
import java.util.List;

/**
 * Database 节点
 * 用于执行 SQL 操作
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DatabaseStep extends Step {
    private String datasourceId;
    private String sqlType; // SELECT, INSERT, UPDATE, DELETE
    private String returnType; // LIST, OBJECT, PAGE (Only for SELECT)
    private String sql;

    @Override
    public String getType() {
        return NodeType.DATABASE;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        // 默认一个输出端口 "out"
        return Collections.singletonList(PortDefinition.output(PortNames.OUT));
    }

    // 可以添加 validateCustom 校验必填项
}
