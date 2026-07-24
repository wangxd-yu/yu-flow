package org.yu.flow.module.open.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.module.open.auth.OpenAuthService;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.dto.OpenGrantItemDTO;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.openapi.OpenApiGeneratorService;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 按平台授权过滤 OpenAPI / Markdown，并附带完整鉴权（授权）说明。
 */
@Service
public class OpenPlatformDocService {

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(
            YAMLFactory.builder()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .build());

    @Resource
    private OpenApiGeneratorService openApiGeneratorService;
    @Resource
    private FlowOpenPlatformService flowOpenPlatformService;
    @Resource
    private FlowOpenPlatformRepository platformRepository;
    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;

    public ObjectNode buildOpenApi(String platformId, HttpServletRequest request) {
        FlowOpenPlatformDO platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new FlowException("OPEN_PLATFORM_NOT_FOUND", "平台不存在"));
        Map<String, String> allowMethodsByApiId = loadAllowMethods(platformId);
        Set<String> allow = new HashSet<>(allowMethodsByApiId.keySet());

        ObjectNode full = openApiGeneratorService.buildOpenApiDocument(request);
        ObjectNode root = full.deepCopy();

        ObjectNode info = (ObjectNode) root.get("info");
        if (info == null) {
            info = objectMapper.createObjectNode();
            root.set("info", info);
        }
        info.put("title", "Yu Flow 开放 API · " + platform.getName());
        info.put("description", buildAuthGuideBody(platform, request));

        rewriteServers(root, request);
        applyOpenSecuritySchemes(root);

        ObjectNode paths = root.has("paths") && root.get("paths").isObject()
                ? (ObjectNode) root.get("paths")
                : objectMapper.createObjectNode();
        ObjectNode filtered = objectMapper.createObjectNode();
        String prefix = entryPrefix();

        Iterator<Map.Entry<String, JsonNode>> it = paths.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String path = e.getKey();
            JsonNode methods = e.getValue();
            if (!methods.isObject()) continue;
            ObjectNode methodObj = (ObjectNode) methods;
            ObjectNode keptMethods = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> mit = methodObj.fields();
            while (mit.hasNext()) {
                Map.Entry<String, JsonNode> me = mit.next();
                JsonNode op = me.getValue();
                String apiId = (op != null && op.has("x-yu-api-id"))
                        ? op.get("x-yu-api-id").asText(null) : null;
                if (apiId == null || !allow.contains(apiId) || op == null) {
                    continue;
                }
                String allowMethods = allowMethodsByApiId.get(apiId);
                if (StrUtil.isNotBlank(allowMethods)
                        && !OpenAuthService.methodAllowed(me.getKey(), allowMethods)) {
                    continue;
                }
                ObjectNode opCopy = op.deepCopy();
                if (StrUtil.isNotBlank(allowMethods)) {
                    opCopy.put("description", appendAllowMethodsNote(
                            opCopy.has("description") ? opCopy.get("description").asText("") : "",
                            allowMethods));
                }
                keptMethods.set(me.getKey(), opCopy);
            }
            if (keptMethods.size() > 0) {
                String openPath = path.startsWith("/") ? prefix + path : prefix + "/" + path;
                filtered.set(openPath, keptMethods);
            }
        }

        root.set("paths", filtered);
        return root;
    }

    public String buildOpenApiYaml(String platformId, HttpServletRequest request) {
        try {
            return yamlMapper.writeValueAsString(buildOpenApi(platformId, request));
        } catch (Exception e) {
            throw new FlowException("OPEN_DOC_YAML_FAIL", "导出 YAML 失败: " + e.getMessage());
        }
    }

    /**
     * Postman Collection v2.1（含开放鉴权头占位变量与鉴权说明）。
     */
    public ObjectNode buildPostmanCollection(String platformId, HttpServletRequest request) {
        FlowOpenPlatformDO platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new FlowException("OPEN_PLATFORM_NOT_FOUND", "平台不存在"));
        ObjectNode openApi = buildOpenApi(platformId, request);
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode info = objectMapper.createObjectNode();
        info.put("name", "Yu Flow Open · " + platform.getName());
        info.put("description", buildAuthGuideBody(platform, request)
                + "\n\n请配置 collection 变量 APP_KEY / APP_SECRET 并自行计算签名"
                + (yuFlowRuntimeSettings.resolveOpen().isAllowPlainSecret()
                ? "；当前环境亦可用明文 Secret（非生产推荐）。"
                : "。"));
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        root.set("info", info);

        ArrayNode variable = objectMapper.createArrayNode();
        variable.add(pmVar("baseUrl", serverBase(request)));
        variable.add(pmVar("APP_KEY", ""));
        variable.add(pmVar("APP_SECRET", ""));
        root.set("variable", variable);

        ArrayNode item = objectMapper.createArrayNode();
        ObjectNode guideItem = objectMapper.createObjectNode();
        guideItem.put("name", "【必读】鉴权 / 授权说明");
        guideItem.put("description", buildAuthGuideBody(platform, request));
        item.add(guideItem);

        JsonNode paths = openApi.get("paths");
        if (paths != null && paths.isObject()) {
            paths.fields().forEachRemaining(e -> {
                String path = e.getKey();
                if (!e.getValue().isObject()) return;
                e.getValue().fields().forEachRemaining(me -> {
                    String method = me.getKey();
                    ObjectNode reqItem = objectMapper.createObjectNode();
                    String name = path + " [" + method.toUpperCase() + "]";
                    if (me.getValue() != null && me.getValue().has("summary")) {
                        name = me.getValue().get("summary").asText(name);
                    }
                    reqItem.put("name", name);
                    ObjectNode requestNode = objectMapper.createObjectNode();
                    requestNode.put("method", method.toUpperCase());
                    ArrayNode headers = objectMapper.createArrayNode();
                    headers.add(pmHeader("X-Yu-App-Key", "{{APP_KEY}}"));
                    headers.add(pmHeader("X-Yu-Timestamp", "{{$timestamp}}"));
                    headers.add(pmHeader("X-Yu-Nonce", UUID.randomUUID().toString().replace("-", "")));
                    headers.add(pmHeader("X-Yu-Signature", "<hmac-sha256-hex>"));
                    if (yuFlowRuntimeSettings.resolveOpen().isAllowPlainSecret()) {
                        headers.add(pmHeader("X-Yu-App-Secret", "{{APP_SECRET}}"));
                    }
                    requestNode.set("header", headers);
                    ObjectNode url = objectMapper.createObjectNode();
                    url.put("raw", "{{baseUrl}}" + path);
                    url.put("host", objectMapper.createArrayNode().add("{{baseUrl}}"));
                    ArrayNode pathSeg = objectMapper.createArrayNode();
                    for (String seg : path.split("/")) {
                        if (StrUtil.isNotBlank(seg)) pathSeg.add(seg);
                    }
                    url.set("path", pathSeg);
                    requestNode.set("url", url);
                    reqItem.set("request", requestNode);
                    item.add(reqItem);
                });
            });
        }
        root.set("item", item);
        return root;
    }

    private ObjectNode pmVar(String key, String value) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("key", key);
        n.put("value", value == null ? "" : value);
        return n;
    }

    private ObjectNode pmHeader(String key, String value) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("key", key);
        n.put("value", value);
        n.put("type", "text");
        return n;
    }

    public String buildMarkdown(String platformId, HttpServletRequest request) {
        FlowOpenPlatformDO platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new FlowException("OPEN_PLATFORM_NOT_FOUND", "平台不存在"));
        ObjectNode api = buildOpenApi(platformId, request);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(platform.getName()).append(" · 开放接口文档\n\n");
        sb.append(buildAuthGuideBody(platform, request));
        sb.append("\n\n## 接口列表\n\n");
        JsonNode paths = api.get("paths");
        if (paths != null && paths.isObject() && paths.size() > 0) {
            paths.fields().forEachRemaining(e -> {
                sb.append("### `").append(e.getKey()).append("`\n\n");
                if (e.getValue().isObject()) {
                    e.getValue().fieldNames().forEachRemaining(m ->
                            sb.append("- ").append(m.toUpperCase()).append("\n"));
                }
                sb.append("\n");
            });
        } else {
            sb.append("_暂无已授权接口_\n\n");
        }
        sb.append("## curl 示例\n\n");
        if (paths != null && paths.isObject() && paths.size() > 0) {
            var first = paths.fields();
            if (first.hasNext()) {
                var e = first.next();
                String openPath = e.getKey();
                String method = "get";
                if (e.getValue().isObject() && e.getValue().fieldNames().hasNext()) {
                    method = e.getValue().fieldNames().next();
                }
                sb.append("```bash\n");
                sb.append("TS=$(date +%s)\n");
                sb.append("NONCE=$(uuidgen | tr -d '-')\n");
                sb.append("REAL_PATH=\"").append(stripOpenPrefix(openPath)).append("\"\n");
                sb.append("BODY_HASH=\"\"   # 有 body 时填 sha256hex(body)\n");
                sb.append("PAYLOAD=\"$(printf '%s\\n%s\\n%s\\n%s\\n%s' \"")
                        .append(method.toUpperCase())
                        .append("\" \"$REAL_PATH\" \"$TS\" \"$NONCE\" \"$BODY_HASH\")\"\n");
                sb.append("SIGN=$(printf '%s' \"$PAYLOAD\" | openssl dgst -sha256 -hmac \"$APP_SECRET\" | awk '{print $2}')\n");
                sb.append("curl -X ").append(method.toUpperCase()).append(" \\\n");
                sb.append("  '").append(serverBase(request)).append(openPath).append("' \\\n");
                sb.append("  -H 'X-Yu-App-Key: $APP_KEY' \\\n");
                sb.append("  -H 'X-Yu-Timestamp: '$TS \\\n");
                sb.append("  -H 'X-Yu-Nonce: '$NONCE \\\n");
                sb.append("  -H 'X-Yu-Signature: '$SIGN\n");
                sb.append("```\n");
            }
        } else {
            sb.append("_暂无已授权接口，无法生成示例_\n");
        }
        return sb.toString();
    }

    /**
     * 对接说明（不含 Secret）：入口、鉴权头、签名算法、错误码。
     */
    public String buildIntegrationGuide(String platformId, HttpServletRequest request) {
        FlowOpenPlatformDO platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new FlowException("OPEN_PLATFORM_NOT_FOUND", "平台不存在"));
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(platform.getName()).append(" · 开放平台对接说明\n\n");
        sb.append(buildAuthGuideBody(platform, request));
        sb.append("\n\n完整接口清单请导出 OpenAPI / Markdown。\n");
        return sb.toString();
    }

    /**
     * 鉴权 / 授权说明正文（不含一级标题），供 OpenAPI / Markdown / Postman / 对接说明复用。
     */
    String buildAuthGuideBody(FlowOpenPlatformDO platform, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 1. 调用入口\n\n");
        sb.append("- 平台：`").append(platform.getName()).append("`（编码 `")
                .append(platform.getCode()).append("`）\n");
        sb.append("- Base：`").append(serverBase(request)).append("`\n");
        sb.append("- 开放前缀：`").append(entryPrefix()).append("`\n");
        sb.append("- 完整 URL：`{Base}").append(entryPrefix()).append("{真实发布path}`\n");
        sb.append("- 示例：真实 path 为 `/demo/hello` → `")
                .append(entryPrefix()).append("/demo/hello`\n");
        if (yuFlowRuntimeSettings.resolveOpen().isAllowDirectPath()) {
            sb.append("- 亦可直连真实 path（需请求头携带 AppKey，且宿主放行）\n");
        }
        sb.append("\n## 2. 鉴权头\n\n");
        sb.append("| Header | 说明 |\n|---|---|\n");
        sb.append("| X-Yu-App-Key | 平台 AppKey |\n");
        sb.append("| X-Yu-Timestamp | Unix 秒级时间戳 |\n");
        sb.append("| X-Yu-Nonce | 随机串，").append(yuFlowRuntimeSettings.resolveOpen().getSkewSeconds())
                .append("s 内不可重复 |\n");
        sb.append("| X-Yu-Signature | HMAC-SHA256 十六进制 |\n");
        if (yuFlowRuntimeSettings.resolveOpen().isAllowPlainSecret()) {
            sb.append("| X-Yu-App-Secret | 明文 Secret（仅无 Signature 时；生产建议关闭） |\n");
        }
        sb.append("\n## 3. 签名串\n\n");
        sb.append("```\n");
        sb.append("METHOD\\nrealPath\\ntimestamp\\nnonce\\nbodySha256OrEmpty\n");
        sb.append("X-Yu-Signature = hex(HMAC_SHA256(appSecret, 签名串))\n");
        sb.append("```\n\n");
        sb.append("- `realPath`：去掉开放前缀后的真实发布路径（含前导 `/`）\n");
        if (yuFlowRuntimeSettings.resolveOpen().isIncludeBodyHash()) {
            sb.append("- `bodySha256OrEmpty`：请求体 UTF-8 字节的 SHA-256 十六进制；无 body 时用空串\n");
        } else {
            sb.append("- 当前环境 `includeBodyHash=false`，签名串末段固定为空串\n");
        }
        sb.append("- 时钟偏差允许 ±").append(yuFlowRuntimeSettings.resolveOpen().getSkewSeconds()).append(" 秒\n");
        sb.append("\n## 4. 授权与方法限制\n\n");
        sb.append("- 仅文档中列出的接口已授权给本平台\n");
        sb.append("- `allow_methods` 为空：跟随接口发布 method\n");
        sb.append("- `allow_methods` 显式配置（如 `GET,POST`）：请求方法必须命中，否则 `403 OPEN_AUTH_METHOD_DENIED`\n");
        sb.append("\n## 5. 错误码\n\n");
        sb.append("| HTTP | code | 含义 |\n|---|---|---|\n");
        sb.append("| 401 | OPEN_AUTH_MISSING | 缺少凭证头 |\n");
        sb.append("| 401 | OPEN_AUTH_INVALID | 签名/密钥错误 |\n");
        sb.append("| 401 | OPEN_AUTH_EXPIRED | 时间窗/凭证/nonce 过期 |\n");
        sb.append("| 403 | OPEN_AUTH_DENIED | 平台停用或未授权接口 |\n");
        sb.append("| 403 | OPEN_AUTH_IP_DENIED | IP 不在白名单 |\n");
        sb.append("| 403 | OPEN_AUTH_METHOD_DENIED | 授权未开放该 HTTP 方法 |\n");
        sb.append("| 401 | OPEN_HOST_AUTH_REQUIRED | 要求宿主登录 |\n");
        sb.append("| 429 | OPEN_RATE_LIMITED | 限流（若启用） |\n\n");
        sb.append("失败响应含字段 `errorCode`（机器可读）与 `msg`。\n");
        return sb.toString();
    }

    private Map<String, String> loadAllowMethods(String platformId) {
        Map<String, String> map = new HashMap<>();
        List<OpenGrantItemDTO> grants = flowOpenPlatformService.listGrantDetails(platformId);
        if (grants == null) {
            return map;
        }
        for (OpenGrantItemDTO g : grants) {
            if (g == null || StrUtil.isBlank(g.getApiId())) {
                continue;
            }
            map.put(g.getApiId(), StrUtil.nullToEmpty(g.getAllowMethods()));
        }
        return map;
    }

    private void applyOpenSecuritySchemes(ObjectNode root) {
        ObjectNode components = root.has("components") && root.get("components").isObject()
                ? (ObjectNode) root.get("components")
                : objectMapper.createObjectNode();
        ObjectNode schemes = objectMapper.createObjectNode();
        schemes.set("YuOpenAppKey", headerApiKey("X-Yu-App-Key",
                "开放平台 AppKey"));
        schemes.set("YuOpenTimestamp", headerApiKey("X-Yu-Timestamp",
                "Unix 秒级时间戳"));
        schemes.set("YuOpenNonce", headerApiKey("X-Yu-Nonce",
                "随机串，时钟窗口内不可重复"));
        schemes.set("YuOpenSignature", headerApiKey("X-Yu-Signature",
                "HMAC-SHA256(appSecret, METHOD\\nrealPath\\ntimestamp\\nnonce\\nbodySha256OrEmpty) 十六进制"));
        if (yuFlowRuntimeSettings.resolveOpen().isAllowPlainSecret()) {
            schemes.set("YuOpenAppSecret", headerApiKey("X-Yu-App-Secret",
                    "明文 Secret（仅无 Signature 时可用；非生产推荐）"));
        }
        components.set("securitySchemes", schemes);
        root.set("components", components);

        ArrayNode security = objectMapper.createArrayNode();
        ObjectNode secReq = objectMapper.createObjectNode();
        secReq.set("YuOpenAppKey", objectMapper.createArrayNode());
        secReq.set("YuOpenTimestamp", objectMapper.createArrayNode());
        secReq.set("YuOpenNonce", objectMapper.createArrayNode());
        secReq.set("YuOpenSignature", objectMapper.createArrayNode());
        security.add(secReq);
        root.set("security", security);
    }

    private ObjectNode headerApiKey(String name, String description) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("type", "apiKey");
        n.put("in", "header");
        n.put("name", name);
        n.put("description", description);
        return n;
    }

    private static String appendAllowMethodsNote(String existing, String allowMethods) {
        String note = "授权允许的 HTTP 方法：`" + allowMethods + "`。";
        if (StrUtil.isBlank(existing)) {
            return note;
        }
        return existing.trim() + "\n\n" + note;
    }

    private String stripOpenPrefix(String openPath) {
        String prefix = entryPrefix();
        if (openPath != null && openPath.startsWith(prefix)) {
            return openPath.substring(prefix.length());
        }
        return openPath;
    }

    private String serverBase(HttpServletRequest request) {
        if (request == null) return "";
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (("http".equals(request.getScheme()) && port != 80)
                || ("https".equals(request.getScheme()) && port != 443)) {
            url.append(":").append(port);
        }
        if (StrUtil.isNotBlank(request.getContextPath())) {
            url.append(request.getContextPath());
        }
        return url.toString();
    }

    private void rewriteServers(ObjectNode root, HttpServletRequest request) {
        if (request == null) return;
        ArrayNode servers = objectMapper.createArrayNode();
        ObjectNode server = objectMapper.createObjectNode();
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        if (("http".equals(request.getScheme()) && port != 80)
                || ("https".equals(request.getScheme()) && port != 443)) {
            url.append(":").append(port);
        }
        if (StrUtil.isNotBlank(request.getContextPath())) {
            url.append(request.getContextPath());
        }
        server.put("url", url.toString());
        server.put("description", "开放调用请使用路径前缀 " + entryPrefix());
        servers.add(server);
        root.set("servers", servers);
    }

    private String entryPrefix() {
        String p = yuFlowRuntimeSettings.resolveOpen().getEntryPrefix();
        if (StrUtil.isBlank(p)) return "/flow-api/open";
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }
}
