package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.exception.FlowException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传场景 biz_fields_schema 简易校验。
 */
public final class OssBizFieldValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OssBizFieldValidator() {
    }

    public static String validateAndSerialize(String schemaJson, Map<String, String> formFields) {
        if (StrUtil.isBlank(schemaJson)) {
            return formFields == null || formFields.isEmpty() ? null : toJson(formFields);
        }
        try {
            List<Map<String, Object>> schema = MAPPER.readValue(schemaJson, new TypeReference<>() {});
            Map<String, String> values = formFields != null ? formFields : Map.of();
            Map<String, String> result = new LinkedHashMap<>();
            for (Map<String, Object> field : schema) {
                String name = field.get("name") != null ? field.get("name").toString() : null;
                if (StrUtil.isBlank(name)) {
                    continue;
                }
                String value = values.get(name);
                boolean required = Boolean.TRUE.equals(field.get("required"));
                if (required && StrUtil.isBlank(value)) {
                    throw new FlowException("OSS_BIZ_FIELD_REQUIRED", "业务字段必填: " + name);
                }
                if (StrUtil.isBlank(value)) {
                    continue;
                }
                Object maxLength = field.get("maxLength");
                if (maxLength instanceof Number && value.length() > ((Number) maxLength).intValue()) {
                    throw new FlowException("OSS_BIZ_FIELD_TOO_LONG", "业务字段超长: " + name);
                }
                Object enumVal = field.get("enum");
                if (enumVal instanceof List<?> list && !list.isEmpty()) {
                    boolean matched = list.stream().anyMatch(v -> value.equals(String.valueOf(v)));
                    if (!matched) {
                        throw new FlowException("OSS_BIZ_FIELD_INVALID", "业务字段取值非法: " + name);
                    }
                }
                result.put(name, value);
            }
            return result.isEmpty() ? null : toJson(result);
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("OSS_BIZ_SCHEMA_INVALID", "biz_fields_schema 解析失败: " + e.getMessage());
        }
    }

    private static String toJson(Map<String, String> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new FlowException("OSS_BIZ_META_SERIALIZE_FAILED", "业务字段序列化失败");
        }
    }
}
