package org.yu.flow.module.alert.service;

import org.yu.flow.module.alert.domain.AlertRuleDO;

/**
 * 告警扫描与通道投递。
 */
public interface AlertDispatchService {

    /** 执行单条规则；skipThrottle=true 时忽略 interval Redis 节流 */
    void runRule(AlertRuleDO rule, boolean skipThrottle);

    /** SysConfig 兜底路径（无启用规则时） */
    void runSysConfigFallback();
}
