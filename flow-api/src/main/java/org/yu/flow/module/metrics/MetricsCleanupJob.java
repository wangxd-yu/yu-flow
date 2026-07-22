package org.yu.flow.module.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.metrics.repository.FlowMetricsMinuteRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 清理过期分钟汇总（分布式锁）。
 */
@Slf4j
@Component
public class MetricsCleanupJob {

    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private FlowMetricsMinuteRepository metricsMinuteRepository;
    @Resource
    private TransactionTemplate transactionTemplate;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-cleanup-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::safeCleanup, 5, 24 * 60, TimeUnit.MINUTES);
        log.info("[MetricsCleanupJob] 已启动");
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safeCleanup() {
        try {
            cleanupOnce();
        } catch (Exception e) {
            log.error("[MetricsCleanupJob] 清理异常", e);
        }
    }

    public void cleanupOnce() {
        YuFlowProperties.Metrics cfg = yuFlowProperties == null ? null : yuFlowProperties.getMetrics();
        if (cfg == null || !cfg.isEnabled() || cfg.getRetainDays() <= 0) {
            return;
        }
        String lockVal = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = FlowRedisUtil.setIfAbsent(MetricsKeys.CLEANUP_LOCK, lockVal, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[MetricsCleanupJob] 抢锁失败: {}", e.getMessage());
            return;
        }
        if (!locked) {
            return;
        }
        try {
            LocalDateTime before = MetricsKeys.nowMinute().minusDays(cfg.getRetainDays());
            Integer n = transactionTemplate.execute(status ->
                    metricsMinuteRepository.deleteByBucketStartBefore(MetricsKeys.toDate(before)));
            if (n != null && n > 0) {
                log.info("[MetricsCleanupJob] 已删除 {} 行（早于 {}）", n, before);
            }
        } finally {
            FlowRedisUtil.unlock(MetricsKeys.CLEANUP_LOCK, lockVal);
        }
    }
}
