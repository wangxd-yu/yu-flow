package org.yu.flow.module.api.runtime;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 静态内容接口：JSON 按 JSON 解析；STRING 是合法 JSON 时解析，否则原样返回。
 */
@Component
public class StaticApiTypeExecutor implements ApiTypeExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Set<String> serviceTypes() {
        return Set.of("JSON", "STRING", "EXCEL");
    }

    @Override
    public Object execute(Invocation invocation) {
        String content = invocation.content();
        return switch (invocation.api().getServiceType()) {
            case "JSON" -> parseJson(content);
            case "STRING" -> parseJsonOrRaw(content);
            default -> Collections.singletonMap("error", "EXCEL 导出暂未实现！");
        };
    }

    private static Object parseJson(String content) {
        try {
            return OBJECT_MAPPER.readValue(content, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON parsing error", e);
        }
    }

    private static Object parseJsonOrRaw(String content) {
        if (JSONUtil.isTypeJSON(content)) {
            try {
                return OBJECT_MAPPER.readValue(content, Object.class);
            } catch (Exception e) {
                return content;
            }
        }
        return content;
    }
}
