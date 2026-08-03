package org.yu.flow.module.oss.job;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.oss.client.OssStorageService;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.repository.OssObjectRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OSS 软删对象与过期文件异步物理清理。
 */
@Slf4j
@Component
public class OssObjectCleanupJob {

    private static final String CLEANUP_LOCK = "flow:oss:cleanup:lock";
    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private TransactionTemplate transactionTemplate;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        int intervalMinutes = Math.max(1, yuFlowProperties.getOss().getCleanupIntervalMinutes());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oss-object-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::executeCleanup, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        log.info("[OssObjectCleanupJob] 已启动，间隔 {} 分钟", intervalMinutes);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    private void executeCleanup() {
        String lockVal = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = FlowRedisUtil.setIfAbsent(CLEANUP_LOCK, lockVal, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[OssObjectCleanupJob] 抢锁失败: {}", e.getMessage());
            return;
        }
        if (!locked) {
            log.debug("[OssObjectCleanupJob] 未抢到清理锁，跳过本轮");
            return;
        }
        try {
            purgeExpiredActive();
            purgeSoftDeleted();
        } catch (Exception e) {
            log.error("[OssObjectCleanupJob] 清理异常", e);
        } finally {
            FlowRedisUtil.unlock(CLEANUP_LOCK, lockVal);
        }
    }

    private void purgeExpiredActive() {
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        List<OssObjectDO> expired = ossObjectRepository.findTop100ByStatusAndExpiresAtBefore(
                OssObjectDO.STATUS_ACTIVE, now);
        for (OssObjectDO object : expired) {
            try {
                transactionTemplate.executeWithoutResult(status -> purgeOne(object, true));
            } catch (Exception e) {
                log.warn("[OssObjectCleanupJob] 过期清理失败 id={}: {}", object.getId(), e.getMessage());
            }
        }
    }

    private void purgeSoftDeleted() {
        List<OssObjectDO> pending = ossObjectRepository.findTop100ByStatusAndObjectPurged(
                OssObjectDO.STATUS_DELETED, false);
        for (OssObjectDO object : pending) {
            try {
                transactionTemplate.executeWithoutResult(status -> purgeOne(object, false));
            } catch (Exception e) {
                log.warn("[OssObjectCleanupJob] 软删清理失败 id={}: {}", object.getId(), e.getMessage());
            }
        }
    }

    private void purgeOne(OssObjectDO object, boolean markDeletedFirst) {
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        if (markDeletedFirst && OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            object.setStatus(OssObjectDO.STATUS_DELETED);
            object.setObjectPurged(false);
            object.setUpdateTime(now);
            ossObjectRepository.save(object);
        }
        if (Boolean.TRUE.equals(object.getObjectPurged())) {
            return;
        }
        try {
            demoModeGuard.checkOssWrite(object.getBucket());
            ossStorageService.removeObject(object.getConnectionCode(), object.getBucket(), object.getObjectKey());
            if (StrUtil.isNotBlank(object.getThumbObjectKey())) {
                try {
                    ossStorageService.removeObject(object.getConnectionCode(), object.getBucket(),
                            object.getThumbObjectKey());
                } catch (Exception e) {
                    log.warn("[OssObjectCleanupJob] MinIO remove thumb id={}: {}", object.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[OssObjectCleanupJob] MinIO removeObject id={}: {}", object.getId(), e.getMessage());
            throw e;
        }
        object.setObjectPurged(true);
        object.setUpdateTime(now);
        ossObjectRepository.save(object);
        log.debug("[OssObjectCleanupJob] 已物理删除 id={} key={}", object.getId(), object.getObjectKey());
    }
}
