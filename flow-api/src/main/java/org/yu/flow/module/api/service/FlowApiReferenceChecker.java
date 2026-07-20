package org.yu.flow.module.api.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检查 Flow API 是否仍被其他流程的 api 节点引用（targetType=api 或未指定）。
 */
@Slf4j
@Component
public class FlowApiReferenceChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    public void assertDeletable(String apiId) {
        List<String> refs = findReferenceLabels(apiId);
        if (!refs.isEmpty()) {
            throw new RuntimeException(
                    "无法删除：接口仍被以下流程引用 — " + String.join("；", refs));
        }
    }

    public List<String> findReferenceLabels(String apiId) {
        if (StrUtil.isBlank(apiId)) {
            return List.of();
        }
        Set<String> labels = new LinkedHashSet<>();

        for (FlowApiDO api : flowApiRepository.findPossibleServiceFlowRefs(apiId)) {
            if (apiId.equals(api.getId())) {
                continue;
            }
            if (contentReferencesApi(api.getDslContent(), apiId)
                    || contentReferencesApi(api.getPublishedSnapshot(), apiId)) {
                labels.add("接口「" + displayName(api.getName(), api.getId()) + "」");
            }
        }

        for (FlowTaskDO task : flowTaskRepository.findPossibleServiceFlowRefs(apiId)) {
            if (contentReferencesApi(task.getDslContent(), apiId)) {
                labels.add("定时任务「" + displayName(task.getName(), task.getId()) + "」");
            }
        }

        for (FlowServiceFlowDO svc : flowServiceFlowRepository.findPossibleServiceFlowRefs(apiId)) {
            if (contentReferencesApi(svc.getDslContent(), apiId)
                    || contentReferencesApi(svc.getPublishedSnapshot(), apiId)) {
                labels.add("内部服务「" + displayName(svc.getName(), svc.getId()) + "」");
            }
        }

        return new ArrayList<>(labels);
    }

    private boolean contentReferencesApi(String content, String apiId) {
        if (StrUtil.isBlank(content) || !content.contains(apiId)) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(content);
            if (root.has("dslContent") && root.get("dslContent").isTextual()) {
                String nested = root.get("dslContent").asText();
                if (dslDocumentReferences(nested, apiId)) {
                    return true;
                }
            }
            return dslRootReferences(root, apiId);
        } catch (Exception e) {
            log.debug("[ApiRef] 解析 DSL 失败，跳过候选: {}", e.getMessage());
            return false;
        }
    }

    private boolean dslDocumentReferences(String dsl, String apiId) {
        if (StrUtil.isBlank(dsl) || !dsl.contains(apiId)) {
            return false;
        }
        try {
            return dslRootReferences(MAPPER.readTree(dsl), apiId);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean dslRootReferences(JsonNode root, String apiId) {
        if (root == null || !root.isObject()) {
            return false;
        }
        JsonNode arr = root.get("nodes");
        if (arr == null || !arr.isArray()) {
            arr = root.get("steps");
        }
        if (arr == null || !arr.isArray()) {
            return false;
        }
        for (JsonNode node : arr) {
            if (nodeReferencesApi(node, apiId)) {
                return true;
            }
        }
        return false;
    }

    private boolean nodeReferencesApi(JsonNode node, String apiId) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (!"api".equals(text(node, "type"))) {
            return false;
        }
        JsonNode data = node.has("data") && node.get("data").isObject()
                ? node.get("data")
                : node;
        String targetType = text(data, "targetType");
        // 默认 / api → 调接口；service → 调内部服务（不算本检查）
        if (StrUtil.isNotBlank(targetType) && !"api".equalsIgnoreCase(targetType)) {
            return false;
        }
        return apiId.equals(text(data, "serviceId"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String displayName(String name, String id) {
        return StrUtil.isNotBlank(name) ? name : id;
    }
}
