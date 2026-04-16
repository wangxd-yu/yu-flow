package org.yu.flow.config.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 响应数据转换引擎 (利用 JSONPath)
 */
@Slf4j
@Component
public class ResponseTransformer {

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();

    // JsonPath 配置对象，避免使用 Configuration.setDefaults 修改全局静态状态
    private final Configuration jsonPathConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(java.util.EnumSet.of(Option.SUPPRESS_EXCEPTIONS))
            .build();

    // 模板缓存：避免每次请求都去解析 JSON 模板字符串
    private final Map<String, Map<String, Object>> templateMapCache = new java.util.concurrent.ConcurrentHashMap<>(64);

    /**
     * 将 rawResult 根据 wrapperTemplateJson 的结构进行动态转换
     *
     * @param rawResult 业务实际返回的数据 (可能是一个 Bean，Map，Page 等)
     * @param wrapperTemplateJson 包装模板JSON字符串
     * @return 转换后的数据对象 (通常是一个 Map，可以直接序列化给前端)
     */
    public Object transform(Object rawResult, String wrapperTemplateJson) {
        if (wrapperTemplateJson == null || wrapperTemplateJson.trim().isEmpty()) {
            return rawResult; // 如果没有模板，就不包装
        }

        try {
            // 从缓存中获取解析过的模板 Map，提高转换效率
            Map<String, Object> templateMap = templateMapCache.computeIfAbsent(wrapperTemplateJson, key -> {
                try {
                    return objectMapper.readValue(key, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    throw new RuntimeException("Parse wrapper template error", e);
                }
            });
            
            // 将原始结果转换为 Jackson 原生支持的 Map 或 List 等结构
            // 这比将其序列化为 String 再重新由 JsonPath 解析要高效得多
            Object rawData = objectMapper.convertValue(rawResult, Object.class);
            DocumentContext documentContext = JsonPath.using(jsonPathConfig).parse(rawData);

            // 深度遍历并取值
            return traverseAndReplace(templateMap, documentContext);
        } catch (Exception e) {
            log.error("根据模板转换响应结果失败: template={}, error={}", wrapperTemplateJson, e.getMessage(), e);
            // 失败的话兜底返回原始结果（或根据系统要求抛出异常）
            return rawResult;
        }
    }

    /**
     * 深度遍历 Map，替换所有的 $ 或 $.xxx 表达式
     */
    @SuppressWarnings("unchecked")
    private Object traverseAndReplace(Object node, DocumentContext rawPathCtx) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            Map<String, Object> newMap = new HashMap<>(map.size());
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                newMap.put(entry.getKey(), traverseAndReplace(entry.getValue(), rawPathCtx));
            }
            return newMap;
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            List<Object> newList = new ArrayList<>(list.size());
            for (Object item : list) {
                newList.add(traverseAndReplace(item, rawPathCtx));
            }
            return newList;
        } else if (node instanceof String) {
            String strNode = (String) node;
            // 判断是否是 JSONPath 表达式
            if ("$".equals(strNode)) {
                return rawPathCtx.read("$");
            } else if (strNode.startsWith("$.")) {
                return rawPathCtx.read(strNode);
            } else {
                return strNode;
            }
        } else {
            // 其他基础类型直出 (Number, Boolean, null 等)
            return node;
        }
    }

    /**
     * 清空模板缓存（通常在 ResponseTemplate 重载时触发）
     */
    public void clearCache() {
        templateMapCache.clear();
        log.info("[ResponseTransformer] 响应模板解析缓存已清空。");
    }
}
