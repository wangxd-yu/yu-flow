package org.yu.flow.module.assetref;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流程资产引用倒排索引（L1 本地 + Redis Pub/Sub 集群同步）。
 *
 * <p>key = {@code api:id} / {@code service:id} → 引用方标签集合。
 * 写路径在事务提交后更新本节点并广播；读路径 O(1) 查索引，避免全表 LIKE + 解析。</p>
 */
@Slf4j
@Component
public class FlowReferenceIndex {

    public static final String REFRESH_TOPIC = "flow:asset:ref:refresh:topic";

    public enum SourceKind { API, TASK, SERVICE }

    public record SourceRef(SourceKind kind, String id, String name) {
        public String label() {
            String display = StrUtil.isNotBlank(name) ? name : id;
            return switch (kind) {
                case API -> "接口「" + display + "」";
                case TASK -> "定时任务「" + display + "」";
                case SERVICE -> "内部服务「" + display + "」";
            };
        }

        public String sourceKey() {
            return kind.name() + ":" + id;
        }
    }

    /** targetKey → sources */
    private final ConcurrentHashMap<String, Set<SourceRef>> reverse = new ConcurrentHashMap<>();
    /** sourceKey → targetKeys（便于移除旧边） */
    private final ConcurrentHashMap<String, Set<String>> forward = new ConcurrentHashMap<>();

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowTaskRepository flowTaskRepository;
    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    @Async("flowAsyncExecutor")
    public void warmUp() {
        ensureReady();
    }

    public boolean isReady() {
        return ready.get();
    }

    /**
     * 若尚未就绪则同步重建一次（与异步 warmUp 共用锁，避免启动窗口内删除走 LIKE 全表扫）。
     */
    public void ensureReady() {
        if (ready.get()) {
            return;
        }
        rebuildAll();
    }

    public List<String> findServiceReferenceLabels(String serviceId) {
        return labelsOf("service:" + serviceId);
    }

    public List<String> findApiReferenceLabels(String apiId) {
        return labelsOf("api:" + apiId);
    }

    private List<String> labelsOf(String targetKey) {
        Set<SourceRef> set = reverse.get(targetKey);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        Set<String> labels = new LinkedHashSet<>();
        for (SourceRef ref : set) {
            labels.add(ref.label());
        }
        return new ArrayList<>(labels);
    }

    public synchronized void rebuildAll() {
        reverse.clear();
        forward.clear();
        int n = 0;
        for (FlowApiDO api : flowApiRepository.findAll()) {
            if (api.getDeleted() != null && api.getDeleted() == 1) {
                continue;
            }
            indexSource(SourceKind.API, api.getId(), api.getName(), api.getDslContent(), api.getPublishedSnapshot());
            n++;
        }
        for (FlowTaskDO task : flowTaskRepository.findAll()) {
            if (task.getDeleted() != null && task.getDeleted() == 1) {
                continue;
            }
            indexSource(SourceKind.TASK, task.getId(), task.getName(), task.getDslContent(), task.getPublishedSnapshot());
            n++;
        }
        for (FlowServiceFlowDO svc : flowServiceFlowRepository.findAll()) {
            if (svc.getDeleted() != null && svc.getDeleted() == 1) {
                continue;
            }
            indexSource(SourceKind.SERVICE, svc.getId(), svc.getName(), svc.getDslContent(), svc.getPublishedSnapshot());
            n++;
        }
        ready.set(true);
        log.info("[FlowReferenceIndex] 全量重建完成: assets={}, reverseKeys={}", n, reverse.size());
    }

    public void reindexApi(FlowApiDO api) {
        if (api == null || StrUtil.isBlank(api.getId())) {
            return;
        }
        indexSource(SourceKind.API, api.getId(), api.getName(), api.getDslContent(), api.getPublishedSnapshot());
    }

    public void reindexTask(FlowTaskDO task) {
        if (task == null || StrUtil.isBlank(task.getId())) {
            return;
        }
        indexSource(SourceKind.TASK, task.getId(), task.getName(), task.getDslContent(), task.getPublishedSnapshot());
    }

    public void reindexService(FlowServiceFlowDO svc) {
        if (svc == null || StrUtil.isBlank(svc.getId())) {
            return;
        }
        indexSource(SourceKind.SERVICE, svc.getId(), svc.getName(), svc.getDslContent(), svc.getPublishedSnapshot());
    }

    public void removeSource(SourceKind kind, String id) {
        if (StrUtil.isBlank(id)) {
            return;
        }
        removeForwardEdges(kind.name() + ":" + id);
    }

    private void indexSource(SourceKind kind, String id, String name, String dsl, String snapshot) {
        String sourceKey = kind.name() + ":" + id;
        removeForwardEdges(sourceKey);

        Set<String> targets = ConcurrentHashMap.newKeySet();
        SourceRef source = new SourceRef(kind, id, name);
        for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(dsl)) {
            addEdge(source, ref.cacheKey(), targets);
        }
        for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(snapshot)) {
            addEdge(source, ref.cacheKey(), targets);
        }
        if (!targets.isEmpty()) {
            forward.put(sourceKey, targets);
        }
    }

    private void addEdge(SourceRef source, String targetKey, Set<String> targets) {
        // 自身不算引用
        if (("service:" + source.id()).equals(targetKey) && source.kind() == SourceKind.SERVICE) {
            return;
        }
        if (("api:" + source.id()).equals(targetKey) && source.kind() == SourceKind.API) {
            return;
        }
        targets.add(targetKey);
        reverse.computeIfAbsent(targetKey, k -> ConcurrentHashMap.newKeySet()).add(source);
    }

    private void removeForwardEdges(String sourceKey) {
        Set<String> oldTargets = forward.remove(sourceKey);
        if (oldTargets == null) {
            return;
        }
        for (String target : oldTargets) {
            Set<SourceRef> set = reverse.get(target);
            if (set != null) {
                set.removeIf(s -> sourceKey.equals(s.sourceKey()));
                if (set.isEmpty()) {
                    reverse.remove(target, set);
                }
            }
        }
    }

    /** 事务提交后本节点全量重建并广播（DSL 变更后调用） */
    public void scheduleRebuildBroadcastAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rebuildAll();
                    publishRefreshEvent();
                }
            });
        } else {
            rebuildAll();
            publishRefreshEvent();
        }
    }

    public void publishRefreshEvent() {
        try {
            stringRedisTemplate.convertAndSend(REFRESH_TOPIC, "REFRESH_ALL");
        } catch (Exception e) {
            log.warn("[FlowReferenceIndex] 发布刷新事件失败: {}", e.getMessage());
        }
    }

    /** 收到集群广播：全量重建（不二次广播） */
    public void onRemoteRefresh() {
        log.info("[FlowReferenceIndex] 收到集群刷新，开始重建");
        rebuildAll();
    }
}
