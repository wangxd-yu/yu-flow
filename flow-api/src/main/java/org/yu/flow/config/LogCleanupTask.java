package org.yu.flow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.log.login.repository.LoginLogRepository;
import org.yu.flow.log.execution.repository.FlowExecutionLogRepository;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日志定时清理任务。
 *
 * <p>采用自管理的 {@link ScheduledExecutorService} 实现定时调度，
 * 不依赖宿主项目开启 {@code @EnableScheduling}，与现有 DataSource 熔断探测保持架构一致。</p>
 *
 * <h3>清理策略：</h3>
 * <ul>
 *   <li>API 执行日志 ({@code flow_log_execution})：默认保留 30 天，通过系统参数 {@code LOG_EXECUTION_RETENTION_DAYS} 可配</li>
 *   <li>登录审计日志 ({@code flow_log_login})：默认保留 90 天，通过系统参数 {@code LOG_LOGIN_RETENTION_DAYS} 可配</li>
 *   <li>配置值设置为 0 时，跳过对应日志的清理</li>
 * </ul>
 *
 * <p>启动后延迟 1 分钟执行首轮清理，之后每 24 小时执行一次。</p>
 *
 * @author yu-flow
 * @since 1.0
 */
@Slf4j
@Component
public class LogCleanupTask {

    /** 系统参数键：执行日志保留天数 */
    public static final String CONFIG_KEY_EXECUTION_RETENTION = "LOG_EXECUTION_RETENTION_DAYS";
    /** 系统参数键：登录日志保留天数 */
    public static final String CONFIG_KEY_LOGIN_RETENTION = "LOG_LOGIN_RETENTION_DAYS";

    /** 执行日志默认保留天数 */
    private static final int DEFAULT_EXECUTION_RETENTION_DAYS = 30;
    /** 登录日志默认保留天数 */
    private static final int DEFAULT_LOGIN_RETENTION_DAYS = 90;

    @Resource
    private FlowExecutionLogRepository flowExecutionLogRepository;

    @Resource
    private LoginLogRepository loginLogRepository;

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    /** 自管理的定时调度器（不依赖宿主的 @EnableScheduling） */
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-cleanup-scheduler");
            t.setDaemon(true);
            return t;
        });

        // 首次延迟 1 分钟执行，之后每 24 小时执行一次
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
        try {
            cleanupExecutionLogs();
        } catch (Exception e) {
            log.error("[LogCleanupTask] 执行日志清理异常", e);
        }

        try {
            cleanupLoginLogs();
        } catch (Exception e) {
            log.error("[LogCleanupTask] 登录日志清理异常", e);
        }
    }

    /**
     * 清理过期的 API 执行日志。
     */
    private void cleanupExecutionLogs() {
        int retentionDays = sysConfigCacheManager.getIntConfig(
                CONFIG_KEY_EXECUTION_RETENTION, DEFAULT_EXECUTION_RETENTION_DAYS);

        if (retentionDays <= 0) {
            log.debug("[LogCleanupTask] 执行日志清理已禁用（保留天数={}）", retentionDays);
            return;
        }

        // 计算时间阈值
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -retentionDays);
        Date threshold = cal.getTime();

        // 使用 TransactionTemplate 编程式事务（避免自调用导致 @Transactional 失效）
        Integer deleted = transactionTemplate.execute(status ->
                flowExecutionLogRepository.deleteByCreateTimeBefore(threshold)
        );

        if (deleted != null && deleted > 0) {
            log.info("[LogCleanupTask] 执行日志清理完成：保留天数={}，删除记录数={}，阈值时间={}",
                    retentionDays, deleted, threshold);
        } else {
            log.debug("[LogCleanupTask] 执行日志清理完成：无过期记录需清理（保留天数={}）", retentionDays);
        }
    }

    /**
     * 清理过期的登录审计日志。
     */
    private void cleanupLoginLogs() {
        int retentionDays = sysConfigCacheManager.getIntConfig(
                CONFIG_KEY_LOGIN_RETENTION, DEFAULT_LOGIN_RETENTION_DAYS);

        if (retentionDays <= 0) {
            log.debug("[LogCleanupTask] 登录日志清理已禁用（保留天数={}）", retentionDays);
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        // 使用 TransactionTemplate 编程式事务
        Integer deleted = transactionTemplate.execute(status ->
                loginLogRepository.deleteByCreateTimeBefore(threshold)
        );

        if (deleted != null && deleted > 0) {
            log.info("[LogCleanupTask] 登录日志清理完成：保留天数={}，删除记录数={}，阈值时间={}",
                    retentionDays, deleted, threshold);
        } else {
            log.debug("[LogCleanupTask] 登录日志清理完成：无过期记录需清理（保留天数={}）", retentionDays);
        }
    }
}
