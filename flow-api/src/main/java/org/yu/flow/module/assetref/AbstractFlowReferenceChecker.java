package org.yu.flow.module.assetref;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 资产引用检查基类：检查某资产是否仍被其他流程的 api 节点引用。
 * <p>优先走 {@link FlowReferenceIndex}；索引未就绪时降级 LIKE 粗筛，
 * 扫描接口 / 定时任务 / 内部服务三类资产的 DSL 与发布快照。</p>
 * <p>子类只需声明 DSL 引用的 targetType、索引查询方法与错误文案名词。</p>
 */
public abstract class AbstractFlowReferenceChecker {

    /** 绑定到具体子类的 logger，warn 输出与原实现保持同名 logger */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    protected FlowApiRepository flowApiRepository;

    @Resource
    protected FlowTaskRepository flowTaskRepository;

    @Resource
    protected FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    protected FlowReferenceIndex flowReferenceIndex;

    /** DSL 引用的 targetType："api" 或 "service"，同时决定降级扫描时的自引用排除 */
    protected abstract String targetType();

    /** 索引就绪时的标签查询（如 findApiReferenceLabels / findServiceReferenceLabels） */
    protected abstract List<String> findIndexedLabels(String targetId);

    /** 删除拦截文案中的资产名词，如「接口」「内部服务」 */
    protected abstract String assetNoun();

    public void assertDeletable(String targetId) {
        List<String> refs = findReferenceLabels(targetId);
        if (!refs.isEmpty()) {
            throw new RuntimeException(
                    "无法删除：" + assetNoun() + "仍被以下流程引用 — " + String.join("；", refs));
        }
    }

    public List<String> findReferenceLabels(String targetId) {
        if (StrUtil.isBlank(targetId)) {
            return List.of();
        }
        try {
            flowReferenceIndex.ensureReady();
            if (flowReferenceIndex.isReady()) {
                return findIndexedLabels(targetId);
            }
        } catch (Exception e) {
            log.warn("[{}] 索引未就绪，降级 LIKE: {}", getClass().getSimpleName(), e.getMessage());
        }
        return findReferenceLabelsFallback(targetId);
    }

    private List<String> findReferenceLabelsFallback(String targetId) {
        Set<String> labels = new LinkedHashSet<>();

        for (FlowApiDO api : flowApiRepository.findPossibleServiceFlowRefs(targetId)) {
            if ("api".equals(targetType()) && targetId.equals(api.getId())) {
                continue;
            }
            if (contentReferences(api.getDslContent(), targetId)
                    || contentReferences(api.getPublishedSnapshot(), targetId)) {
                labels.add("接口「" + displayName(api.getName(), api.getId()) + "」");
            }
        }

        for (FlowTaskDO task : flowTaskRepository.findPossibleServiceFlowRefs(targetId)) {
            if (contentReferences(task.getDslContent(), targetId)
                    || contentReferences(task.getPublishedSnapshot(), targetId)) {
                labels.add("定时任务「" + displayName(task.getName(), task.getId()) + "」");
            }
        }

        for (FlowServiceFlowDO svc : flowServiceFlowRepository.findPossibleServiceFlowRefs(targetId)) {
            if ("service".equals(targetType()) && targetId.equals(svc.getId())) {
                continue;
            }
            if (contentReferences(svc.getDslContent(), targetId)
                    || contentReferences(svc.getPublishedSnapshot(), targetId)) {
                labels.add("内部服务「" + displayName(svc.getName(), svc.getId()) + "」");
            }
        }

        return new ArrayList<>(labels);
    }

    private boolean contentReferences(String content, String targetId) {
        if (StrUtil.isBlank(content) || !content.contains(targetId)) {
            return false;
        }
        for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(content)) {
            if (targetType().equals(ref.targetType()) && targetId.equals(ref.targetId())) {
                return true;
            }
        }
        return false;
    }

    private static String displayName(String name, String id) {
        return StrUtil.isNotBlank(name) ? name : id;
    }
}
