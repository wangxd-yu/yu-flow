package org.yu.flow.module.api.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.assetref.FlowDslReferenceScanner;
import org.yu.flow.module.assetref.FlowReferenceIndex;
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
 * <p>优先走 {@link FlowReferenceIndex}；索引未就绪时降级 LIKE 粗筛。</p>
 */
@Slf4j
@Component
public class FlowApiReferenceChecker {

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    private FlowReferenceIndex flowReferenceIndex;

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
        try {
            flowReferenceIndex.ensureReady();
            if (flowReferenceIndex.isReady()) {
                return flowReferenceIndex.findApiReferenceLabels(apiId);
            }
        } catch (Exception e) {
            log.warn("[FlowApiReferenceChecker] 索引未就绪，降级 LIKE: {}", e.getMessage());
        }
        return findReferenceLabelsFallback(apiId);
    }

    private List<String> findReferenceLabelsFallback(String apiId) {
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
            if (contentReferencesApi(task.getDslContent(), apiId)
                    || contentReferencesApi(task.getPublishedSnapshot(), apiId)) {
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
        for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(content)) {
            if ("api".equals(ref.targetType()) && apiId.equals(ref.targetId())) {
                return true;
            }
        }
        return false;
    }

    private static String displayName(String name, String id) {
        return StrUtil.isNotBlank(name) ? name : id;
    }
}
