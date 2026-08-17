package org.yu.flow.module.transfer.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 扫描 DSL，收集资产对目标环境外部资源的引用。
 *
 * <p>这些引用都是 code（数据源 code、MQ / OSS 连接 code），跨环境天然可用，
 * 但目标环境必须已经建好同 code 的资源，否则接口一跑就报「数据源未找到」。</p>
 */
@Slf4j
public final class BundleRequirementScanner {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    /** 递归深度上限，避免异常 DSL 拖垮导出 */
    private static final int MAX_DEPTH = 32;

    private BundleRequirementScanner() {
    }

    /**
     * @param acc 以 kind + key 聚合的收集器，跨多个资产累加
     */
    public static void scanDsl(String dsl, String assetName, Map<String, TransferRequirementDTO> acc) {
        if (StrUtil.isBlank(dsl)) {
            return;
        }
        try {
            walk(MAPPER.readTree(dsl), assetName, acc, 0);
        } catch (Exception e) {
            log.debug("[AssetTransfer] DSL 依赖扫描失败: {}", e.getMessage());
        }
    }

    public static void add(Map<String, TransferRequirementDTO> acc, String kind, String key, String assetName) {
        if (StrUtil.isBlank(key) || key.contains("${")) {
            // 表达式求值出来的 code 无法静态确定，跳过
            return;
        }
        TransferRequirementDTO req = acc.computeIfAbsent(kind + ":" + key, k -> {
            TransferRequirementDTO dto = new TransferRequirementDTO();
            dto.setKind(kind);
            dto.setKey(key);
            return dto;
        });
        if (StrUtil.isNotBlank(assetName) && !req.getUsedBy().contains(assetName)) {
            req.getUsedBy().add(assetName);
        }
    }

    private static void walk(JsonNode node, String assetName, Map<String, TransferRequirementDTO> acc, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> walk(child, assetName, acc, depth + 1));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        collectFromStep(node, assetName, acc);
        node.fields().forEachRemaining(entry -> walk(entry.getValue(), assetName, acc, depth + 1));
    }

    /** 画布格式把配置放在 node.data 下，引擎格式直接平铺在节点上 */
    private static void collectFromStep(JsonNode node, String assetName, Map<String, TransferRequirementDTO> acc) {
        String type = text(node, "type");
        if (StrUtil.isBlank(type)) {
            return;
        }
        JsonNode data = node.has("data") && node.get("data").isObject() ? node.get("data") : node;
        switch (type) {
            case "database" -> add(acc, TransferRequirementDTO.KIND_DATASOURCE, text(data, "datasourceId"), assetName);
            case "mqSend", "mqTrigger" -> add(acc, TransferRequirementDTO.KIND_MQ, text(data, "connectionCode"), assetName);
            case "oss" -> add(acc, TransferRequirementDTO.KIND_OSS, text(data, "connectionCode"), assetName);
            default -> {
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
