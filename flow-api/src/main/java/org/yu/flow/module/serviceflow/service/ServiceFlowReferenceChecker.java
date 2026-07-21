package org.yu.flow.module.serviceflow.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.assetref.FlowDslReferenceScanner;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检查内部服务是否仍被其他流程的 api 节点引用（targetType=service）。
 * <p>优先走 {@link FlowReferenceIndex}；索引未就绪时降级 LIKE 粗筛。</p>
 */
@Slf4j
@Component
public class ServiceFlowReferenceChecker {

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    private FlowReferenceIndex flowReferenceIndex;

    public void assertDeletable(String serviceId) {
        List<String> refs = findReferenceLabels(serviceId);
        if (!refs.isEmpty()) {
            throw new RuntimeException(
                    "无法删除：内部服务仍被以下流程引用 — " + String.join("；", refs));
        }
    }

    public List<String> findReferenceLabels(String serviceId) {
        if (StrUtil.isBlank(serviceId)) {
            return List.of();
        }
        if (flowReferenceIndex.isReady()) {
            return flowReferenceIndex.findServiceReferenceLabels(serviceId);
        }
        return findReferenceLabelsFallback(serviceId);
    }

    private List<String> findReferenceLabelsFallback(String serviceId) {
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
                continue;
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
        for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(content)) {
            if ("service".equals(ref.targetType()) && serviceId.equals(ref.targetId())) {
                return true;
            }
        }
        return false;
    }

    private static String displayName(String name, String id) {
        return StrUtil.isNotBlank(name) ? name : id;
    }
}
