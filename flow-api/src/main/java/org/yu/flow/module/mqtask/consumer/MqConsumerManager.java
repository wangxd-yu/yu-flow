package org.yu.flow.module.mqtask.consumer;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.evaluator.executor.MqTriggerStepExecutor;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.TracePersistUtil;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.mq.provider.MqConnectionSpec;
import org.yu.flow.module.mq.provider.MqMessage;
import org.yu.flow.module.mq.provider.MqProvider;
import org.yu.flow.module.mq.provider.MqProviderRegistry;
import org.yu.flow.module.mq.provider.MqSubscription;
import org.yu.flow.module.mq.service.MqConnectionService;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.domain.FlowMqTaskLogDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.mqtask.service.FlowMqTaskLogService;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MQ 消费者管理器
 *
 * <p>照 {@code FlowTaskScheduler} 模式：应用启动后加载所有 enabled=true 的 MQ 任务，
 * 按发布快照中的订阅配置向 Provider 注册消费者，收到消息后触发流程执行。
 *
 * <p>可靠性策略：
 * <ul>
 *   <li>毒消息防线：消费回调吞掉全部异常并记 FAILED 日志，不向 Provider 抛出（避免 requeue 死循环）；</li>
 *   <li>消息级幂等：Redis 锁 {@code flow:mq:dedup:{taskId}:{messageId}}，TTL 可配；
 *       Redis 不可用时降级放行（去重仅兜底，执行日志可追溯）；</li>
 *   <li>总开关 {@code yu-flow.mq.consumer-enabled}：多实例部署时仅一个实例开启即可。</li>
 * </ul>
 *
 * @author yu-flow
 */
@Slf4j
@Component
public class MqConsumerManager {

    /** 消息幂等锁 key 前缀 */
    private static final String DEDUP_KEY_PREFIX = "flow:mq:dedup:";

    /** 触发类型：消息触发 */
    private static final String TRIGGER_MQ = "MQ";

    /** 触发类型：手动模拟 */
    private static final String TRIGGER_MANUAL = "MANUAL";

    @Resource
    private FlowEngine flowEngine;

    @Resource
    private FlowMqTaskLogService flowMqTaskLogService;

    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;

    @Resource
    private MqConnectionService mqConnectionService;

    @Resource
    private MqProviderRegistry mqProviderRegistry;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private AssetMetricsRecorder assetMetricsRecorder;

    @Resource
    private TaskScheduler taskScheduler;

    private static final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();

    /** taskId -> 订阅句柄映射，用于取消订阅 */
    private final Map<String, MqSubscription> subscriptions = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // 启动加载
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 应用启动完毕后，加载所有 enabled=true 的 MQ 任务，注册消费订阅。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadAllTasks() {
        if (!yuFlowProperties.getMq().isConsumerEnabled()) {
            log.info("[MqConsumerManager] yu-flow.mq.consumer-enabled=false，跳过消费者启动");
            return;
        }
        List<FlowMqTaskDO> enabledTasks = flowMqTaskRepository.findByEnabled(true);
        log.info("[MqConsumerManager] 启动加载 MQ 任务，共 {} 个", enabledTasks.size());
        for (FlowMqTaskDO task : enabledTasks) {
            try {
                subscribe(task);
            } catch (Exception e) {
                log.error("[MqConsumerManager] 任务订阅失败，taskId={}, name={}, error={}",
                        task.getId(), task.getName(), e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        subscriptions.keySet().forEach(this::cancel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 订阅管理
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 注册一个任务的消费订阅。
     * 若已存在同 taskId 的订阅，先取消再重新订阅。
     * <p>订阅配置取自 {@code publishedSnapshot}，与运行 DSL 保持一致；草稿配置不影响线上订阅。
     *
     * @param task 任务定义
     */
    public void subscribe(FlowMqTaskDO task) {
        if (task == null || StrUtil.isBlank(task.getId())) {
            log.warn("[MqConsumerManager] 任务为空，跳过订阅");
            return;
        }
        if (!yuFlowProperties.getMq().isConsumerEnabled()) {
            log.info("[MqConsumerManager] 消费总开关关闭，跳过订阅: taskId={}", task.getId());
            return;
        }
        if (task.getPublishStatus() == null || task.getPublishStatus() != 1
                || StrUtil.isBlank(task.getPublishedSnapshot())) {
            log.info("[MqConsumerManager] 任务未发布，跳过订阅: taskId={}, name={}",
                    task.getId(), task.getName());
            cancel(task.getId());
            return;
        }
        PublishedSnapshot snap = resolvePublishedSnapshot(task);
        if (StrUtil.isBlank(snap.connectionCode()) || StrUtil.isBlank(snap.topic())) {
            log.warn("[MqConsumerManager] 发布快照缺少连接编码或 topic，跳过订阅: taskId={}", task.getId());
            cancel(task.getId());
            return;
        }
        // 幂等：先取消旧订阅
        cancel(task.getId());

        try {
            MqConnectionSpec spec = mqConnectionService.buildSpec(snap.connectionCode());
            MqProvider provider = mqProviderRegistry.getProvider(spec.getMqType());
            String taskId = task.getId();
            MqSubscription subscription = provider.subscribe(
                    spec, snap.topic(), snap.consumerGroup(), snap.concurrency(),
                    message -> onMessage(taskId, message));
            subscriptions.put(taskId, subscription);
            log.info("[MqConsumerManager] 任务已订阅: taskId={}, name={}, connection={}, topic={}, group={}, concurrency={}",
                    taskId, snap.name() != null ? snap.name() : task.getName(),
                    snap.connectionCode(), snap.topic(), snap.consumerGroup(), snap.concurrency());
        } catch (Exception e) {
            log.error("[MqConsumerManager] 任务订阅失败: taskId={}, topic={}, error={}",
                    task.getId(), snap.topic(), e.getMessage());
            throw e;
        }
    }

    /**
     * 取消任务订阅（停用或删除时调用）。
     *
     * @param taskId 任务ID
     */
    public void cancel(String taskId) {
        MqSubscription subscription = subscriptions.remove(taskId);
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception e) {
                log.warn("[MqConsumerManager] 关闭订阅异常: taskId={}, error={}", taskId, e.getMessage());
            }
            log.info("[MqConsumerManager] 任务订阅已取消: taskId={}", taskId);
        }
    }

    /**
     * 重新订阅（发布/回滚后调用）。
     *
     * @param task 更新后的任务定义
     */
    public void resubscribe(FlowMqTaskDO task) {
        cancel(task.getId());
        if (Boolean.TRUE.equals(task.getEnabled())) {
            subscribe(task);
        }
    }

    /** 订阅是否在运行（管理页展示消费状态） */
    public boolean isRunning(String taskId) {
        MqSubscription subscription = subscriptions.get(taskId);
        return subscription != null && subscription.isRunning();
    }

    /**
     * 当前存活的订阅任务 ID（管理页列表批量展示，避免逐行请求）。
     * <p>仅反映本节点内存中的订阅句柄状态。</p>
     */
    public Set<String> runningTaskIds() {
        Set<String> running = new HashSet<>();
        subscriptions.forEach((taskId, subscription) -> {
            if (subscription != null && subscription.isRunning()) {
                running.add(taskId);
            }
        });
        return running;
    }

    /**
     * 手动模拟一条消息触发（供 /simulate 接口调用），异步执行，不阻塞 HTTP 线程。
     * <p>走已发布快照，与真实消费链路一致；结果在任务日志中查看。
     *
     * @param task    任务定义
     * @param message 模拟消息体
     */
    public void simulate(FlowMqTaskDO task, String message) {
        if (task == null || StrUtil.isBlank(task.getId())) {
            return;
        }
        PublishedSnapshot snap = resolvePublishedSnapshot(task);
        MqMessage mqMessage = MqMessage.builder()
                .messageId("manual-" + UUID.randomUUID())
                .topic(snap.topic())
                .body(message)
                .headers(new HashMap<>())
                .build();
        String taskId = task.getId();
        taskScheduler.schedule(() -> {
            FlowMqTaskDO latestTask = flowMqTaskRepository.findById(taskId).orElse(null);
            if (latestTask == null) {
                return;
            }
            executeTask(latestTask, mqMessage, TRIGGER_MANUAL);
        }, java.time.Instant.now());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 消费执行
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 消费回调：吞掉全部异常，不向 Provider 抛出（毒消息一律 ack + FAILED 日志）。
     */
    private void onMessage(String taskId, MqMessage message) {
        try {
            FlowMqTaskDO latestTask = flowMqTaskRepository.findById(taskId).orElse(null);
            if (latestTask == null) {
                log.warn("[MqConsumerManager] 任务不存在或已删除，取消订阅: taskId={}", taskId);
                cancel(taskId);
                return;
            }
            // 消息级幂等：Redis 锁按 messageId 去重；Redis 不可用降级放行
            String messageId = message != null ? message.getMessageId() : null;
            if (StrUtil.isNotBlank(messageId)) {
                String dedupKey = DEDUP_KEY_PREFIX + taskId + ":" + messageId;
                int ttlSeconds = Math.max(1, yuFlowProperties.getMq().getDedupTtlSeconds());
                try {
                    boolean first = FlowRedisUtil.setIfAbsent(dedupKey, "1", ttlSeconds, TimeUnit.SECONDS);
                    if (!first) {
                        log.info("[MqConsumerManager] 消息重复，跳过执行: taskId={}, messageId={}", taskId, messageId);
                        saveSkippedLog(latestTask, message, "消息重复（幂等去重命中），跳过执行");
                        return;
                    }
                } catch (Exception e) {
                    log.warn("[MqConsumerManager] Redis 不可用，幂等去重降级放行: taskId={}, messageId={}, error={}",
                            taskId, messageId, e.getMessage());
                }
            }
            executeTask(latestTask, message, TRIGGER_MQ);
        } catch (Exception e) {
            // 必须吞掉：防止 Provider 侧 nack/requeue 死循环
            log.error("[MqConsumerManager] 消费回调异常: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    private void executeTask(FlowMqTaskDO latestTask, MqMessage message, String triggerType) {
        long startTime = System.currentTimeMillis();
        String status = "RUNNING";
        String errorMsg = null;
        String traceData = null;

        PublishedSnapshot snap = resolvePublishedSnapshot(latestTask);
        String publishedName = snap.name() != null ? snap.name() : latestTask.getName();
        String body = message != null ? message.getBody() : null;

        log.info("[MqConsumerManager] 开始执行任务: taskId={}, name={}, topic={}, messageId={}, triggerType={}",
                latestTask.getId(), publishedName,
                message != null ? message.getTopic() : null,
                message != null ? message.getMessageId() : null, triggerType);

        try {
            String dsl = snap.dsl();
            if (StrUtil.isBlank(dsl)) {
                throw new IllegalStateException("任务未发布或发布快照为空，跳过执行");
            }
            int maxBytes = yuFlowProperties.getMq().getMaxMessageBytes();
            if (maxBytes > 0 && body != null && body.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
                throw new IllegalStateException("消息体超过大小上限 " + maxBytes + " 字节，拒绝执行");
            }

            // 注入消息元信息，供 MqTriggerStepExecutor 写入 $.mq.*
            Map<String, Object> args = new HashMap<>();
            args.put("taskName", publishedName);
            args.put(MqTriggerStepExecutor.ARG_TOPIC, message != null ? message.getTopic() : null);
            args.put(MqTriggerStepExecutor.ARG_MESSAGE, body);
            args.put(MqTriggerStepExecutor.ARG_HEADERS, message != null ? message.getHeaders() : null);
            args.put(MqTriggerStepExecutor.ARG_MESSAGE_ID, message != null ? message.getMessageId() : null);

            boolean logEnabled = Boolean.TRUE.equals(latestTask.getLogEnabled());
            Object result = flowEngine.execute(dsl, args, logEnabled,
                    "MQ", latestTask.getId(), publishedName);

            if (logEnabled) {
                FlowTrace trace = (result instanceof FlowTrace) ? (FlowTrace) result : null;
                if (trace != null && "error".equalsIgnoreCase(trace.getStatus())) {
                    status = "FAILED";
                    errorMsg = trace.getErrorMsg();
                } else {
                    status = "SUCCESS";
                }
                if (trace != null) {
                    // DSL 用内容哈希引用；超限递进截断
                    TracePersistUtil.PersistOptions opts = TracePersistUtil.PersistOptions.from(
                            yuFlowProperties != null ? yuFlowProperties.getEngine() : null);
                    traceData = TracePersistUtil.serializeForPersist(trace, dsl, objectMapper, opts);
                }
            } else if (result instanceof ExecutionResult) {
                ExecutionResult er = (ExecutionResult) result;
                status = er.isSuccess() ? "SUCCESS" : "FAILED";
                if (!er.isSuccess()) {
                    errorMsg = er.getMessage();
                }
            } else {
                status = "SUCCESS";
            }
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            log.error("[MqConsumerManager] 任务执行失败: taskId={}, name={}, error={}",
                    latestTask.getId(), publishedName, e.getMessage(), e);
        }

        // 写入日志（始终记录摘要；trace 仅在 logEnabled 时写入）
        long costTimeMs = System.currentTimeMillis() - startTime;
        assetMetricsRecorder.record(
                MetricsAssetType.MQ_TASK,
                latestTask.getId(),
                MetricsOutcome.fromStatus(status),
                costTimeMs,
                triggerType);
        try {
            FlowMqTaskLogDO logDO = FlowMqTaskLogDO.builder()
                    .taskId(latestTask.getId())
                    .taskName(publishedName)
                    .topic(message != null ? message.getTopic() : null)
                    .messageId(message != null ? message.getMessageId() : null)
                    .triggerType(triggerType)
                    .status(status)
                    .costTimeMs(costTimeMs)
                    .errorMsg(errorMsg)
                    .traceData(traceData)
                    .build();
            flowMqTaskLogService.saveAsync(logDO);
        } catch (Exception e) {
            log.error("[MqConsumerManager] 日志写入失败: taskId={}, error={}", latestTask.getId(), e.getMessage());
        }

        log.info("[MqConsumerManager] 任务执行完成: taskId={}, status={}, costTimeMs={}",
                latestTask.getId(), status, costTimeMs);
    }

    private void saveSkippedLog(FlowMqTaskDO task, MqMessage message, String reason) {
        assetMetricsRecorder.record(
                MetricsAssetType.MQ_TASK, task.getId(), MetricsOutcome.SKIPPED, 0L, TRIGGER_MQ);
        try {
            FlowMqTaskLogDO logDO = FlowMqTaskLogDO.builder()
                    .taskId(task.getId())
                    .taskName(task.getName())
                    .topic(message != null ? message.getTopic() : null)
                    .messageId(message != null ? message.getMessageId() : null)
                    .triggerType(TRIGGER_MQ)
                    .status("SKIPPED")
                    .costTimeMs(0L)
                    .errorMsg(reason)
                    .build();
            flowMqTaskLogService.saveAsync(logDO);
        } catch (Exception e) {
            log.error("[MqConsumerManager] SKIPPED 日志写入失败: taskId={}, error={}",
                    task.getId(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 发布快照解析
    // ─────────────────────────────────────────────────────────────────────────

    /** 已发布快照字段（一次 JSON 解析） */
    private record PublishedSnapshot(String dsl, String name, String connectionCode,
                                     String topic, String consumerGroup, int concurrency) {}

    /** 消费运行时只读已发布快照；MANUAL 模拟也走快照，保证与线上一致。 */
    private PublishedSnapshot resolvePublishedSnapshot(FlowMqTaskDO task) {
        int draftConcurrency = task.getConcurrency() != null ? Math.max(1, task.getConcurrency()) : 1;
        if (task.getPublishStatus() == null || task.getPublishStatus() != 1
                || StrUtil.isBlank(task.getPublishedSnapshot())) {
            return new PublishedSnapshot(null, task.getName(), task.getConnectionCode(),
                    task.getTopic(), task.getConsumerGroup(), draftConcurrency);
        }
        try {
            JsonNode snap = objectMapper.readTree(task.getPublishedSnapshot());
            String dsl = textOrNull(snap, "dslContent");
            String name = textOrNull(snap, "name");
            String connectionCode = textOrNull(snap, "connectionCode");
            String topic = textOrNull(snap, "topic");
            String consumerGroup = textOrNull(snap, "consumerGroup");
            int concurrency = snap.has("concurrency") && !snap.get("concurrency").isNull()
                    ? Math.max(1, snap.get("concurrency").asInt(1)) : draftConcurrency;
            if (StrUtil.isBlank(name)) {
                name = task.getName();
            }
            // 兼容缺字段快照：降级草稿字段，避免已发布任务无法订阅
            if (StrUtil.isBlank(connectionCode)) {
                connectionCode = task.getConnectionCode();
            }
            if (StrUtil.isBlank(topic)) {
                topic = task.getTopic();
            }
            return new PublishedSnapshot(dsl, name, connectionCode, topic, consumerGroup, concurrency);
        } catch (Exception e) {
            log.warn("[MqConsumerManager] 解析 publishedSnapshot 失败: taskId={}", task.getId(), e);
            return new PublishedSnapshot(null, task.getName(), task.getConnectionCode(),
                    task.getTopic(), task.getConsumerGroup(), draftConcurrency);
        }
    }

    private static String textOrNull(JsonNode snap, String field) {
        JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }
}
