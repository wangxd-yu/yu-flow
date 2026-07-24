package org.yu.flow.module.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.service.AlertDispatchService;
import org.yu.flow.module.alert.service.AlertManageService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时扫描：优先执行启用规则；无规则时回退 SysConfig 全局 Webhook。
 */
@Slf4j
@Component
public class AlertWebhookJob {

    @Resource
    private AlertManageService alertManageService;
    @Resource
    private AlertDispatchService alertDispatchService;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alert-webhook-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::safeTick, 90, 60, TimeUnit.SECONDS);
        log.info("[AlertWebhookJob] 已启动（规则优先 / SysConfig 兜底）");
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safeTick() {
        try {
            tickOnce();
        } catch (Exception e) {
            log.error("[AlertWebhookJob] 执行异常", e);
        }
    }

    public void tickOnce() {
        List<AlertRuleDO> rules = alertManageService.listEnabledRules();
        if (rules != null && !rules.isEmpty()) {
            for (AlertRuleDO rule : rules) {
                try {
                    alertDispatchService.runRule(rule, false);
                } catch (Exception e) {
                    log.warn("[AlertWebhookJob] 规则 {} 执行失败: {}", rule.getName(), e.getMessage());
                }
            }
            return;
        }
        alertDispatchService.runSysConfigFallback();
    }
}
