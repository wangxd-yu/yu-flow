package org.yu.flow.module.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.List;

/**
 * OpenAPI 3.0 规范构建器（Phase 3.3）。
 *
 * <p>根据已发布 API 列表（{@link FlowApiDO} 集合）生成符合 OpenAPI 3.0 规范的
 * JSON/YAML 文档，供 `/flow-api/open-api/spec` 端点输出。</p>
 *
 * <h2>规范覆盖</h2>
 * <ul>
 *   <li>基础元数据（info.title / info.version / info.description）</li>
 *   <li>每个已发布 API 的 paths 条目（method、url、summary、description）</li>
 *   <li>从 {@code contract} 字段解析 requestBody / parameters（若存在）</li>
 *   <li>通用 responses 定义（200、400、500）</li>
 *   <li>securitySchemes（Bearer JWT）</li>
 * </ul>
 */
@Component
public class OpenApiSpecBuilder {

    private static final ObjectMapper JSON = FlowObjectMapperUtil.flowObjectMapper();

    /**
     * 构建 OpenAPI 3.0 JSON 规范。
     *
     * @param apis         已发布 API 列表
     * @param serverUrl    服务基础 URL（如 {@code http://localhost:11281/flow}）
     * @param title        文档标题
     * @param version      文档版本号
     * @return OpenAPI 3.0 规范的 JSON ObjectNode
     */
    public ObjectNode build(List<org.yu.flow.module.api.domain.FlowApiDO> apis,
                            String serverUrl, String title, String version) {
        ObjectNode root = JSON.createObjectNode();

        // openapi 版本
        root.put("openapi", "3.0.3");

        // info
        ObjectNode info = root.putObject("info");
        info.put("title", title != null ? title : "Yu Flow API");
        info.put("version", version != null ? version : "1.0.0");
        info.put("description", "Yu Flow 动态 API 平台 - 由系统自动生成");
        info.putObject("contact")
                .put("name", "Yu Flow")
                .put("url", "https://github.com/yu-flow");

        // servers
        ArrayNode servers = root.putArray("servers");
        ObjectNode server = servers.addObject();
        server.put("url", serverUrl != null ? serverUrl : "/");
        server.put("description", "默认服务器");

        // security schemes
        ObjectNode components = root.putObject("components");
        ObjectNode secSchemes = components.putObject("securitySchemes");
        ObjectNode bearerAuth = secSchemes.putObject("bearerAuth");
        bearerAuth.put("type", "http");
        bearerAuth.put("scheme", "bearer");
        bearerAuth.put("bearerFormat", "JWT");

        // 通用响应定义
        ObjectNode schemas = components.putObject("schemas");
        buildErrorSchema(schemas);
        buildPageSchema(schemas);

        // paths
        ObjectNode paths = root.putObject("paths");
        if (apis != null) {
            for (org.yu.flow.module.api.domain.FlowApiDO api : apis) {
                addPath(paths, api);
            }
        }

        return root;
    }

    // ----------------------------------------------------------------

    private void addPath(ObjectNode paths, org.yu.flow.module.api.domain.FlowApiDO api) {
        String url = PublishedApiSnapshot.resolveUrl(api);
        String rawMethod = PublishedApiSnapshot.resolveMethod(api);
        if (url == null || url.isBlank() || rawMethod == null || rawMethod.isBlank()) {
            return;
        }

        // 规范 path 须以 / 开头
        String path = url.startsWith("/") ? url : "/" + url;
        String method = rawMethod.toLowerCase();

        ObjectNode pathItem = (ObjectNode) paths.get(path);
        if (pathItem == null) {
            pathItem = paths.putObject(path);
        }

        ObjectNode operation = pathItem.putObject(method);

        // summary / description / operationId
        String name = api.getName() != null ? api.getName() : url;
        operation.put("summary", name);
        operation.put("operationId", "api-" + api.getId());

        // tags（来自 api.tags 字段，逗号分隔）
        ArrayNode tags = operation.putArray("tags");
        if (api.getTags() != null && !api.getTags().isBlank()) {
            for (String tag : api.getTags().split(",")) {
                tags.add(tag.trim());
            }
        } else {
            tags.add("API");
        }

        // info 描述
        if (api.getInfo() != null && !api.getInfo().isBlank()) {
            operation.put("description", api.getInfo());
        }

        // security（默认需要 JWT，若已配置 OPEN 鉴权可不要求）
        String secConfig = api.getSecurityConfig();
        boolean isOpen = isOpenAuth(secConfig);
        if (!isOpen) {
            operation.putArray("security").addObject().putArray("bearerAuth");
        }

        // requestBody（POST/PUT/PATCH 方法）
        boolean hasBody = "post".equals(method) || "put".equals(method) || "patch".equals(method);
        if (hasBody) {
            buildRequestBody(operation, api);
        } else {
            // GET/DELETE 参数从 contract 提取
            buildParameters(operation, api);
        }

        // responses
        buildResponses(operation, api.getResponseType());
    }

    private void buildRequestBody(ObjectNode operation, org.yu.flow.module.api.domain.FlowApiDO api) {
        // 尝试从 contract 解析字段
        JsonNode contractNode = parseContract(api.getContract());

        ObjectNode requestBody = operation.putObject("requestBody");
        requestBody.put("required", true);
        ObjectNode content = requestBody.putObject("content");
        ObjectNode jsonContent = content.putObject("application/json");
        ObjectNode schema = jsonContent.putObject("schema");
        schema.put("type", "object");

        if (contractNode != null && contractNode.isArray() && contractNode.size() > 0) {
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = null;
            for (JsonNode field : contractNode) {
                String fieldName = getStr(field, "name");
                if (fieldName == null || fieldName.isBlank()) continue;
                ObjectNode prop = properties.putObject(fieldName);
                String fieldType = mapFieldType(getStr(field, "type"));
                prop.put("type", fieldType);
                String label = getStr(field, "label");
                if (label != null) prop.put("description", label);
                // required 字段
                if (Boolean.TRUE.equals(field.get("required") != null
                        && field.get("required").asBoolean(false))) {
                    if (required == null) required = schema.putArray("required");
                    required.add(fieldName);
                }
            }
        } else {
            // 无契约时给个通用示例
            schema.put("description", "请求参数（无契约定义）");
        }
    }

    private void buildParameters(ObjectNode operation, org.yu.flow.module.api.domain.FlowApiDO api) {
        JsonNode contractNode = parseContract(api.getContract());
        ArrayNode params = operation.putArray("parameters");

        if (contractNode != null && contractNode.isArray()) {
            for (JsonNode field : contractNode) {
                String fieldName = getStr(field, "name");
                if (fieldName == null || fieldName.isBlank()) continue;
                ObjectNode param = params.addObject();
                param.put("name", fieldName);
                param.put("in", "query");
                String label = getStr(field, "label");
                param.put("description", label != null ? label : fieldName);
                param.put("required", field.get("required") != null
                        && field.get("required").asBoolean(false));
                param.putObject("schema").put("type", mapFieldType(getStr(field, "type")));
            }
        }
    }

    private void buildResponses(ObjectNode operation, String responseType) {
        ObjectNode responses = operation.putObject("responses");

        ObjectNode ok = responses.putObject("200");
        ok.put("description", "成功");
        ObjectNode okContent = ok.putObject("content").putObject("application/json");
        ObjectNode okSchema = okContent.putObject("schema");
        okSchema.put("type", "object");
        okSchema.putObject("properties")
                .putObject("ok").put("type", "boolean");

        // 数据字段（根据 responseType）
        ObjectNode dataSchema = okSchema.putObject("properties").putObject("data");
        if ("PAGE".equalsIgnoreCase(responseType)) {
            dataSchema.put("$ref", "#/components/schemas/PageResult");
        } else if ("LIST".equalsIgnoreCase(responseType)) {
            dataSchema.put("type", "array");
            dataSchema.putObject("items").put("type", "object");
        } else {
            dataSchema.put("type", "object");
        }

        responses.putObject("400")
                .put("description", "参数校验失败")
                .putObject("content").putObject("application/json")
                .putObject("schema").put("$ref", "#/components/schemas/ErrorResponse");

        responses.putObject("500")
                .put("description", "系统内部错误")
                .putObject("content").putObject("application/json")
                .putObject("schema").put("$ref", "#/components/schemas/ErrorResponse");
    }

    private void buildErrorSchema(ObjectNode schemas) {
        ObjectNode error = schemas.putObject("ErrorResponse");
        error.put("type", "object");
        ObjectNode props = error.putObject("properties");
        props.putObject("ok").put("type", "boolean").put("example", false);
        props.putObject("code").put("type", "integer").put("example", 400);
        props.putObject("msg").put("type", "string").put("example", "参数错误");
    }

    private void buildPageSchema(ObjectNode schemas) {
        ObjectNode page = schemas.putObject("PageResult");
        page.put("type", "object");
        ObjectNode props = page.putObject("properties");
        props.putObject("total").put("type", "integer");
        props.putObject("pageNum").put("type", "integer");
        props.putObject("pageSize").put("type", "integer");
        props.putObject("list").put("type", "array").putObject("items").put("type", "object");
    }

    // ----------------------------------------------------------------
    // 辅助方法
    // ----------------------------------------------------------------

    private JsonNode parseContract(String contract) {
        if (contract == null || contract.isBlank()) return null;
        try {
            return JSON.readTree(contract);
        } catch (Exception e) {
            return null;
        }
    }

    private String getStr(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v == null || v.isNull()) ? null : v.asText(null);
    }

    private String mapFieldType(String yuFlowType) {
        if (yuFlowType == null) return "string";
        return switch (yuFlowType.toLowerCase()) {
            case "integer", "int", "long" -> "integer";
            case "number", "float", "double", "decimal" -> "number";
            case "boolean", "bool" -> "boolean";
            case "array", "list" -> "array";
            case "object", "map" -> "object";
            default -> "string";
        };
    }

    private boolean isOpenAuth(String secConfig) {
        if (secConfig == null || secConfig.isBlank()) return false;
        try {
            JsonNode node = JSON.readTree(secConfig);
            JsonNode mode = node.get("authMode");
            return mode != null && "OPEN".equalsIgnoreCase(mode.asText());
        } catch (Exception e) {
            return false;
        }
    }
}
