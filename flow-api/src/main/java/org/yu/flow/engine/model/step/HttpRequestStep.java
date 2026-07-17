package org.yu.flow.engine.model.step;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Arrays;
import java.util.LinkedHashMap;
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
 *   ignoreSsl: 是否忽略 SSL 证书校验（自签名 HTTPS）
 *
 * 输出 Map: { "status": 200, "body": {...}, "headers": {...} }
 * 失败时: { "status": -1, "error": "...", "timeMs": n }，出口为 fail
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

    /**
     * 网络异常 / 超时时的额外重试次数（不含首次）。0 = 不重试。
     * HTTP 业务失败（走 fail 口）不重试。
     */
    private Integer retryCount;

    /** 两次尝试之间的间隔（毫秒） */
    private Integer retryIntervalMs;

    /**
     * 成功条件（Aviator 表达式）。为空时回退 HTTP 2xx。
     * 求值上下文为响应结果：status / body / headers / timeMs
     * 示例：status == 200、status == 200 && body.code == 0
     */
    private String successCondition;

    /**
     * 是否记录三方调用日志。null 视为 true（默认开启）。
     */
    private Boolean logEnabled;

    /**
     * 接口标识（写入三方日志 apiType）；为空时回退节点 ID。
     */
    private String apiType;

    /**
     * 是否忽略 SSL 证书校验（自签名 / 内网 HTTPS）。
     * <ul>
     *   <li>null（未配置）→ 执行器按 true 处理，便于内网自签名</li>
     *   <li>true → 信任全部证书并跳过主机名校验</li>
     *   <li>false → 严格校验证书（公网正式环境请显式关闭）</li>
     * </ul>
     */
    private Boolean ignoreSsl;

    /**
     * 兼容前端历史脏数据：headers/params 可能是 [{key,value}] 数组或 Map。
     */
    @JsonSetter("headers")
    public void setHeaders(Object value) {
        this.headers = toStringMap(value);
    }

    @JsonSetter("params")
    public void setParams(Object value) {
        this.params = toStringMap(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> toStringMap(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map) {
            ((Map<?, ?>) value).forEach((k, v) -> {
                if (k != null) {
                    result.put(String.valueOf(k), v == null ? "" : String.valueOf(v));
                }
            });
            return result;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> kv = (Map<String, Object>) item;
                Object key = kv.get("key");
                if (key == null || String.valueOf(key).isEmpty()) {
                    continue;
                }
                Object v = kv.get("value");
                result.put(String.valueOf(key), v == null ? "" : String.valueOf(v));
            }
            return result;
        }
        return result;
    }

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
