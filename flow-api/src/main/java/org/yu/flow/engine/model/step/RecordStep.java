package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Record 数据构造节点 (Object Builder)
 *
 * 将散落在不同节点的数据拼装成一个新的 Map/JSON 对象。
 * 通常用于构造 httpRequest 的 Body 或 response 的 Body。
 *
 * 配置:
 *   schema: (Map) 定义输出对象的 Key 和 Value 的来源表达式
 *     例如: { "userName": "$.start.name", "meta": "$.calc.result" }
 *     支持 JsonPath 和 ${} 变量引用
 *
 * 输出: 构造好的 Map，存储在 {nodeId}.result
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RecordStep extends Step {

    /**
     * 输出对象的 schema 配置
     * key: 输出字段名
     * value: 数据来源表达式 (JsonPath 或 ${var})
     */
    private Map<String, Object> schema;

    @Override
    public String getType() {
        return NodeType.RECORD;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
