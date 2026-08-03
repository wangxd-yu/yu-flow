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
 * 声明式 JSON 字段映射：从 payload / 上下文路径提取字段写入目标对象。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class JsonMapStep extends Step {

    private List<MapField> mappings;

    @Data
    public static class MapField {
        /** 输出对象字段名 */
        private String target;
        /** 源路径（相对 payload 或绝对 $.node.out） */
        private String source;
    }

    @Override
    public String getType() {
        return NodeType.JSON_MAP;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
