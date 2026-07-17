package org.yu.flow.log.third.support;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.log.third.domain.FlowThirdLogDO;
import org.yu.flow.log.third.service.FlowThirdLogService;

/**
 * 供非 Spring 管理的执行器（如 HttpRequestStepExecutor）异步写入三方日志。
 *
 * <p>FlowEngine 多处通过 {@code new FlowEngine()} 创建，执行器无法直接注入 Bean，
 * 因此用静态 holder 桥接。</p>
 */
@Slf4j
@Component
public class ThirdLogRecorder {

    private static volatile FlowThirdLogService service;

    @Resource
    private FlowThirdLogService flowThirdLogService;

    @PostConstruct
    public void init() {
        service = flowThirdLogService;
    }

    @PreDestroy
    public void destroy() {
        service = null;
    }

    public static void saveAsync(FlowThirdLogDO logDO) {
        if (logDO == null) {
            return;
        }
        FlowThirdLogService svc = service;
        if (svc == null) {
            log.warn("[ThirdLog] Recorder 未就绪，跳过入库: url={}", logDO.getRequestUrl());
            return;
        }
        try {
            svc.saveLogAsync(logDO);
        } catch (Exception e) {
            log.error("[ThirdLog] 提交异步入库失败: url={}, error={}",
                    logDO.getRequestUrl(), e.getMessage(), e);
        }
    }
}
