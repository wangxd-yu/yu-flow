package org.yu.flow.module.mqtask.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.mq.provider.MqConnectionSpec;
import org.yu.flow.module.mq.provider.MqProvider;
import org.yu.flow.module.mq.provider.MqProviderRegistry;
import org.yu.flow.module.mq.service.MqConnectionService;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private volatile Map<String, Long> cachedBacklog = Map.of();
    private volatile long cacheExpiresAtMs = 0L;

    /** taskId -> backlog（null 表示未知/不支持） */
    public Map<String, Long> backlogByTaskId() {
        long now = System.currentTimeMillis();
        int cacheSeconds = Math.max(1, yuFlowProperties.getMq().getBacklogCacheSeconds());
        if (now < cacheExpiresAtMs && !cachedBacklog.isEmpty()) {
            return cachedBacklog;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now < cacheExpiresAtMs && !cachedBacklog.isEmpty()) {
                return cachedBacklog;
            }
            Map<String, Long> fresh = collectBacklog();
            cachedBacklog = fresh;
            cacheExpiresAtMs = now + cacheSeconds * 1000L;
            return fresh;
        }
    }

    private Map<String, Long> collectBacklog() {
        List<FlowMqTaskDO> tasks = flowMqTaskRepository.findAll();
        Map<String, Long> result = new HashMap<>();
        for (FlowMqTaskDO task : tasks) {
            if (task == null || StrUtil.isBlank(task.getId())) {
                continue;
            }
            if (task.getPublishStatus() == null || task.getPublishStatus() != 1
                    || StrUtil.isBlank(task.getPublishedSnapshot())) {
                result.put(task.getId(), null);
                continue;
            }
            Snapshot snap = resolveSnapshot(task);
            if (StrUtil.isBlank(snap.connectionCode()) || StrUtil.isBlank(snap.topic())) {
                result.put(task.getId(), null);
                continue;
            }
            try {
                MqConnectionSpec spec = mqConnectionService.buildSpec(snap.connectionCode());
                MqProvider provider = mqProviderRegistry.getProvider(spec.getMqType());
                Long backlog = provider.estimateBacklog(spec, snap.topic(), snap.consumerGroup());
                result.put(task.getId(), backlog);
            } catch (Exception e) {
                log.debug("[MqConsumerBacklogService] backlog failed taskId={}: {}", task.getId(), e.getMessage());
                result.put(task.getId(), null);
            }
        }
        return result;
    }

    private record Snapshot(String connectionCode, String topic, String consumerGroup) {}

    private static Snapshot resolveSnapshot(FlowMqTaskDO task) {
        String connectionCode = task.getConnectionCode();
        String topic = task.getTopic();
        String consumerGroup = task.getConsumerGroup();
        if (StrUtil.isBlank(task.getPublishedSnapshot())) {
            return new Snapshot(connectionCode, topic, consumerGroup);
        }
        try {
            JsonNode snap = MAPPER.readTree(task.getPublishedSnapshot());
            String cc = textOrNull(snap, "connectionCode");
            String tp = textOrNull(snap, "topic");
            String cg = textOrNull(snap, "consumerGroup");
            if (StrUtil.isBlank(cc)) {
                cc = connectionCode;
            }
            if (StrUtil.isBlank(tp)) {
                tp = topic;
            }
            if (cg == null) {
                cg = consumerGroup;
            }
            return new Snapshot(cc, tp, cg);
        } catch (Exception e) {
            return new Snapshot(connectionCode, topic, consumerGroup);
        }
    }

    private static String textOrNull(JsonNode snap, String field) {
        JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }
}
