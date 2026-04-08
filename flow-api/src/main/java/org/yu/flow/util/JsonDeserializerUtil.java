package org.yu.flow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonDeserializerUtil {

    private static final ObjectMapper mapper = FlowObjectMapperUtil.flowObjectMapper();

    // 优化后的正则表达式，匹配 ${key} 或 ${key:default}
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");

    /**
     * 反序列化JSON字符串并替换占位符
     */
    public static String deserialize(String jsonStr, Map<String, Object> variables) {
        try {
            JsonNode rootNode = mapper.readTree(jsonStr);
            JsonNode processedNode = processNode(rootNode, variables);
            return mapper.writeValueAsString(processedNode);
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * 反序列化JSON字符串并替换占位符
     */
    public static JsonNode deserializeToJsonNode(String jsonStr, Map<String, Object> variables) {
        try {
            JsonNode rootNode = mapper.readTree(jsonStr);
            JsonNode processedNode = processNode(rootNode, variables);
            return processedNode;
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * 递归处理JSON节点
     */
    private static JsonNode processNode(JsonNode node, Map<String, Object> variables) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            ObjectNode resultNode = mapper.createObjectNode();

            objectNode.fields().forEachRemaining(entry -> {
                JsonNode processed = processNode(entry.getValue(), variables);
                resultNode.set(entry.getKey(), processed);
            });
            return resultNode;

        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            ArrayNode resultNode = mapper.createArrayNode();

            arrayNode.forEach(element -> resultNode.add(processNode(element, variables)));
            return resultNode;

        } else if (node.isTextual()) {
            String text = node.textValue();
            return replacePlaceholders(text, variables);
        }

        return node;
    }

    /**
     * 替换文本中的占位符
     */
    private static JsonNode replacePlaceholders(String text, Map<String, Object> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);

        // 如果是纯占位符(没有其他文本)
        if (matcher.matches() && text.equals(matcher.group())) {
            return resolvePlaceholder(text, variables);
        }

        // 处理混合文本中的占位符
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String defaultValue = matcher.groupCount() > 1 ? matcher.group(2) : null;

            Object value = getNestedValue(variables, key);
            String replacement = value != null ? value.toString() :
                    (defaultValue != null ? defaultValue : matcher.group());
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return new TextNode(sb.toString());
    }

    /**
     * 解析纯占位符
     */
    private static JsonNode resolvePlaceholder(String placeholder, Map<String, Object> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(placeholder);
        if (matcher.find()) {
            String key = matcher.group(1).trim();
            String defaultValue = matcher.groupCount() > 1 ? matcher.group(2) : null;

            Object value = getNestedValue(variables, key);
            if (value != null) {
                try {
                    // 尝试将值转换为JsonNode
                    return mapper.valueToTree(value);
                } catch (Exception e) {
                    return new TextNode(value.toString());
                }
            }
            return defaultValue != null ? new TextNode(defaultValue) : new TextNode(placeholder);
        }
        return new TextNode(placeholder);
    }

    /**
     * 从Map中获取嵌套值
     */
    @SuppressWarnings("unchecked")
    private static Object getNestedValue(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;

        String[] keys = key.split("\\.");
        Object current = map;

        for (String k : keys) {
            if (!(current instanceof Map)) return null;

            current = ((Map<String, Object>) current).get(k);
            if (current == null) return null;
        }

        return current;
    }

    public static void main(String[] args) {
        String json = "{\"userObj\":\"${user}\", \"userStr\":\"User: ${user.name}\", "
                + "\"address\":${user.address}, \"age\":\"${user.age:18}\", "
                + "\"missing\":\"${nonexistent.key:Not Found}\"}";

        ObjectNode address = mapper.createObjectNode();
        address.put("city", "北京");
        address.put("street", "长安街");

        ObjectNode user = mapper.createObjectNode();
        user.put("name", "张三");
        user.set("address", address);

        ObjectNode variables = mapper.createObjectNode();
        variables.set("user", user);

        String result = deserialize(json, (Map)variables);
        // [DEBUG] removed println
    }
}
