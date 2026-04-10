package org.yu.flow.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON Schema 入参前置校验服务
 *
 * <p>基于 networknt/json-schema-validator 高性能校验库，将前端定义的 JSON Schema 规则
 * 应用于运行时请求体，校验不通过时抛出携带中文友好提示的 {@link SchemaValidationException}。</p>
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
     * 校验请求体 JSON 是否满足给定的 JSON Schema 规则。
     *
     * @param schemaJson      前端存储的 JSON Schema 字符串（如 {@code {"type":"object","properties":{...},"required":[...]}} ）
     * @param requestBodyJson 用户实际请求的 JSON 字符串
     * @throws SchemaValidationException 校验失败时，携带所有校验错误的中文友好提示
     */
    public void validate(String schemaJson, String requestBodyJson) {
        if (StrUtil.isBlank(schemaJson) || StrUtil.isBlank(requestBodyJson)) {
            return;
        }

        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            JsonNode dataNode = objectMapper.readTree(requestBodyJson);

            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(dataNode);

            if (errors != null && !errors.isEmpty()) {
                List<String> messages = errors.stream()
                        .map(this::toChineseMessage)
                        .collect(Collectors.toList());

                String combined = String.join("；", messages);
                log.warn("[SchemaValidator] 入参校验失败: {}", combined);
                throw new SchemaValidationException(combined, messages);
            }
        } catch (SchemaValidationException e) {
            // 直接向上抛出，不要被下面的 catch 吃掉
            throw e;
        } catch (Exception e) {
            log.error("[SchemaValidator] Schema 解析或校验过程异常", e);
            throw new SchemaValidationException("请求参数格式不正确: " + e.getMessage());
        }
    }

    /**
     * 校验请求体（已解析为 Map）是否满足给定的 JSON Schema 规则。
     * 避免在拦截器中重复序列化。
     *
     * @param schemaJson 前端存储的 JSON Schema 字符串
     * @param bodyParams 已解析的请求体参数 Map
     * @throws SchemaValidationException 校验失败时抛出
     */
    public void validate(String schemaJson, Map<String, Object> bodyParams) {
        if (StrUtil.isBlank(schemaJson) || bodyParams == null || bodyParams.isEmpty()) {
            return;
        }

        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            JsonNode dataNode = objectMapper.valueToTree(bodyParams);

            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(dataNode);

            if (errors != null && !errors.isEmpty()) {
                List<String> messages = errors.stream()
                        .map(this::toChineseMessage)
                        .collect(Collectors.toList());

                String combined = String.join("；", messages);
                log.warn("[SchemaValidator] 入参校验失败: {}", combined);
                throw new SchemaValidationException(combined, messages);
            }
        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SchemaValidator] Schema 解析或校验过程异常", e);
            throw new SchemaValidationException("请求参数格式不正确: " + e.getMessage());
        }
    }

    // ============================= 中文消息转换 =============================

    /**
     * 将 networknt 校验库的 ValidationMessage 转换为中文友好提示。
     *
     * <p>常见的 type 映射：</p>
     * <ul>
     *   <li>required → "xxx 字段不能为空"</li>
     *   <li>type → "xxx 字段类型不匹配"</li>
     *   <li>pattern → "xxx 字段格式不匹配"</li>
     *   <li>minLength / maxLength → 长度提示</li>
     *   <li>minimum / maximum → 范围提示</li>
     * </ul>
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
                // 从消息中提取缺失的字段名
                String missingField = extractMissingField(vm.getMessage());
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

    /**
     * 从 required 类型的原始消息中提取缺失字段名。
     * networknt 格式示例: "$.fieldName: is missing but it is required"
     */
    private String extractMissingField(String message) {
        if (message == null) return null;
        // 尝试从方括号中提取: "required property 'xxx' not found"
        int start = message.indexOf('\'');
        int end = message.lastIndexOf('\'');
        if (start >= 0 && end > start) {
            return message.substring(start + 1, end);
        }
        return null;
    }
}
