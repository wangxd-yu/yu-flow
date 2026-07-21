package org.yu.flow.module.openapi;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * OpenAPI 3.0 契约生成服务
 *
 * <p>读取所有已发布的动态 API 定义，将其元数据 + 前端定义的 contract（SchemaNode 树）
 * 映射为标准 OpenAPI 3.0.3 规范的 JSON 文档。</p>
 *
 * <h3>映射关系</h3>
 * <ul>
 *   <li>已发布快照 {@code url} → OpenAPI path（草稿 URL 不暴露）</li>
 *   <li>已发布快照 {@code method} → HTTP method</li>
 *   <li>已发布快照 {@code name} → operation summary</li>
 *   <li>已发布快照 {@code info} → operation description</li>
 *   <li>已发布快照 {@code tags} → operation tags（逗号分隔）</li>
 *   <li>{@code FlowApiDO.directoryId} → 按目录分组的 tag</li>
 *   <li>已发布快照 {@code contract} → parameters / requestBody / responses</li>
 * </ul>
 *
 * <h3>Contract JSON 结构（前端 ControllerForm 生成）</h3>
 * <pre>
 * {
 *   "request": {
 *     "query":      [...SchemaNode[]],
 *     "pathParams": [...SchemaNode[]],
 *     "headers":    [...SchemaNode[]],
 *     "body":       [...SchemaNode[]],
 *     "bodyType":   "json" | "none" | "raw",
 *     "rawBody":    ""
 *   },
 *   "responses": {
 *     "200": {
 *       "statusCode":  200,
 *       "description": "成功",
 *       "body":        [...SchemaNode[]]
 *     }
 *   }
 * }
 * </pre>
 *
 * @author yu-flow
 */
@Slf4j
@Service
public class OpenApiGeneratorService {

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    /**
     * OpenAPI 契约缓存 (TTL 30秒，按 Server URL 分区)
     */
    private final Cache<String, String> openApiCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(50)
            .build();

    /**
     * 生成完整的 OpenAPI 3.0 文档
     *
     * @return OpenAPI JSON 字符串
     */
    public String generateOpenApiJson(HttpServletRequest request) {
        String cacheKey = "default";
        if (request != null) {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            cacheKey = scheme + "://" + serverName + ":" + serverPort + request.getContextPath();
        }

        return openApiCache.get(cacheKey, key -> {
            try {
                ObjectNode root = buildOpenApiDocument(request);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            } catch (Exception e) {
                log.error("[OpenApiGenerator] 生成 OpenAPI 文档失败", e);
                return "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"Yu Flow API\",\"version\":\"1.0.0\"},\"paths\":{}}";
            }
        });
    }

    /**
     * 生成 OpenAPI 文档的 Jackson 树节点
     */
    public ObjectNode buildOpenApiDocument(HttpServletRequest request) {
        ObjectNode root = objectMapper.createObjectNode();

        // ─── openapi 版本 ───
        root.put("openapi", "3.0.3");

        // ─── info ───
        ObjectNode info = objectMapper.createObjectNode();
        info.put("title", "Yu Flow 动态 API 文档");
        info.put("description", "由 Yu Flow 引擎自动生成的 OpenAPI 契约文档。所有已发布的动态接口均会出现在此文档中。");
        info.put("version", "1.0.0");
        ObjectNode contact = objectMapper.createObjectNode();
        contact.put("name", "Yu Flow");
        info.set("contact", contact);
        root.set("info", info);

        // ─── servers ───
        if (request != null) {
            ArrayNode servers = objectMapper.createArrayNode();
            ObjectNode server = objectMapper.createObjectNode();
            
            // 构建 server url，例如 http://localhost:8080/flow
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            String contextPath = request.getContextPath();
            
            StringBuilder serverUrl = new StringBuilder();
            serverUrl.append(scheme).append("://").append(serverName);
            if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
                serverUrl.append(":").append(serverPort);
            }
            if (StrUtil.isNotBlank(contextPath)) {
                serverUrl.append(contextPath);
            }
            
            server.put("url", serverUrl.toString());
            server.put("description", "当前服务地址");
            servers.add(server);
            root.set("servers", servers);
        }

        // ─── 加载目录映射（directoryId → name） ───
        Map<String, String> directoryNameMap = loadDirectoryNameMap();

        // ─── 加载所有已发布的 API ───
        List<FlowApiDO> publishedApis = flowApiRepository.findByPublishStatus(1);
        log.info("[OpenApiGenerator] 共发现 {} 个已发布 API，开始生成 OpenAPI 文档", publishedApis.size());

        // ─── paths ───
        ObjectNode paths = objectMapper.createObjectNode();

        // ─── 收集所有 tag ───
        Set<String> tagNames = new LinkedHashSet<>();

        for (FlowApiDO api : publishedApis) {
            String publishedUrl = PublishedApiSnapshot.resolveUrl(api);
            String publishedMethod = PublishedApiSnapshot.resolveMethod(api);
            if (publishedUrl == null || publishedMethod == null) continue;

            String path = normalizePathToOpenApi(publishedUrl);
            String method = publishedMethod.toLowerCase();

            // 确保 path 对象存在
            ObjectNode pathItem;
            if (paths.has(path)) {
                pathItem = (ObjectNode) paths.get(path);
            } else {
                pathItem = objectMapper.createObjectNode();
                paths.set(path, pathItem);
            }

            // 构建 operation（契约/摘要均取发布快照）
            ObjectNode operation = buildOperation(api, directoryNameMap, tagNames);
            pathItem.set(method, operation);
        }

        root.set("paths", paths);

        // ─── tags ───
        ArrayNode tagsArray = objectMapper.createArrayNode();
        for (String tagName : tagNames) {
            ObjectNode tag = objectMapper.createObjectNode();
            tag.put("name", tagName);
            tagsArray.add(tag);
        }
        root.set("tags", tagsArray);

        return root;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  内部：构建单个 Operation 对象
    // ═══════════════════════════════════════════════════════════════════

    private ObjectNode buildOperation(FlowApiDO api, Map<String, String> directoryNameMap, Set<String> tagNames) {
        ObjectNode operation = objectMapper.createObjectNode();

        String summary = PublishedApiSnapshot.resolveName(api);
        String description = PublishedApiSnapshot.resolveInfo(api);
        String tagsCsv = PublishedApiSnapshot.resolveTags(api);

        // ── summary / description ──
        if (StrUtil.isNotBlank(summary)) {
            operation.put("summary", summary);
        }
        if (StrUtil.isNotBlank(description)) {
            operation.put("description", description);
        }

        // ── operationId ──
        operation.put("operationId", generateOperationId(api));

        // ── tags ──
        ArrayNode tagsArr = objectMapper.createArrayNode();
        // 优先使用用户自定义 tags（发布快照）
        if (StrUtil.isNotBlank(tagsCsv)) {
            for (String t : tagsCsv.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    tagsArr.add(trimmed);
                    tagNames.add(trimmed);
                }
            }
        }
        // 补充目录 tag
        if (StrUtil.isNotBlank(api.getDirectoryId())) {
            String dirName = directoryNameMap.get(api.getDirectoryId());
            if (dirName != null && !dirName.isEmpty()) {
                tagsArr.add(dirName);
                tagNames.add(dirName);
            }
        }
        // 如果没有任何 tag，按 serviceType 分组
        if (tagsArr.size() == 0) {
            String defaultTag = api.getServiceType() != null ? api.getServiceType() : "default";
            tagsArr.add(defaultTag);
            tagNames.add(defaultTag);
        }
        operation.set("tags", tagsArr);

        // ── 解析 contract（已发布快照优先） ──
        JsonNode contract = parseContract(PublishedApiSnapshot.resolveContract(api));

        if (contract != null) {
            JsonNode requestNode = contract.path("request");

            // ── parameters (query + path + headers) ──
            ArrayNode parameters = objectMapper.createArrayNode();
            addParametersFromSchemaNodes(parameters, requestNode.path("query"), "query");
            addParametersFromSchemaNodes(parameters, requestNode.path("pathParams"), "path");
            addParametersFromSchemaNodes(parameters, requestNode.path("headers"), "header");
            if (parameters.size() > 0) {
                operation.set("parameters", parameters);
            }

            // ── requestBody ──
            String bodyType = requestNode.path("bodyType").asText("none");
            if (!"none".equals(bodyType)) {
                ObjectNode requestBody = buildRequestBody(requestNode, bodyType);
                if (requestBody != null) {
                    operation.set("requestBody", requestBody);
                }
            }

            // ── responses ──
            ObjectNode responses = buildResponses(contract.path("responses"));
            operation.set("responses", responses);
        } else {
            // 无 contract 时提供默认 response
            ObjectNode responses = objectMapper.createObjectNode();
            ObjectNode resp200 = objectMapper.createObjectNode();
            resp200.put("description", "成功");
            ObjectNode content = objectMapper.createObjectNode();
            ObjectNode jsonType = objectMapper.createObjectNode();
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            jsonType.set("schema", schema);
            content.set("application/json", jsonType);
            resp200.set("content", content);
            responses.set("200", resp200);
            operation.set("responses", responses);
        }

        return operation;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  内部：Parameters 构建（Query / Path / Header）
    // ═══════════════════════════════════════════════════════════════════

    private void addParametersFromSchemaNodes(ArrayNode parameters, JsonNode nodesArray, String in) {
        if (!nodesArray.isArray()) return;

        for (JsonNode node : nodesArray) {
            String name = node.path("name").asText("");
            if (name.isEmpty()) continue;

            ObjectNode param = objectMapper.createObjectNode();
            param.put("name", name);
            param.put("in", in);

            String description = node.path("description").asText("");
            if (!description.isEmpty()) {
                param.put("description", description);
            }

            boolean required = node.path("required").asBoolean(false);
            // path 参数始终 required
            if ("path".equals(in)) {
                required = true;
            }
            param.put("required", required);

            // schema
            ObjectNode schema = buildSchemaFromNode(node);
            param.set("schema", schema);

            parameters.add(param);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  内部：RequestBody 构建
    // ═══════════════════════════════════════════════════════════════════

    private ObjectNode buildRequestBody(JsonNode requestNode, String bodyType) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("required", true);

        ObjectNode content = objectMapper.createObjectNode();

        if ("json".equals(bodyType)) {
            JsonNode bodyNodes = requestNode.path("body");
            if (bodyNodes.isArray() && bodyNodes.size() > 0) {
                ObjectNode mediaType = objectMapper.createObjectNode();
                ObjectNode schema = buildObjectSchemaFromNodes(bodyNodes);
                mediaType.set("schema", schema);
                content.set("application/json", mediaType);
            } else {
                // 空 body 定义
                ObjectNode mediaType = objectMapper.createObjectNode();
                ObjectNode schema = objectMapper.createObjectNode();
                schema.put("type", "object");
                mediaType.set("schema", schema);
                content.set("application/json", mediaType);
            }
        } else if ("raw".equals(bodyType)) {
            ObjectNode mediaType = objectMapper.createObjectNode();
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "string");
            mediaType.set("schema", schema);

            // 添加 example
            String rawBody = requestNode.path("rawBody").asText("");
            if (!rawBody.isEmpty()) {
                mediaType.put("example", rawBody);
            }
            content.set("text/plain", mediaType);
        } else {
            // form-data 等其他类型
            ObjectNode mediaType = objectMapper.createObjectNode();
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            mediaType.set("schema", schema);
            content.set("application/x-www-form-urlencoded", mediaType);
        }

        requestBody.set("content", content);
        return requestBody;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  内部：Responses 构建
    // ═══════════════════════════════════════════════════════════════════

    private ObjectNode buildResponses(JsonNode responsesNode) {
        ObjectNode responses = objectMapper.createObjectNode();

        if (responsesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = responsesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String statusCode = entry.getKey();
                JsonNode respDef = entry.getValue();

                ObjectNode responseObj = objectMapper.createObjectNode();
                responseObj.put("description", respDef.path("description").asText(""));

                JsonNode bodyNodes = respDef.path("body");
                if (bodyNodes.isArray() && bodyNodes.size() > 0) {
                    ObjectNode content = objectMapper.createObjectNode();
                    ObjectNode mediaType = objectMapper.createObjectNode();
                    ObjectNode schema = buildObjectSchemaFromNodes(bodyNodes);
                    mediaType.set("schema", schema);
                    content.set("application/json", mediaType);
                    responseObj.set("content", content);
                }

                responses.set(statusCode, responseObj);
            }
        }

        // 确保至少有一个 200 响应
        if (!responses.has("200")) {
            ObjectNode resp200 = objectMapper.createObjectNode();
            resp200.put("description", "成功");
            ObjectNode content = objectMapper.createObjectNode();
            ObjectNode mediaType = objectMapper.createObjectNode();
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            mediaType.set("schema", schema);
            content.set("application/json", mediaType);
            resp200.set("content", content);
            responses.set("200", resp200);
        }

        return responses;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  内部：SchemaNode → JSON Schema 转换
    //  （复用 SchemaValidatorService 中已验证的逻辑）
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 将 SchemaNode 数组转为 { type: "object", properties: {...}, required: [...] }
     */
    private ObjectNode buildObjectSchemaFromNodes(JsonNode nodesArray) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();

        for (JsonNode node : nodesArray) {
            String fieldName = node.path("name").asText("");
            if (fieldName.isEmpty()) continue;

            ObjectNode prop = buildSchemaFromNode(node);
            properties.set(fieldName, prop);

            if (node.path("required").asBoolean(false)) {
                required.add(fieldName);
            }
        }

        schema.set("properties", properties);
        if (required.size() > 0) {
            schema.set("required", required);
        }
        return schema;
    }

    /**
     * 从单个 SchemaNode 构建 JSON Schema property（递归支持嵌套）
     */
    private ObjectNode buildSchemaFromNode(JsonNode node) {
        ObjectNode prop = objectMapper.createObjectNode();
        String type = node.path("type").asText("string");
        prop.put("type", type);

        String description = node.path("description").asText("");
        if (!description.isEmpty()) {
            prop.put("description", description);
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
                copyNumberIfPresent(node, prop, "exclusiveMinimum");
                copyNumberIfPresent(node, prop, "exclusiveMaximum");
                copyNumberIfPresent(node, prop, "multipleOf");
                break;

            case "boolean":
                // boolean 类型无额外约束
                break;

            case "array":
                copyNumberIfPresent(node, prop, "minItems");
                copyNumberIfPresent(node, prop, "maxItems");
                copyBoolIfPresent(node, prop, "uniqueItems");
                JsonNode children = node.path("children");
                if (children.isArray() && children.size() > 0) {
                    ObjectNode itemsSchema;
                    if (children.size() == 1
                            && "items".equals(children.get(0).path("name").asText())) {
                        // Query 基础类型数组。
                        itemsSchema = buildSchemaFromNode(children.get(0));
                    } else {
                        // Body 对象数组。
                        itemsSchema = buildObjectSchemaFromNodes(children);
                    }
                    prop.set("items", itemsSchema);
                } else {
                    // 无 children 时默认 items 为 object
                    ObjectNode items = objectMapper.createObjectNode();
                    items.put("type", "object");
                    prop.set("items", items);
                }
                break;

            case "object":
                JsonNode objChildren = node.path("children");
                if (objChildren.isArray() && objChildren.size() > 0) {
                    ObjectNode childSchema = buildObjectSchemaFromNodes(objChildren);
                    // 合并 properties 和 required 到当前节点
                    if (childSchema.has("properties")) {
                        prop.set("properties", childSchema.get("properties"));
                    }
                    if (childSchema.has("required")) {
                        prop.set("required", childSchema.get("required"));
                    }
                }
                break;

            default:
                break;
        }

        return prop;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 将 Yu Flow 的 URL 路径转换为 OpenAPI 规范的路径格式。
     * <p>主要处理：确保前导 /，以及将 Spring 风格 {param} 保留（已兼容 OpenAPI 格式）。</p>
     */
    private String normalizePathToOpenApi(String url) {
        String path = url.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    /**
     * 生成唯一的 operationId
     */
    private String generateOperationId(FlowApiDO api) {
        String methodRaw = PublishedApiSnapshot.resolveMethod(api);
        String method = methodRaw != null ? methodRaw.toLowerCase() : "get";
        String path = PublishedApiSnapshot.resolveUrl(api);
        if (path == null) {
            path = "";
        }
        // 将 /api/user/{id} → api_user_id
        String pathPart = path
                .replaceAll("[{}]", "")
                .replaceAll("[^a-zA-Z0-9/]", "_")
                .replaceAll("/+", "_")
                .replaceAll("^_+|_+$", "");
        return method + "_" + pathPart;
    }

    /**
     * 安全解析 contract JSON 字符串
     */
    private JsonNode parseContract(String contractJson) {
        if (StrUtil.isBlank(contractJson)) return null;
        try {
            return objectMapper.readTree(contractJson);
        } catch (Exception e) {
            log.warn("[OpenApiGenerator] 解析 contract 失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 加载目录 ID → 名称映射
     */
    private Map<String, String> loadDirectoryNameMap() {
        try {
            List<FlowDirectoryDO> dirs = flowDirectoryRepository.findAll();
            return dirs.stream().collect(Collectors.toMap(
                    FlowDirectoryDO::getId,
                    FlowDirectoryDO::getName,
                    (a, b) -> a
            ));
        } catch (Exception e) {
            log.warn("[OpenApiGenerator] 加载目录映射失败：{}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ── 复制工具（与 SchemaValidatorService 保持一致） ──

    private void copyIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode val = from.path(field);
        if (!val.isMissingNode() && !val.isNull() && !val.asText("").isEmpty()) {
            to.put(field, val.asText());
        }
    }

    private void copyNumberIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode val = from.path(field);
        if (!val.isMissingNode() && !val.isNull() && val.isNumber()) {
            if (val.isInt()) {
                to.put(field, val.intValue());
            } else if (val.isLong()) {
                to.put(field, val.longValue());
            } else {
                to.put(field, val.doubleValue());
            }
        }
    }

    private void copyBoolIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode val = from.path(field);
        if (!val.isMissingNode() && !val.isNull() && val.isBoolean() && val.asBoolean()) {
            to.put(field, true);
        }
    }
}
