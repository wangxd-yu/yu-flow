package org.yu.flow.module.api.runtime;

import cn.hutool.core.util.StrUtil;

/**
 * 单次接口执行内，执行环节与日志环节共享的上下文。
 */
public final class RuntimeLogContext {

    private final String logMode;
    private String actualSql;

    /**
     * @param logMode 已解析的日志模式，见 {@link org.yu.flow.engine.log.LogMode}；为 null 时不开启流程轨迹
     */
    public RuntimeLogContext(String logMode) {
        this.logMode = logMode;
    }

    /** 仅 ALL 模式让流程引擎采集逐节点轨迹 */
    public boolean traceEnabled() {
        return "ALL".equals(logMode);
    }

    /** 记录参数内联后的实际 SQL，供日志与调试轨迹展示 */
    public void actualSql(String sql) {
        this.actualSql = sql;
    }

    public String actualSqlOr(String fallback) {
        return StrUtil.isNotBlank(actualSql) ? actualSql : fallback;
    }
}
