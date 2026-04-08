package org.yu.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.*;

public class JsonDeserializerWithDefaults {

    private static final ObjectMapper mapper = new ObjectMapper();

    // 修改后的正则表达式，使用非命名捕获组
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");

    // 用于预处理未加引号占位符的正则表达式
    private static final Pattern UNQUOTED_PLACEHOLDER_PATTERN =
            Pattern.compile("(?<!\")\\$\\{([^}]+)\\}(?!\")");

    public static String deserialize(String jsonStr, Map<String, Object> variables) {
        try {
            // 1. 预处理JSON，确保所有占位符都有引号
            String preprocessed = preprocessJson(jsonStr);

            // 2. 解析为JSON树
            JsonNode rootNode = mapper.readTree(preprocessed);

            // 3. 处理节点并替换占位符
            rootNode = processNode(rootNode, variables);

            // 4. 转换回JSON字符串
            return mapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * 预处理JSON字符串，为未加引号的占位符添加引号
     */
    private static String preprocessJson(String jsonStr) {
        if (jsonStr == null) {
            return null;
        }

        // 使用StringBuilder代替StringBuffer，因为不需要线程安全
        StringBuilder sb = new StringBuilder();
        Matcher matcher = UNQUOTED_PLACEHOLDER_PATTERN.matcher(jsonStr);

        int lastEnd = 0;
        while (matcher.find()) {
            // 添加非匹配部分
            sb.append(jsonStr.substring(lastEnd, matcher.start()));
            // 添加带引号的占位符
            sb.append('"').append(matcher.group()).append('"');
            lastEnd = matcher.end();
        }
        // 添加剩余部分
        sb.append(jsonStr.substring(lastEnd));

        return sb.toString();
    }

    /**
     * 递归处理JSON节点
     */
    private static JsonNode processNode(JsonNode node, Map<String, Object> variables) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode processed = processNode(entry.getValue(), variables);
                objectNode.set(entry.getKey(), processed);
            }
            return objectNode;

        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            ArrayNode newArray = mapper.createArrayNode();

            for (JsonNode element : arrayNode) {
                newArray.add(processNode(element, variables));
            }
            return newArray;

        } else if (node.isValueNode()) {
            String strValue = node.asText();
            if (strValue != null) {
                // 检查是否是纯占位符（没有其他文本）
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(strValue);
                if (matcher.matches()) {
                    return resolvePlaceholder(strValue, variables);
                }
                // 处理可能包含占位符的混合文本
                return new TextNode(replacePlaceholdersInText(strValue, variables));
            }
        }
        return node;
    }

    /**
     * 解析纯占位符（如 "${user.address}"）
     */
    private static JsonNode resolvePlaceholder(String placeholder, Map<String, Object> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(placeholder);
        if (matcher.matches()) {
            String key = matcher.group(1).trim();
            String defaultValue = matcher.groupCount() > 1 ? matcher.group(2) : null;

            Object value = getValueFromMap(key, variables);

            if (value == null && defaultValue != null) {
                return new TextNode(defaultValue);
            }

            if (value != null) {
                try {
                    // 如果是Map或Collection，转换为JsonNode
                    if (value instanceof Map || value instanceof Collection) {
                        return mapper.valueToTree(value);
                    }
                    // 其他类型转换为字符串
                    return new TextNode(value.toString());
                } catch (Exception e) {
                    return new TextNode(value.toString());
                }
            }
        }
        // 无法解析，保留原占位符
        return new TextNode(placeholder);
    }

    /**
     * 替换文本中的占位符（用于混合文本）
     */
    private static String replacePlaceholdersInText(String input, Map<String, Object> variables) {
        if (input == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);

        int lastEnd = 0;
        while (matcher.find()) {
            // 添加非匹配部分
            result.append(input.substring(lastEnd, matcher.start()));

            // 处理占位符
            String key = matcher.group(1).trim();
            String defaultValue = matcher.groupCount() > 1 ? matcher.group(2) : null;
            Object value = getValueFromMap(key, variables);

            // 决定替换内容
            String replacement;
            if (value != null) {
                replacement = value.toString();
            } else if (defaultValue != null) {
                replacement = defaultValue;
            } else {
                replacement = matcher.group();
            }

            result.append(replacement);
            lastEnd = matcher.end();
        }
        // 添加剩余部分
        result.append(input.substring(lastEnd));

        return result.toString();
    }

    /**
     * 从Map中获取多级变量的值
     */
    private static Object getValueFromMap(String key, Map<String, Object> map) {
        if (key == null || map == null) {
            return null;
        }

        String[] keys = key.split("\\.");
        Object value = map;

        try {
            for (String k : keys) {
                if (value instanceof Map) {
                    value = ((Map<?, ?>) value).get(k);
                    if (value == null) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        // 测试用例
        String json = "{\"userObj\":\"${user}\", \"userStr\":\"User: ${user.name}\", " +
                "\"address\":${user.address}, \"age\":\"${user.age:18}\", " +
                "\"missing\":\"${nonexistent.key:Not Found}\"}";

        Map<String, Object> address = new HashMap<>();
        address.put("city", "北京");
        address.put("street", "长安街");

        Map<String, Object> user = new HashMap<>();
        user.put("name", "张三");
        // age 故意不设置，测试默认值
        user.put("address", address);

        Map<String, Object> variables = new HashMap<>();
        variables.put("user", user);

        String result = deserialize(json, variables);
        System.out.println(result);



        /* 预期输出:
        {
          "userObj": {
            "name": "张三",
            "address": {
              "city": "北京",
              "street": "长安街"
            }
          },
          "userStr": "User: 张三",
          "address": {
            "city": "北京",
            "street": "长安街"
          },
          "age": "18",
          "missing": "Not Found"
        }
        */
    }
}
