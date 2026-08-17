package org.yu.flow.module.mqtask.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.mq.provider.MqConnectionSpec;
import org.yu.flow.module.mq.provider.MqProvider;
import org.yu.flow.module.mq.provider.MqProviderRegistry;
import org.yu.flow.module.mq.service.MqConnectionService;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQ 任务积压（Kafka lag / Rabbit 队列深度）轻量查询，带内存缓存。
 */
@Slf4j
@Service
public class MqConsumerBacklogService {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;

    @Resource
    private MqConnectionService mqConnectionService;

    @Resource
    private MqProviderRegistry mqProviderRegistry;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private TaskScheduler taskScheduler;

    private volatile Map<String, Long> cachedBacklog = Map.of();
    private volatile long cacheExpiresAtMs = 0L;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * taskId -> backlog（null 表示未知/不支持，未发布任务不在结果中）。
     *
     * <p>采集要逐个连接问 broker，慢且不稳定，因此只在首次调用时同步采集；
     * 之后缓存过期一律先返回旧值，再后台刷新，管理页列表不会被 broker 的响应时间拖住。</p>
     */
    public Map<String, Long> backlogByTaskId() {
        long now = System.currentTimeMillis();
        if (now < cacheExpiresAtMs) {
            return cachedBacklog;
        }
        if (cacheExpiresAtMs == 0L) {
            synchronized (this) {
                if (cacheExpiresAtMs == 0L) {
                    publish(collectBacklog());
                }
            }
            return cachedBacklog;
        }
        triggerAsyncRefresh();
        return cachedBacklog;
    }

    private void triggerAsyncRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            taskScheduler.schedule(() -> {
                try {
                    publish(collectBacklog());
                } catch (Exception e) {
                    // 刷新失败保留旧值，并顺延一个周期，避免每次请求都触发重试风暴
                    log.debug("[MqConsumerBacklogService] 后台刷新失败: {}", e.getMessage());
                    cacheExpiresAtMs = System.currentTimeMillis() + cacheTtlMs();
                } finally {
                    refreshing.set(false);
                }
            }, Instant.now());
        } catch (Exception e) {
            refreshing.set(false);
            log.debug("[MqConsumerBacklogService] 无法提交后台刷新任务: {}", e.getMessage());
        }
    }

    private void publish(Map<String, Long> fresh) {
        cachedBacklog = fresh;
        cacheExpiresAtMs = System.currentTimeMillis() + cacheTtlMs();
    }

    private long cacheTtlMs() {
        return Math.max(1, yuFlowProperties.getMq().getBacklogCacheSeconds()) * 1000L;
    }

    private Map<String, Long> collectBacklog() {
        List<Object[]> rows = flowMqTaskRepository.findPublishedSubscribeTargets();
        Map<String, Long> result = new HashMap<>();
        // 多个任务常订阅同一 topic/消费组，按订阅坐标去重后每个坐标只问 broker 一次
        Map<Target, Long> probed = new HashMap<>();
        for (Object[] row : rows) {
            String taskId = (String) row[0];
            if (StrUtil.isBlank(taskId)) {
                continue;
            }
            Target target = resolveTarget(row);
            if (StrUtil.isBlank(target.connectionCode()) || StrUtil.isBlank(target.topic())) {
                result.put(taskId, null);
                continue;
            }
            // 探测失败记 null，这里必须显式判 key 是否存在，否则同一坐标会被反复重试
            if (!probed.containsKey(target)) {
                probed.put(target, estimate(target));
            }
            result.put(taskId, probed.get(target));
        }
        return result;
    }

    private Long estimate(Target target) {
        try {
            MqConnectionSpec spec = mqConnectionService.buildSpec(target.connectionCode());
            MqProvider provider = mqProviderRegistry.getProvider(spec.getMqType());
            return provider.estimateBacklog(spec, target.topic(), target.consumerGroup());
        } catch (Exception e) {
            log.debug("[MqConsumerBacklogService] backlog failed topic={}: {}", target.topic(), e.getMessage());
            return null;
        }
    }

    /** 订阅坐标：积压按已发布快照统计，快照缺字段时回落到草稿配置 */
    private record Target(String connectionCode, String topic, String consumerGroup) {}

    private static Target resolveTarget(Object[] row) {
        String connectionCode = (String) row[1];
        String topic = (String) row[2];
        String consumerGroup = (String) row[3];
        String snapshotJson = (String) row[4];
        if (StrUtil.isBlank(snapshotJson)) {
            return new Target(connectionCode, topic, consumerGroup);
        }
        try {
            JsonNode snap = MAPPER.readTree(snapshotJson);
            String cc = textOrNull(snap, "connectionCode");
            String tp = textOrNull(snap, "topic");
            String cg = textOrNull(snap, "consumerGroup");
            return new Target(
                    StrUtil.isBlank(cc) ? connectionCode : cc,
                    StrUtil.isBlank(tp) ? topic : tp,
                    cg == null ? consumerGroup : cg);
        } catch (Exception e) {
            return new Target(connectionCode, topic, consumerGroup);
        }
    }

    private static String textOrNull(JsonNode snap, String field) {
        JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }
}
