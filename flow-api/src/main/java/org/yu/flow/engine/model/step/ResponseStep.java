package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.NodeType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Response HTTP 响应终态节点
 *
 * 用于替代或增强 End 节点，控制 API 响应的 Status Code、Headers 和 Body。
 *
 * 配置:
 *   status: HTTP 状态码 (支持表达式，默认 200)
 *   headers: 响应头 Map
 *   body: 响应体数据来源 (支持 ${} 变量引用或直接对象)
 *
 * 这是流程的终止节点，返回 null 表示无下一步。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ResponseStep extends Step {

    private Object status = 200;
    private Map<String, String> headers;
    private Object body;

    @Override
    public String getType() {
        return NodeType.RESPONSE;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Collections.emptyList(); // 终止节点无输出端口
    }
}
