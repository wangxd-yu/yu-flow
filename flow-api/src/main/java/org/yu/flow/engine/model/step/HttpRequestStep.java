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
 * HttpRequest 节点
 * 发起真实的 HTTP 网络请求
 *
 * 配置:
 *   url: 请求地址 (支持 ${} 变量替换)
 *   method: GET, POST, PUT, DELETE
 *   headers: 请求头
 *   body: 请求体
 *   timeout: 超时时间 (毫秒)
 *
 * 输出 Map: { "status": 200, "body": {...}, "headers": {...} }
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class HttpRequestStep extends Step {

    private String url;
    private String method = "GET";
    private Map<String, String> headers;
    private Map<String, String> params; // Query Params
    private Object body;
    private int timeout = 30000; // 默认 30 秒

    @Override
    public String getType() {
        return NodeType.HTTP_REQUEST;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(
            PortDefinition.output(PortNames.SUCCESS),
            PortDefinition.output(PortNames.FAIL)
        );
    }
}
