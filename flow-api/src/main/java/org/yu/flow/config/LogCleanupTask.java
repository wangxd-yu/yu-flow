package org.yu.flow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.log.audit.repository.AuditLogRepository;
import org.yu.flow.log.execution.repository.FlowExecutionLogRepository;
import org.yu.flow.log.login.repository.LoginLogRepository;
import org.yu.flow.log.open.repository.FlowOpenCallLogRepository;
import org.yu.flow.log.service.repository.FlowServiceLogRepository;
import org.yu.flow.log.task.repository.FlowTaskLogRepository;
import org.yu.flow.log.third.repository.FlowThirdLogRepository;
import org.yu.flow.module.alert.repository.AlertEventRepository;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日志定时清理任务。
 *
 * <p>采用自管理的 {@link ScheduledExecutorService} 实现定时调度，
 * 不依赖宿主项目开启 {@code @EnableScheduling}。</p>
 *
 * <h3>清理策略（系统参数，组 LOG；值为 0 则跳过该类日志）：</h3>
 * <ul>
 *   <li>{@code LOG_EXECUTION_RETENTION_DAYS} — API 执行日志，默认 30</li>
 *   <li>{@code LOG_LOGIN_RETENTION_DAYS} — 登录审计，默认 90</li>
 *   <li>{@code LOG_TASK_RETENTION_DAYS} — 定时任务日志，默认 30</li>
 *   <li>{@code LOG_MQ_TASK_RETENTION_DAYS} — MQ 任务日志，默认 30</li>
 * </ul>
 *
 * <p>定时任务日志 / MQ 任务日志 / API 执行日志支持资产级覆盖（{@code logRetentionDays}）：
 * null=跟随系统配置，0=永久保留（豁免清理），&gt;0=按资产自定义天数清理。</p>
 * <ul>
 *   <li>{@code LOG_SERVICE_RETENTION_DAYS} — 服务编排日志，默认 30</li>
 *   <li>{@code LOG_THIRD_RETENTION_DAYS} — 第三方调用日志，默认 30</li>
 *   <li>{@code LOG_OPEN_CALL_RETENTION_DAYS} — 开放平台调用日志，默认 30</li>
 *   <li>{@code LOG_AUDIT_RETENTION_DAYS} — 配置变更审计，默认 180</li>
 *   <li>{@code LOG_ALERT_EVENT_RETENTION_DAYS} — 告警历史，默认 90</li>
 * </ul>
 *
 * <p>启动后延迟 1 分钟执行首轮清理，之后每 24 小时执行一次；多节点通过 Redis 锁互斥。</p>
 *
 * @author yu-flow
 * @since 1.0
 */
@Slf4j
@Component
public class LogCleanupTask {

    public static final String CONFIG_KEY_EXECUTION_RETENTION = "LOG_EXECUTION_RETENTION_DAYS";
    public static final String CONFIG_KEY_LOGIN_RETENTION = "LOG_LOGIN_RETENTION_DAYS";
    public static final String CONFIG_KEY_TASK_RETENTION = "LOG_TASK_RETENTION_DAYS";
    public static final String CONFIG_KEY_MQ_TASK_RETENTION = "LOG_MQ_TASK_RETENTION_DAYS";
    public static final String CONFIG_KEY_SERVICE_RETENTION = "LOG_SERVICE_RETENTION_DAYS";
    public static final String CONFIG_KEY_THIRD_RETENTION = "LOG_THIRD_RETENTION_DAYS";
    public static final String CONFIG_KEY_OPEN_CALL_RETENTION = "LOG_OPEN_CALL_RETENTION_DAYS";
    public static final String CONFIG_KEY_AUDIT_RETENTION = "LOG_AUDIT_RETENTION_DAYS";
    public static final String CONFIG_KEY_ALERT_EVENT_RETENTION = "LOG_ALERT_EVENT_RETENTION_DAYS";

    private static final String CLEANUP_LOCK = "flow:log:cleanup:lock";

    private static final int DEFAULT_EXECUTION_RETENTION_DAYS = 30;
    private static final int DEFAULT_LOGIN_RETENTION_DAYS = 90;
    private static final int DEFAULT_TASK_RETENTION_DAYS = 30;
    private static final int DEFAULT_MQ_TASK_RETENTION_DAYS = 30;
    private static final int DEFAULT_SERVICE_RETENTION_DAYS = 30;
    private static final int DEFAULT_THIRD_RETENTION_DAYS = 30;
    private static final int DEFAULT_OPEN_CALL_RETENTION_DAYS = 30;
    private static final int DEFAULT_AUDIT_RETENTION_DAYS = 180;
    private static final int DEFAULT_ALERT_EVENT_RETENTION_DAYS = 90;

    @Resource
    private FlowExecutionLogRepository flowExecutionLogRepository;
    @Resource
    private org.yu.flow.module.api.repository.FlowApiRepository flowApiRepository;
    @Resource
    private LoginLogRepository loginLogRepository;
    @Resource
    private FlowTaskLogRepository flowTaskLogRepository;
    @Resource
    private org.yu.flow.module.task.repository.FlowTaskRepository flowTaskRepository;
    @Resource
    private org.yu.flow.module.mqtask.repository.FlowMqTaskLogRepository flowMqTaskLogRepository;
    @Resource
    private org.yu.flow.module.mqtask.repository.FlowMqTaskRepository flowMqTaskRepository;
    @Resource
    private FlowServiceLogRepository flowServiceLogRepository;
    @Resource
    private FlowThirdLogRepository flowThirdLogRepository;
    @Resource
    private FlowOpenCallLogRepository flowOpenCallLogRepository;
    @Resource
    private AuditLogRepository auditLogRepository;
    @Resource
    private AlertEventRepository alertEventRepository;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;
    @Resource
    private TransactionTemplate transactionTemplate;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-cleanup-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::executeCleanup, 1, 24 * 60, TimeUnit.MINUTES);
        log.info("[LogCleanupTask] 日志清理定时任务已启动，首次执行将在 1 分钟后触发，此后每 24 小时执行一次。");
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            log.info("[LogCleanupTask] 日志清理定时任务已停止。");
        }
    }

    /**
     * 执行清理逻辑（安全包裹，不会因异常中断调度链）。
     */
    private void executeCleanup() {
        String lockVal = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = FlowRedisUtil.setIfAbsent(CLEANUP_LOCK, lockVal, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[LogCleanupTask] 抢锁失败（Redis）: {}", e.getMessage());
            return;
        }
        if (!locked) {
            log.debug("[LogCleanupTask] 未抢到清理锁，跳过本轮（其他节点执行中）");
            return;
        }
        try {
            runOne("执行日志", this::cleanupExecutionLogs);
            runOne("登录日志", this::cleanupLoginLogs);
            runOne("任务日志", this::cleanupTaskLogs);
            runOne("MQ任务日志", this::cleanupMqTaskLogs);
            runOne("服务日志", this::cleanupServiceLogs);
            runOne("第三方日志", this::cleanupThirdLogs);
            runOne("开放调用日志", this::cleanupOpenCallLogs);
            runOne("审计日志", this::cleanupAuditLogs);
            runOne("告警历史", this::cleanupAlertEvents);
        } finally {
            FlowRedisUtil.unlock(CLEANUP_LOCK, lockVal);
        }
    }

    private void runOne(String label, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("[LogCleanupTask] {}清理异常", label, e);
        }
    }

    private void cleanupExecutionLogs() {
        cleanupWithTaskOverrides(
                CONFIG_KEY_EXECUTION_RETENTION,
                DEFAULT_EXECUTION_RETENTION_DAYS,
                "执行日志",
                flowApiRepository.findLogRetentionOverrides(),
                (apiId, threshold) -> flowExecutionLogRepository.deleteByApiIdAndCreateTimeBefore(apiId, threshold),
                threshold -> flowExecutionLogRepository.deleteByCreateTimeBefore(threshold),
                (threshold, excludeIds) -> flowExecutionLogRepository.deleteByCreateTimeBeforeAndApiIdNotIn(threshold, excludeIds));
    }

    private void cleanupLoginLogs() {
        cleanupByLocalDateTimeDays(
                CONFIG_KEY_LOGIN_RETENTION,
                DEFAULT_LOGIN_RETENTION_DAYS,
                "登录日志",
                threshold -> loginLogRepository.deleteByCreateTimeBefore(threshold));
    }

    private void cleanupTaskLogs() {
        cleanupWithTaskOverrides(
                CONFIG_KEY_TASK_RETENTION,
                DEFAULT_TASK_RETENTION_DAYS,
                "任务日志",
                flowTaskRepository.findLogRetentionOverrides(),
                (taskId, threshold) -> flowTaskLogRepository.deleteByTaskIdAndCreateTimeBefore(taskId, threshold),
                threshold -> flowTaskLogRepository.deleteByCreateTimeBefore(threshold),
                (threshold, excludeIds) -> flowTaskLogRepository.deleteByCreateTimeBeforeAndTaskIdNotIn(threshold, excludeIds));
    }

    private void cleanupMqTaskLogs() {
        cleanupWithTaskOverrides(
                CONFIG_KEY_MQ_TASK_RETENTION,
                DEFAULT_MQ_TASK_RETENTION_DAYS,
                "MQ任务日志",
                flowMqTaskRepository.findLogRetentionOverrides(),
                (taskId, threshold) -> flowMqTaskLogRepository.deleteByTaskIdAndCreateTimeBefore(taskId, threshold),
                threshold -> flowMqTaskLogRepository.deleteByCreateTimeBefore(threshold),
                (threshold, excludeIds) -> flowMqTaskLogRepository.deleteByCreateTimeBeforeAndTaskIdNotIn(threshold, excludeIds));
    }

    private void cleanupServiceLogs() {
        cleanupByLocalDateTimeDays(
                CONFIG_KEY_SERVICE_RETENTION,
                DEFAULT_SERVICE_RETENTION_DAYS,
                "服务日志",
                threshold -> flowServiceLogRepository.deleteByCreateTimeBefore(threshold));
    }

    private void cleanupThirdLogs() {
        cleanupByLocalDateTimeDays(
                CONFIG_KEY_THIRD_RETENTION,
                DEFAULT_THIRD_RETENTION_DAYS,
                "第三方日志",
                threshold -> flowThirdLogRepository.deleteByCreateTimeBefore(threshold));
    }

    private void cleanupOpenCallLogs() {
        cleanupByLocalDateTimeDays(
                CONFIG_KEY_OPEN_CALL_RETENTION,
                DEFAULT_OPEN_CALL_RETENTION_DAYS,
                "开放调用日志",
                threshold -> flowOpenCallLogRepository.deleteByCreateTimeBefore(threshold));
    }

    private void cleanupAuditLogs() {
        cleanupByLocalDateTimeDays(
                CONFIG_KEY_AUDIT_RETENTION,
                DEFAULT_AUDIT_RETENTION_DAYS,
                "审计日志",
                threshold -> auditLogRepository.deleteByCreateTimeBefore(threshold));
    }

    private void cleanupAlertEvents() {
        cleanupByLocalDateTimeDays(
                CONFIG_KEY_ALERT_EVENT_RETENTION,
                DEFAULT_ALERT_EVENT_RETENTION_DAYS,
                "告警历史",
                threshold -> alertEventRepository.deleteByFiredAtBefore(threshold));
    }

    private void cleanupByLocalDateTimeDays(
            String configKey,
            int defaultDays,
            String label,
            java.util.function.Function<LocalDateTime, Integer> deleter) {
        int retentionDays = sysConfigCacheManager.getIntConfig(configKey, defaultDays);
        if (retentionDays <= 0) {
            log.debug("[LogCleanupTask] {}清理已禁用（保留天数={}）", label, retentionDays);
            return;
        }
        LocalDateTime threshold = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(retentionDays);
        Integer deleted = transactionTemplate.execute(status -> deleter.apply(threshold));
        logDeleted(label, retentionDays, deleted, threshold);
    }

    /**
     * 带资产级覆盖的日志清理（两段式）：
     * <ol>
     *   <li>资产级：{@code logRetentionDays > 0} 的资产（任务 / API）按各自天数清理；{@code = 0} 永久保留，跳过删除</li>
     *   <li>系统级：其余资产按系统配置的保留天数清理（配置 ≤0 表示禁用系统级清理）；
     *       有资产级配置的资产一律从系统级删除中排除</li>
     * </ol>
     */
    private void cleanupWithTaskOverrides(
            String configKey,
            int defaultDays,
            String label,
            List<Object[]> overrides,
            java.util.function.BiFunction<String, LocalDateTime, Integer> perTaskDeleter,
            java.util.function.Function<LocalDateTime, Integer> globalDeleter,
            java.util.function.BiFunction<LocalDateTime, List<String>, Integer> globalExcludeDeleter) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        List<String> excludeIds = new ArrayList<>();
        for (Object[] row : overrides) {
            String taskId = (String) row[0];
            Integer days = row[1] != null ? ((Number) row[1]).intValue() : null;
            if (taskId == null || days == null) continue;
            excludeIds.add(taskId);
            if (days <= 0) {
                log.debug("[LogCleanupTask] {}：资产 {} 配置永久保留，跳过清理", label, taskId);
                continue;
            }
            LocalDateTime taskThreshold = now.minusDays(days);
            Integer deleted = transactionTemplate.execute(status -> perTaskDeleter.apply(taskId, taskThreshold));
            logDeleted(label + "(资产 " + taskId + ")", days, deleted, taskThreshold);
        }
        int retentionDays = sysConfigCacheManager.getIntConfig(configKey, defaultDays);
        if (retentionDays <= 0) {
            log.debug("[LogCleanupTask] {}系统级清理已禁用（保留天数={}）", label, retentionDays);
            return;
        }
        LocalDateTime threshold = now.minusDays(retentionDays);
        Integer deleted = transactionTemplate.execute(status -> excludeIds.isEmpty()
                ? globalDeleter.apply(threshold)
                : globalExcludeDeleter.apply(threshold, excludeIds));
        logDeleted(label, retentionDays, deleted, threshold);
    }

    private void logDeleted(String label, int retentionDays, Integer deleted, Object threshold) {
        if (deleted != null && deleted > 0) {
            log.info("[LogCleanupTask] {}清理完成：保留天数={}，删除记录数={}，阈值时间={}",
                    label, retentionDays, deleted, threshold);
        } else {
            log.debug("[LogCleanupTask] {}清理完成：无过期记录需清理（保留天数={}）", label, retentionDays);
        }
    }
}
