package org.yu.flow.module.task.scheduler;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.TracePersistUtil;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.log.task.domain.FlowTaskLogDO;
import org.yu.flow.log.task.service.FlowTaskLogService;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 动态定时任务调度器
 *
 * <p>使用 Spring {@link TaskScheduler} 实现任务的运行时动态注册与取消，
 * 无需重启应用即可生效。应用启动后自动加载所有 enabled=true 的任务。
 *
 * <p>多节点部署时，Cron / MANUAL 触发通过 Redis 分布式锁保证同一任务全局只执行一次；
 * debug 不走锁。
 *
 * @author yu-flow
 */
@Slf4j
@Component
public class FlowTaskScheduler {

    /** Cron 分布式锁 key 前缀 */
    private static final String TASK_LOCK_KEY_PREFIX = "flow:task:lock:";

    /** 触发类型：Cron 调度（需抢锁） */
    private static final String TRIGGER_CRON = "CRON";

    /** 触发类型：手动立即执行（与 Cron 共用锁，防连点 / 与调度重叠） */
    private static final String TRIGGER_MANUAL = "MANUAL";

    @Resource
    private TaskScheduler taskScheduler;

    @Resource
    private FlowEngine flowEngine;

    @Resource
    private FlowTaskLogService flowTaskLogService;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private AssetMetricsRecorder assetMetricsRecorder;

    private static final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();

    /** taskId -> ScheduledFuture 映射，用于取消调度 */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // 启动加载
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 应用启动完毕后，加载所有 enabled=true 的任务，注册到调度器。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadAllTasks() {
        List<FlowTaskDO> enabledTasks = flowTaskRepository.findByEnabled(true);
        log.info("[FlowTaskScheduler] 启动加载定时任务，共 {} 个", enabledTasks.size());
        for (FlowTaskDO task : enabledTasks) {
            try {
                schedule(task);
            } catch (Exception e) {
                log.error("[FlowTaskScheduler] 任务注册失败，taskId={}, name={}, cron={}, error={}",
                        task.getId(), task.getName(), task.getCron(), e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 调度管理
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 注册一个任务到调度器。
     * 若已存在同 taskId 的调度，先取消再重新注册。
     * <p>触发器 Cron 取自 {@code publishedSnapshot}，与运行 DSL 保持一致；草稿 Cron 不影响线上调度。
     *
     * @param task 任务定义
     */
    public void schedule(FlowTaskDO task) {
        if (task == null || StrUtil.isBlank(task.getId())) {
            log.warn("[FlowTaskScheduler] 任务为空，跳过注册");
            return;
        }
        if (task.getPublishStatus() == null || task.getPublishStatus() != 1
                || StrUtil.isBlank(task.getPublishedSnapshot())) {
            log.info("[FlowTaskScheduler] 任务未发布，跳过注册: taskId={}, name={}",
                    task.getId(), task.getName());
            cancel(task.getId());
            return;
        }
        PublishedSnapshot snap = resolvePublishedSnapshot(task);
        String publishedCron = snap.cron();
        if (StrUtil.isBlank(publishedCron)) {
            log.warn("[FlowTaskScheduler] 发布快照中 Cron 为空，跳过注册: taskId={}", task.getId());
            cancel(task.getId());
            return;
        }
        // 幂等：先取消旧调度
        cancel(task.getId());

        try {
            CronTrigger trigger = new CronTrigger(publishedCron);
            // 捕获 taskId，执行时再查库，避免闭包持有过期 dsl
            String taskId = task.getId();
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeById(taskId, TRIGGER_CRON), trigger);
            futures.put(task.getId(), future);
            log.info("[FlowTaskScheduler] 任务已注册: taskId={}, name={}, cron(published)={}",
                    task.getId(), snap.name() != null ? snap.name() : task.getName(), publishedCron);
        } catch (Exception e) {
            log.error("[FlowTaskScheduler] 任务注册失败: taskId={}, cron={}, error={}",
                    task.getId(), publishedCron, e.getMessage());
            throw e;
        }
    }

    /**
     * 取消任务调度（停用或删除时调用）。
     *
     * @param taskId 任务ID
     */
    public void cancel(String taskId) {
        ScheduledFuture<?> future = futures.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("[FlowTaskScheduler] 任务已取消: taskId={}", taskId);
        }
    }

    /**
     * 重新调度（Cron 修改后调用）。
     *
     * @param task 更新后的任务定义
     */
    public void reschedule(FlowTaskDO task) {
        cancel(task.getId());
        if (Boolean.TRUE.equals(task.getEnabled())) {
            schedule(task);
        }
    }

    /**
     * 立即手动触发一次（供 /run 接口调用），异步执行，不阻塞 HTTP 线程。
     * <p>与 Cron 共用 Redis 执行锁，避免连点或与调度重叠双跑。
     *
     * @param task 任务定义
     */
    public void triggerManually(FlowTaskDO task) {
        if (task == null || StrUtil.isBlank(task.getId())) {
            return;
        }
        taskScheduler.schedule(() -> executeById(task.getId(), TRIGGER_MANUAL),
                java.time.Instant.now());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 执行逻辑
    // ─────────────────────────────────────────────────────────────────────────

    private void executeById(String taskId, String triggerType) {
        FlowTaskDO latestTask = flowTaskRepository.findById(taskId).orElse(null);
        if (latestTask == null) {
            log.warn("[FlowTaskScheduler] 任务不存在或已删除，跳过执行: taskId={}", taskId);
            cancel(taskId);
            return;
        }

        // Cron / MANUAL 抢分布式锁；debug 不走锁
        String lockKey = null;
        String lockValue = null;
        ScheduledFuture<?> renewFuture = null;
        int ttlMinutes = Math.max(1, yuFlowProperties.getTask().getLockTtlMinutes());
        boolean needLock = TRIGGER_CRON.equals(triggerType) || TRIGGER_MANUAL.equals(triggerType);
        if (needLock) {
            lockKey = TASK_LOCK_KEY_PREFIX + taskId;
            lockValue = UUID.randomUUID().toString();
            try {
                boolean acquired = FlowRedisUtil.setIfAbsent(lockKey, lockValue, ttlMinutes, TimeUnit.MINUTES);
                if (!acquired) {
                    log.info("[FlowTaskScheduler] 未抢到执行锁，跳过: taskId={}, name={}, trigger={}, lockKey={}",
                            latestTask.getId(), latestTask.getName(), triggerType, lockKey);
                    saveSkippedLog(latestTask, triggerType, "未抢到分布式执行锁，其他节点正在执行或刚执行完成");
                    return;
                }
            } catch (Exception e) {
                // fail-closed：Redis 不可用时不执行，避免多节点重复跑
                log.error("[FlowTaskScheduler] Redis 不可用，fail-closed 跳过执行: taskId={}, name={}, error={}",
                        latestTask.getId(), latestTask.getName(), e.getMessage());
                saveSkippedLog(latestTask, triggerType, "Redis 不可用，fail-closed 跳过执行: " + e.getMessage());
                return;
            }

            // 心跳续期：长任务执行期间防止 TTL 提前丢失导致双跑
            int renewSec = yuFlowProperties.getTask().getLockRenewIntervalSeconds();
            if (renewSec > 0) {
                final String renewKey = lockKey;
                final String renewVal = lockValue;
                final int renewTtl = ttlMinutes;
                renewFuture = taskScheduler.scheduleAtFixedRate(
                        () -> {
                            boolean ok = FlowRedisUtil.renewLock(renewKey, renewVal, renewTtl, TimeUnit.MINUTES);
                            if (!ok) {
                                log.warn("[FlowTaskScheduler] 锁续期失败（可能已丢失）: lockKey={}", renewKey);
                            } else {
                                log.debug("[FlowTaskScheduler] 锁续期成功: lockKey={}, ttl={}min", renewKey, renewTtl);
                            }
                        },
                        java.time.Instant.now().plusSeconds(renewSec),
                        java.time.Duration.ofSeconds(renewSec));
            }
        }

        try {
            executeTaskWithTriggerType(latestTask, triggerType);
        } finally {
            if (renewFuture != null) {
                renewFuture.cancel(false);
            }
            if (lockKey != null && lockValue != null) {
                boolean unlocked = FlowRedisUtil.unlock(lockKey, lockValue);
                if (!unlocked) {
                    log.warn("[FlowTaskScheduler] 释放执行锁失败或锁已过期: taskId={}, lockKey={}",
                            latestTask.getId(), lockKey);
                }
            }
        }
    }

    private void executeTaskWithTriggerType(FlowTaskDO latestTask, String triggerType) {
        long startTime = System.currentTimeMillis();
        String status = "RUNNING";
        String errorMsg = null;
        String traceData = null;

        PublishedSnapshot snap = resolvePublishedSnapshot(latestTask);
        String publishedName = snap.name() != null ? snap.name() : latestTask.getName();

        log.info("[FlowTaskScheduler] 开始执行任务: taskId={}, name={}, triggerType={}",
                latestTask.getId(), publishedName, triggerType);

        try {
            String dsl = snap.dsl();
            if (StrUtil.isBlank(dsl)) {
                throw new IllegalStateException("任务未发布或发布快照为空，跳过执行");
            }

            // 透传已发布快照中的元信息，供 ScheduleStepExecutor 写入 $.schedule.*
            Map<String, Object> args = new HashMap<>();
            args.put("taskName", publishedName);
            args.put("cron", snap.cron());

            boolean logEnabled = Boolean.TRUE.equals(latestTask.getLogEnabled());
            Object result = flowEngine.execute(dsl, args, logEnabled,
                    "TASK", latestTask.getId(), publishedName);

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
            log.error("[FlowTaskScheduler] 任务执行失败: taskId={}, name={}, error={}",
                    latestTask.getId(), publishedName, e.getMessage(), e);
        }

        // 写入日志（始终记录摘要；trace 仅在 logEnabled 时写入）
        long costTimeMs = System.currentTimeMillis() - startTime;
        assetMetricsRecorder.record(
                MetricsAssetType.TASK,
                latestTask.getId(),
                MetricsOutcome.fromStatus(status),
                costTimeMs,
                triggerType);
        try {
            FlowTaskLogDO logDO = FlowTaskLogDO.builder()
                    .taskId(latestTask.getId())
                    .taskName(publishedName)
                    .triggerType(triggerType)
                    .status(status)
                    .costTimeMs(costTimeMs)
                    .errorMsg(errorMsg)
                    .traceData(traceData)
                    .build();
            flowTaskLogService.saveAsync(logDO);
        } catch (Exception e) {
            log.error("[FlowTaskScheduler] 日志写入失败: taskId={}, error={}", latestTask.getId(), e.getMessage());
        }

        log.info("[FlowTaskScheduler] 任务执行完成: taskId={}, status={}, costTimeMs={}",
                latestTask.getId(), status, costTimeMs);
    }

    private void saveSkippedLog(FlowTaskDO task, String triggerType, String reason) {
        assetMetricsRecorder.record(
                MetricsAssetType.TASK, task.getId(), MetricsOutcome.SKIPPED, 0L, triggerType);
        try {
            FlowTaskLogDO logDO = FlowTaskLogDO.builder()
                    .taskId(task.getId())
                    .taskName(task.getName())
                    .triggerType(triggerType)
                    .status("SKIPPED")
                    .costTimeMs(0L)
                    .errorMsg(reason)
                    .build();
            flowTaskLogService.saveAsync(logDO);
        } catch (Exception e) {
            log.error("[FlowTaskScheduler] SKIPPED 日志写入失败: taskId={}, error={}",
                    task.getId(), e.getMessage());
        }
    }

    /** 已发布快照字段（一次 JSON 解析） */
    private record PublishedSnapshot(String dsl, String name, String cron) {}

    /** 调度运行时只读已发布快照；MANUAL 也走快照，保证与线上一致。 */
    private PublishedSnapshot resolvePublishedSnapshot(FlowTaskDO task) {
        if (task.getPublishStatus() == null || task.getPublishStatus() != 1
                || StrUtil.isBlank(task.getPublishedSnapshot())) {
            return new PublishedSnapshot(null, task.getName(), task.getCron());
        }
        try {
            JsonNode snap = objectMapper.readTree(task.getPublishedSnapshot());
            String dsl = textOrNull(snap, "dslContent");
            String name = textOrNull(snap, "name");
            String cron = textOrNull(snap, "cron");
            if (StrUtil.isBlank(name)) {
                name = task.getName();
            }
            // 兼容极旧快照缺 cron：降级草稿字段，避免已发布任务无法注册
            if (StrUtil.isBlank(cron)) {
                cron = task.getCron();
            }
            return new PublishedSnapshot(dsl, name, cron);
        } catch (Exception e) {
            log.warn("[FlowTaskScheduler] 解析 publishedSnapshot 失败: taskId={}", task.getId(), e);
            return new PublishedSnapshot(null, task.getName(), task.getCron());
        }
    }

    private static String textOrNull(JsonNode snap, String field) {
        JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }
}
