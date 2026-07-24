package org.yu.flow.log.open.support;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.log.open.domain.FlowOpenCallLogDO;
import org.yu.flow.log.open.service.FlowOpenCallLogService;

/**
 * 网关侧异步写入开放平台入站摘要日志（fail-open）。
 */
@Slf4j
@Component
public class OpenCallLogRecorder {

    private static volatile FlowOpenCallLogService service;

    @Resource
    private FlowOpenCallLogService flowOpenCallLogService;

    @PostConstruct
    public void init() {
        service = flowOpenCallLogService;
    }

    @PreDestroy
    public void destroy() {
        service = null;
    }

    public static void saveAsync(FlowOpenCallLogDO logDO) {
        if (logDO == null) {
            return;
        }
        FlowOpenCallLogService svc = service;
        if (svc == null) {
            return;
        }
        try {
            svc.saveLogAsync(logDO);
        } catch (Exception e) {
            log.warn("[OpenCallLog] 提交异步入库失败: {}", e.getMessage());
        }
    }
}
