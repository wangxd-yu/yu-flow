package org.yu.flow.module.serviceflow.service;

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
 * 检查内部服务是否仍被其他流程的 api 节点引用（targetType=service）。
 */
@Slf4j
@Component
public class ServiceFlowReferenceChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    /**
     * 若仍被引用则抛出 RuntimeException，阻止删除。
     */
    public void assertDeletable(String serviceId) {
        List<String> refs = findReferenceLabels(serviceId);
        if (!refs.isEmpty()) {
            throw new RuntimeException(
                    "无法删除：内部服务仍被以下流程引用 — " + String.join("；", refs));
        }
    }

    /**
     * @return 可读引用标签，如「接口 API「xxx」」「定时任务「yyy」」「内部服务「zzz」」
     */
    public List<String> findReferenceLabels(String serviceId) {
        if (StrUtil.isBlank(serviceId)) {
            return List.of();
        }
        Set<String> labels = new LinkedHashSet<>();

        for (FlowApiDO api : flowApiRepository.findPossibleServiceFlowRefs(serviceId)) {
            if (contentReferencesService(api.getDslContent(), serviceId)
                    || contentReferencesService(api.getPublishedSnapshot(), serviceId)) {
                labels.add("接口「" + displayName(api.getName(), api.getId()) + "」");
            }
        }

        for (FlowTaskDO task : flowTaskRepository.findPossibleServiceFlowRefs(serviceId)) {
            if (contentReferencesService(task.getDslContent(), serviceId)
                    || contentReferencesService(task.getPublishedSnapshot(), serviceId)) {
                labels.add("定时任务「" + displayName(task.getName(), task.getId()) + "」");
            }
        }

        for (FlowServiceFlowDO svc : flowServiceFlowRepository.findPossibleServiceFlowRefs(serviceId)) {
            if (serviceId.equals(svc.getId())) {
                continue; // 自身草稿里的 id 不算引用
            }
            if (contentReferencesService(svc.getDslContent(), serviceId)
                    || contentReferencesService(svc.getPublishedSnapshot(), serviceId)) {
                labels.add("内部服务「" + displayName(svc.getName(), svc.getId()) + "」");
            }
        }

        return new ArrayList<>(labels);
    }

    private boolean contentReferencesService(String content, String serviceId) {
        if (StrUtil.isBlank(content) || !content.contains(serviceId)) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(content);
            // 发布快照：{ dslContent, contract, ... }
            if (root.has("dslContent") && root.get("dslContent").isTextual()) {
                String nested = root.get("dslContent").asText();
                if (dslDocumentReferences(nested, serviceId)) {
                    return true;
                }
            }
            return dslRootReferences(root, serviceId);
        } catch (Exception e) {
            log.debug("[ServiceFlowRef] 解析 DSL 失败，跳过候选: {}", e.getMessage());
            return false;
        }
    }

    private boolean dslDocumentReferences(String dsl, String serviceId) {
        if (StrUtil.isBlank(dsl) || !dsl.contains(serviceId)) {
            return false;
        }
        try {
            return dslRootReferences(MAPPER.readTree(dsl), serviceId);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean dslRootReferences(JsonNode root, String serviceId) {
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
            if (nodeReferencesService(node, serviceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean nodeReferencesService(JsonNode node, String serviceId) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (!"api".equals(text(node, "type"))) {
            return false;
        }
        // 画布格式字段在 data；引擎 steps 可能 flatten 到根
        JsonNode data = node.has("data") && node.get("data").isObject()
                ? node.get("data")
                : node;
        if (!"service".equalsIgnoreCase(text(data, "targetType"))) {
            return false;
        }
        return serviceId.equals(text(data, "serviceId"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String displayName(String name, String id) {
        return StrUtil.isNotBlank(name) ? name : id;
    }
}
