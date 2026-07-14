package org.yu.flow.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据 API contract.request 中的 SchemaNode 定义，将 HTTP 字符串参数转换为强类型值。
 */
@Service
public class ContractParamTypeConverter {

    private static final int MAX_CACHE_SIZE = 1000;

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();
    private final Map<String, JsonNode> contractCache = new ConcurrentHashMap<>();

    /**
     * 转换 contract.request 下指定区域（query、pathParams、headers、body）的参数。
     */
    public Map<String, Object> convertSection(String contractJson, String section,
                                              Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) {
            source.forEach(result::put);
        }
        if (StrUtil.isBlank(contractJson) || result.isEmpty()) {
            return result;
        }

        JsonNode nodes = getContract(contractJson).path("request").path(section);
        if (!nodes.isArray()) {
            return result;
        }

        for (JsonNode node : nodes) {
            String name = node.path("name").asText("");
            if (!name.isEmpty() && result.containsKey(name)) {
                result.put(name, convertValue(result.get(name), node, section + "." + name));
            }
        }
        return result;
    }

    private JsonNode getContract(String contractJson) {
        if (contractCache.size() >= MAX_CACHE_SIZE) {
            contractCache.clear();
        }
        return contractCache.computeIfAbsent(contractJson, value -> {
            try {
                return objectMapper.readTree(value);
            } catch (Exception e) {
                throw new SchemaValidationException("API 契约 JSON 格式不正确: " + e.getMessage());
            }
        });
    }

    private Object convertValue(Object value, JsonNode schema, String path) {
        String type = schema.path("type").asText("string");
        if (value == null) {
            return null;
        }

        try {
            switch (type) {
                case "integer":
                    return toInteger(value);
                case "number":
                    return toNumber(value);
                case "boolean":
                    return toBoolean(value);
                case "null":
                    return toNull(value);
                case "object":
                    return toObject(value, schema.path("children"), path);
                case "array":
                    return toArray(value, schema.path("children"), path);
                case "string":
                default:
                    return toFormattedString(value, schema.path("format").asText(""));
            }
        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            String displayName = schema.path("description").asText("");
            String field = displayName.isEmpty() ? path : displayName + "（" + path + "）";
            throw new SchemaValidationException(
                    "参数 " + field + " 无法转换为 " + describeType(type, schema) + ": " + value);
        }
    }

    private Object toInteger(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger) {
            return value;
        }
        BigInteger integer = new BigInteger(value.toString().trim());
        if (integer.bitLength() <= 63) {
            return integer.longValue();
        }
        return integer;
    }

    private Object toNumber(Object value) {
        if (value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(value.toString().trim());
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = value.toString().trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("boolean 仅接受 true 或 false");
    }

    private Object toNull(Object value) {
        if ("null".equalsIgnoreCase(value.toString().trim())) {
            return null;
        }
        throw new IllegalArgumentException("null 类型仅接受 null");
    }

    private Object toFormattedString(Object value, String format) {
        if (!(value instanceof String)) {
            return value;
        }
        String text = ((String) value).trim();
        switch (format) {
            case "date":
                return Date.valueOf(LocalDate.parse(text));
            case "time":
                return Time.valueOf(LocalTime.parse(text));
            case "date-time":
                return parseDateTime(text);
            case "uuid":
                return UUID.fromString(text);
            default:
                return value;
        }
    }

    private Timestamp parseDateTime(String text) {
        String normalized = text.replace(' ', 'T');
        try {
            return Timestamp.from(Instant.parse(normalized));
        } catch (DateTimeParseException ignored) {
            try {
                return Timestamp.from(OffsetDateTime.parse(normalized).toInstant());
            } catch (DateTimeParseException ignoredOffset) {
                return Timestamp.valueOf(LocalDateTime.parse(normalized));
            }
        }
    }

    private Map<String, Object> toObject(Object value, JsonNode children, String path) {
        Map<String, Object> source;
        if (value instanceof Map) {
            source = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> source.put(String.valueOf(key), item));
        } else if (value instanceof String) {
            try {
                source = objectMapper.readValue((String) value, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("object 必须是 JSON 对象", e);
            }
        } else {
            throw new IllegalArgumentException("object 类型不匹配");
        }

        if (children.isArray()) {
            for (JsonNode child : children) {
                String name = child.path("name").asText("");
                if (!name.isEmpty() && source.containsKey(name)) {
                    source.put(name, convertValue(source.get(name), child, path + "." + name));
                }
            }
        }
        return source;
    }

    private List<Object> toArray(Object value, JsonNode children, String path) {
        List<Object> source = new ArrayList<>();
        if (value instanceof Collection) {
            source.addAll((Collection<?>) value);
        } else if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                source.add(java.lang.reflect.Array.get(value, i));
            }
        } else if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.startsWith("[")) {
                try {
                    source.addAll(objectMapper.readValue(text, new TypeReference<List<Object>>() {}));
                } catch (Exception e) {
                    throw new IllegalArgumentException("array 必须是 JSON 数组或逗号分隔值", e);
                }
            } else if (!text.isEmpty()) {
                for (String item : text.split(",")) {
                    source.add(item.trim());
                }
            }
        } else {
            throw new IllegalArgumentException("array 类型不匹配");
        }

        if (children.isArray() && !children.isEmpty()) {
            JsonNode itemSchema = children.get(0);
            for (int i = 0; i < source.size(); i++) {
                Object item = source.get(i);
                if (item instanceof Map) {
                    source.set(i, toObject(item, children, path + "[" + i + "]"));
                } else {
                    source.set(i, convertValue(item, itemSchema, path + "[" + i + "]"));
                }
            }
        }
        return source;
    }

    private String describeType(String type, JsonNode schema) {
        String format = schema.path("format").asText("");
        return format.isEmpty() ? type : type + "(" + format + ")";
    }
}
