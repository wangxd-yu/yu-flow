package org.yu.flow.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JSON Schema 入参前置校验服务
 *
 * <p>基于 networknt/json-schema-validator 高性能校验库，将前端定义的 API 契约
 * （存储于 FlowApiDO.contract 字段）解析后，分别对 Body 和 Query 参数进行 JSON Schema 校验。
 * 校验不通过时抛出携带中文友好提示的 {@link SchemaValidationException}。</p>
 *
 * <p>contract 字段存储的是完整的契约 JSON，结构如下：</p>
 * <pre>
 * {
 *   "request": {
 *     "query": [...SchemaNode[]],
 *     "headers": [...SchemaNode[]],
 *     "body": [...SchemaNode[]],
 *     "bodyType": "json"
 *   },
 *   "responses": { ... }
 * }
 * </pre>
 *
 * <p>线程安全：内部 ObjectMapper 和 JsonSchemaFactory 均为无状态/线程安全实例。</p>
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class SchemaValidatorService {

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();

    /**
     * 使用 Draft 7 作为默认规范（兼容性最好，前端 treeToSchema 输出即为此规范）
     */
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    /**
     * 契约编译产物包装类
     */
    private static class CompiledContract {
        final JsonSchema bodySchema;
        final JsonSchema querySchema;

        CompiledContract(JsonSchema bodySchema, JsonSchema querySchema) {
            this.bodySchema = bodySchema;
            this.querySchema = querySchema;
        }
    }

    /**
     * 本地缓存：将 contract 内容作为 Key，避免每次请求都重新解析并编译 Schema
     * 设最大容量为 1000 以防止极端修改操作导致的内存泄露
     */
    private final Map<String, CompiledContract> schemaCache = new ConcurrentHashMap<>();

    private CompiledContract getOrCompileSchemaSafely(String contractJson) {
        if (schemaCache.size() > 1000) {
            schemaCache.clear();
        }
        return schemaCache.computeIfAbsent(contractJson, key -> {
            try {
                JsonNode contract = objectMapper.readTree(key);
                JsonNode requestNode = contract.path("request");
                if (requestNode.isMissingNode()) {
                    return new CompiledContract(null, null);
                }

                JsonNode bodyNodesArray = requestNode.path("body");
                String bodyType = requestNode.path("bodyType").asText("none");
                JsonSchema bodySchema = null;
                if ("json".equals(bodyType) && bodyNodesArray.isArray() && bodyNodesArray.size() > 0) {
                    JsonNode nodeSchema = schemaNodesTreeToJsonSchema(bodyNodesArray);
                    if (nodeSchema != null) {
                        bodySchema = schemaFactory.getSchema(nodeSchema);
                    }
                }

                JsonNode queryNodesArray = requestNode.path("query");
                JsonSchema querySchema = null;
                if (queryNodesArray.isArray() && queryNodesArray.size() > 0) {
                    JsonNode nodeSchema = schemaNodesFlatToJsonSchema(queryNodesArray);
                    if (nodeSchema != null) {
                        querySchema = schemaFactory.getSchema(nodeSchema);
                    }
                }

                return new CompiledContract(bodySchema, querySchema);
            } catch (Exception e) {
                log.error("[SchemaValidator] 异步/缓存节点编译异常", e);
                // 抛出运行时异常，打破计算，让外层捕获
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 从完整契约 JSON 中提取 body 和 query 的校验规则，分别进行校验。
     *
     * @param contractJson 前端存储到 rule 字段的完整契约 JSON 字符串
     * @param bodyParams   已解析的请求体参数 Map（POST/PUT body）
     * @param queryParams  已解析的查询参数 Map（URL query string）
     * @throws SchemaValidationException 校验失败时，携带所有校验错误的中文友好提示
     */
    public void validateFromContract(String contractJson,
                                     Map<String, Object> bodyParams,
                                     Map<String, String> queryParams) {
        if (StrUtil.isBlank(contractJson)) {
            return;
        }

        try {
            CompiledContract compiled = getOrCompileSchemaSafely(contractJson);
            List<String> allErrors = new ArrayList<>();

            // ─── 顺序同步校验 Body ───
            if (compiled.bodySchema != null) {
                JsonNode bodyData = bodyParams != null ? objectMapper.valueToTree(bodyParams) : objectMapper.createObjectNode();
                allErrors.addAll(doValidate(compiled.bodySchema, bodyData));
            }

            // ─── 顺序同步校验 Query ───
            if (compiled.querySchema != null) {
                JsonNode queryData = queryParams != null ? objectMapper.valueToTree(queryParams) : objectMapper.createObjectNode();
                allErrors.addAll(doValidate(compiled.querySchema, queryData));
            }

            // 抛出统一切面异常
            if (!allErrors.isEmpty()) {
                String combined = String.join("；", allErrors);
                log.warn("[SchemaValidator] 入参校验失败: {}", combined);
                throw new SchemaValidationException(combined, allErrors);
            }

        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SchemaValidator] 契约解析或校验过程异常", e);
            // 这里兼容处理抛出的 IllegalArgumentException 或包装的 RuntimeException
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (e.getCause() != null) {
                msg = e.getCause().getMessage();
            }
            throw new SchemaValidationException("请求参数格式不正确: " + msg);
        }
    }

    /**
     * 执行 JSON Schema 校验并返回中文错误消息列表
     */
    private List<String> doValidate(JsonSchema schema, JsonNode dataNode) {
        Set<ValidationMessage> errors = schema.validate(dataNode);
        if (errors == null || errors.isEmpty()) {
            return Collections.emptyList();
        }
        return errors.stream()
                .map(this::toChineseMessage)
                .collect(Collectors.toList());
    }

    // ============================= SchemaNode → JSON Schema 转换 =============================

    /**
     * 将前端的树形 SchemaNode 数组转为标准 JSON Schema。
     * 用于 Body（json 类型），SchemaNode 可以有嵌套 children。
     *
     * <p>前端 SchemaNode 结构:
     * { id, name, description, type, required, children?, pattern, minLength, maxLength, ... }</p>
     */
    private JsonNode schemaNodesTreeToJsonSchema(JsonNode nodesArray) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode requiredArr = objectMapper.createArrayNode();

        for (JsonNode node : nodesArray) {
            String fieldName = node.path("name").asText("");
            if (fieldName.isEmpty()) continue;

            ObjectNode prop = buildPropertyFromNode(node);
            properties.set(fieldName, prop);

            if (node.path("required").asBoolean(false)) {
                requiredArr.add(fieldName);
            }
        }

        schema.set("properties", properties);
        if (requiredArr.size() > 0) {
            schema.set("required", requiredArr);
        }
        return schema;
    }

    /**
     * 将前端的扁平 SchemaNode 数组转为标准 JSON Schema。
     * 用于 Query Params / Headers（无嵌套）。
     */
    private JsonNode schemaNodesFlatToJsonSchema(JsonNode nodesArray) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode requiredArr = objectMapper.createArrayNode();

        for (JsonNode node : nodesArray) {
            String fieldName = node.path("name").asText("");
            if (fieldName.isEmpty()) continue;

            ObjectNode prop = objectMapper.createObjectNode();
            // Query params 从 URL 取到的都是 string，所以 schema 中统一用 string
            prop.put("type", "string");

            // 复制 string 校验规则
            copyIfPresent(node, prop, "pattern");
            copyNumberIfPresent(node, prop, "minLength");
            copyNumberIfPresent(node, prop, "maxLength");

            String displayName = node.path("description").asText("");
            if (!displayName.isEmpty()) {
                prop.put("description", displayName);
            }

            properties.set(fieldName, prop);

            if (node.path("required").asBoolean(false)) {
                requiredArr.add(fieldName);
            }
        }

        schema.set("properties", properties);
        if (requiredArr.size() > 0) {
            schema.set("required", requiredArr);
        }
        return schema;
    }

    /**
     * 从单个 SchemaNode 构建 JSON Schema property（支持递归嵌套）
     */
    private ObjectNode buildPropertyFromNode(JsonNode node) {
        ObjectNode prop = objectMapper.createObjectNode();
        String type = node.path("type").asText("string");
        prop.put("type", type);

        String displayName = node.path("description").asText("");
        if (!displayName.isEmpty()) {
            prop.put("description", displayName);
        }

        switch (type) {
            case "string":
                copyIfPresent(node, prop, "pattern");
                copyIfPresent(node, prop, "format");
                copyNumberIfPresent(node, prop, "minLength");
                copyNumberIfPresent(node, prop, "maxLength");
                break;

            case "number":
            case "integer":
                copyNumberIfPresent(node, prop, "minimum");
                copyNumberIfPresent(node, prop, "maximum");
                copyBoolIfPresent(node, prop, "exclusiveMinimum");
                copyBoolIfPresent(node, prop, "exclusiveMaximum");
                copyNumberIfPresent(node, prop, "multipleOf");
                break;

            case "array":
                copyNumberIfPresent(node, prop, "minItems");
                copyNumberIfPresent(node, prop, "maxItems");
                copyBoolIfPresent(node, prop, "uniqueItems");
                // items：将 children 构建为 object schema 后设置为 items
                ObjectNode itemsSchema = buildChildSchema(node.path("children"));
                if (itemsSchema != null) {
                    prop.set("items", itemsSchema);
                }
                break;

            case "object":
                // 将 children 直接展开为 properties
                ObjectNode objectSchema = buildChildSchema(node.path("children"));
                if (objectSchema != null) {
                    prop.setAll(objectSchema);
                }
                break;

            default:
                break;
        }

        return prop;
    }

    /**
     * 将 children 节点数组构建为包含 properties/required 的 object schema。
     * array 类型用作 items，object 类型直接 setAll 合并到父节点。
     *
     * @param children SchemaNode 子节点数组
     * @return 包含 type/properties/required 的 ObjectNode，或 null
     */
    private ObjectNode buildChildSchema(JsonNode children) {
        if (!children.isArray() || children.size() == 0) {
            return null;
        }
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode childProps = objectMapper.createObjectNode();
        ArrayNode childRequired = objectMapper.createArrayNode();
        for (JsonNode child : children) {
            String childField = child.path("name").asText("");
            if (childField.isEmpty()) continue;
            childProps.set(childField, buildPropertyFromNode(child));
            if (child.path("required").asBoolean(false)) {
                childRequired.add(childField);
            }
        }
        schema.set("properties", childProps);
        if (childRequired.size() > 0) {
            schema.set("required", childRequired);
        }
        return schema;
    }

    // ============================= 工具方法 =============================

    private void copyIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode val = from.path(field);
        if (!val.isMissingNode() && !val.isNull() && !val.asText("").isEmpty()) {
            to.put(field, val.asText());
        }
    }

    private void copyNumberIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode val = from.path(field);
        if (!val.isMissingNode() && !val.isNull() && val.isNumber()) {
            // Jackson ObjectNode 不支持 put(String, Number)，需按具体类型调用
            if (val.isInt()) {
                to.put(field, val.intValue());
            } else if (val.isLong()) {
                to.put(field, val.longValue());
            } else if (val.isDouble() || val.isFloat()) {
                to.put(field, val.doubleValue());
            } else {
                to.put(field, val.decimalValue());
            }
        }
    }

    private void copyBoolIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode val = from.path(field);
        if (!val.isMissingNode() && !val.isNull() && val.isBoolean() && val.asBoolean()) {
            to.put(field, true);
        }
    }

    // ============================= 中文消息转换 =============================

    /**
     * 将 networknt 校验库的 ValidationMessage 转换为中文友好提示。
     */
    private String toChineseMessage(ValidationMessage vm) {
        String path = vm.getInstanceLocation() != null
                ? vm.getInstanceLocation().toString()
                : "";
        // 字段路径去除开头的 $. 或 /
        String field = path.replaceFirst("^\\$\\.?", "").replaceFirst("^/", "");
        if (field.isEmpty()) {
            field = "根对象";
        }

        String type = vm.getType();
        if (type == null) {
            return "「" + field + "」" + vm.getMessage();
        }

        switch (type) {
            case "required":
                Object[] args = vm.getArguments();
                String missingField = (args != null && args.length > 0) ? args[0].toString() : null;
                return missingField != null
                        ? "「" + missingField + "」字段不能为空"
                        : "「" + field + "」缺少必填字段";
            case "type":
                return "「" + field + "」字段类型不匹配";
            case "pattern":
                return "「" + field + "」字段格式不匹配（正则校验失败）";
            case "minLength":
                return "「" + field + "」字段长度不足";
            case "maxLength":
                return "「" + field + "」字段超出最大长度限制";
            case "minimum":
            case "exclusiveMinimum":
                return "「" + field + "」字段值小于最小值限制";
            case "maximum":
            case "exclusiveMaximum":
                return "「" + field + "」字段值超出最大值限制";
            case "multipleOf":
                return "「" + field + "」字段值不满足倍数约束";
            case "minItems":
                return "「" + field + "」数组元素数不足";
            case "maxItems":
                return "「" + field + "」数组元素数超出限制";
            case "uniqueItems":
                return "「" + field + "」数组中存在重复元素";
            case "format":
                return "「" + field + "」字段格式不正确";
            case "enum":
                return "「" + field + "」字段值不在允许的枚举范围内";
            case "additionalProperties":
                return "「" + field + "」包含未定义的额外属性";
            default:
                return "「" + field + "」" + vm.getMessage();
        }
    }

}
