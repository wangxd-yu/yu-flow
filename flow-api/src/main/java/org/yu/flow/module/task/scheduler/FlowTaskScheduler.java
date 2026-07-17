package org.yu.flow.module.task.scheduler;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.log.task.domain.FlowTaskLogDO;
import org.yu.flow.log.task.service.FlowTaskLogService;

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
 * <p>多节点部署时，Cron 触发通过 Redis 分布式锁保证同一任务全局只执行一次；
 * 手动触发（MANUAL）与 debug 不走锁。
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
     *
     * @param task 任务定义
     */
    public void schedule(FlowTaskDO task) {
        if (task == null || StrUtil.isBlank(task.getCron())) {
            log.warn("[FlowTaskScheduler] 任务 Cron 为空，跳过注册: taskId={}", task != null ? task.getId() : "null");
            return;
        }
        // 幂等：先取消旧调度
        cancel(task.getId());

        try {
            CronTrigger trigger = new CronTrigger(task.getCron());
            // 捕获 taskId，执行时再查库，避免闭包持有过期 dsl
            String taskId = task.getId();
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeById(taskId, TRIGGER_CRON), trigger);
            futures.put(task.getId(), future);
            log.info("[FlowTaskScheduler] 任务已注册: taskId={}, name={}, cron={}",
                    task.getId(), task.getName(), task.getCron());
        } catch (Exception e) {
            log.error("[FlowTaskScheduler] 任务注册失败: taskId={}, cron={}, error={}",
                    task.getId(), task.getCron(), e.getMessage());
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
     * <p>手动触发不走 Redis 分布式锁。
     *
     * @param task 任务定义
     */
    public void triggerManually(FlowTaskDO task) {
        if (task == null || StrUtil.isBlank(task.getId())) {
            return;
        }
        taskScheduler.schedule(() -> executeById(task.getId(), "MANUAL"),
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

        // 仅 Cron 抢分布式锁；MANUAL / debug 不走锁
        String lockKey = null;
        String lockValue = null;
        if (TRIGGER_CRON.equals(triggerType)) {
            lockKey = TASK_LOCK_KEY_PREFIX + taskId;
            lockValue = UUID.randomUUID().toString();
            int ttlMinutes = Math.max(1, yuFlowProperties.getTask().getLockTtlMinutes());
            try {
                boolean acquired = FlowRedisUtil.setIfAbsent(lockKey, lockValue, ttlMinutes, TimeUnit.MINUTES);
                if (!acquired) {
                    log.info("[FlowTaskScheduler] 未抢到执行锁，跳过: taskId={}, name={}, lockKey={}",
                            latestTask.getId(), latestTask.getName(), lockKey);
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
        }

        try {
            executeTaskWithTriggerType(latestTask, triggerType);
        } finally {
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

        log.info("[FlowTaskScheduler] 开始执行任务: taskId={}, name={}, triggerType={}",
                latestTask.getId(), latestTask.getName(), triggerType);

        try {
            if (StrUtil.isBlank(latestTask.getDslContent())) {
                throw new IllegalStateException("任务 DSL 内容为空，无法执行");
            }

            // 透传任务元信息，供 ScheduleStepExecutor 写入 $.schedule.*
            Map<String, Object> args = new HashMap<>();
            args.put("taskName", latestTask.getName());
            args.put("cron", latestTask.getCron());

            boolean logEnabled = Boolean.TRUE.equals(latestTask.getLogEnabled());
            Object result = flowEngine.execute(latestTask.getDslContent(), args, logEnabled,
                    "TASK", latestTask.getId(), latestTask.getName());

            if (logEnabled) {
                FlowTrace trace = (result instanceof FlowTrace) ? (FlowTrace) result : null;
                if (trace != null && "error".equalsIgnoreCase(trace.getStatus())) {
                    status = "FAILED";
                    errorMsg = trace.getErrorMsg();
                } else {
                    status = "SUCCESS";
                }
                if (trace != null) {
                    traceData = objectMapper.writeValueAsString(trace);
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
                    latestTask.getId(), latestTask.getName(), e.getMessage(), e);
        }

        // 写入日志（始终记录摘要；trace 仅在 logEnabled 时写入）
        long costTimeMs = System.currentTimeMillis() - startTime;
        try {
            FlowTaskLogDO logDO = FlowTaskLogDO.builder()
                    .taskId(latestTask.getId())
                    .taskName(latestTask.getName())
                    .triggerType(triggerType)
                    .status(status)
                    .costTimeMs(costTimeMs)
                    .errorMsg(errorMsg)
                    .traceData(traceData)
                    .build();
            flowTaskLogService.save(logDO);
        } catch (Exception e) {
            log.error("[FlowTaskScheduler] 日志写入失败: taskId={}, error={}", latestTask.getId(), e.getMessage());
        }

        log.info("[FlowTaskScheduler] 任务执行完成: taskId={}, status={}, costTimeMs={}",
                latestTask.getId(), status, costTimeMs);
    }

    private void saveSkippedLog(FlowTaskDO task, String triggerType, String reason) {
        try {
            FlowTaskLogDO logDO = FlowTaskLogDO.builder()
                    .taskId(task.getId())
                    .taskName(task.getName())
                    .triggerType(triggerType)
                    .status("SKIPPED")
                    .costTimeMs(0L)
                    .errorMsg(reason)
                    .build();
            flowTaskLogService.save(logDO);
        } catch (Exception e) {
            log.error("[FlowTaskScheduler] SKIPPED 日志写入失败: taskId={}, error={}",
                    task.getId(), e.getMessage());
        }
    }
}
