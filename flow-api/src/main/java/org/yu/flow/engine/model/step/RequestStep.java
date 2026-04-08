package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.validation.ValidationRule;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Request 请求节点
 * 参照 Postman Flow 的 Request 组件，作为 HTTP 请求入口
 *
 * 将请求数据拆分为三个输出端口:
 * - headers: 请求头 (Authorization, Content-Type 等)
 * - params: 查询参数 / URL参数
 * - body: 请求体
 *
 * 支持参数校验 (复用 ValidationRule)
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RequestStep extends Step {

    /**
     * 参数校验规则 (复用 Start 节点的 ValidationRule)
     * key: 参数名 (可以是 params 或 body 中的字段名)
     * value: 校验规则
     */
    private Map<String, ValidationRule> validations;

    @Override
    public String getType() {
        return NodeType.REQUEST;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(
                PortDefinition.output(PortNames.HEADERS),
                PortDefinition.output(PortNames.PARAMS),
                PortDefinition.output(PortNames.BODY)
        );
    }
}
