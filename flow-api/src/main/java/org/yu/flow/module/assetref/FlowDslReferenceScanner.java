package org.yu.flow.module.assetref;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 DSL / 发布快照中扫描 api 节点对外引用（targetType=api|service）。
 */
@Slf4j
public final class FlowDslReferenceScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FlowDslReferenceScanner() {
    }

    public record OutboundRef(String targetType, String targetId) {
        public String cacheKey() {
            return targetType + ":" + targetId;
        }
    }

    /**
     * @param content 草稿 DSL 或 publishedSnapshot JSON
     */
    public static List<OutboundRef> scan(String content) {
        if (StrUtil.isBlank(content) || !content.contains("serviceId")) {
            return List.of();
        }
        List<OutboundRef> out = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(content);
            if (root.has("dslContent") && root.get("dslContent").isTextual()) {
                scanDslRoot(MAPPER.readTree(root.get("dslContent").asText()), out);
            }
            scanDslRoot(root, out);
        } catch (Exception e) {
            log.debug("[FlowDslRefScan] 解析失败: {}", e.getMessage());
        }
        return out;
    }

    private static void scanDslRoot(JsonNode root, List<OutboundRef> out) {
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode arr = root.get("nodes");
        if (arr == null || !arr.isArray()) {
            arr = root.get("steps");
        }
        if (arr == null || !arr.isArray()) {
            return;
        }
        for (JsonNode node : arr) {
            if (node == null || !node.isObject()) {
                continue;
            }
            if (!"api".equals(text(node, "type"))) {
                continue;
            }
            JsonNode data = node.has("data") && node.get("data").isObject()
                    ? node.get("data") : node;
            String serviceId = text(data, "serviceId");
            if (StrUtil.isBlank(serviceId)) {
                continue;
            }
            String targetType = text(data, "targetType");
            if (StrUtil.isBlank(targetType) || "api".equalsIgnoreCase(targetType)) {
                out.add(new OutboundRef("api", serviceId));
            } else if ("service".equalsIgnoreCase(targetType)) {
                out.add(new OutboundRef("service", serviceId));
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
