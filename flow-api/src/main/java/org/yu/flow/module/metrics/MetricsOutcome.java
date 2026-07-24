package org.yu.flow.module.metrics;

/**
 * 归一化执行结果。
 */
public enum MetricsOutcome {
    SUCCESS,
    FAIL,
    SKIPPED,
    /** 鉴权/授权失败；不计入业务成功率分母，避免污染健康度 */
    AUTH_FAIL;

    public static MetricsOutcome fromStatus(String status) {
        if (status == null) {
            return FAIL;
        }
        String s = status.trim().toUpperCase();
        if ("SUCCESS".equals(s) || "OK".equals(s) || "COMPLETED".equals(s) || "FINISHED".equals(s)) {
            return SUCCESS;
        }
        if ("SKIPPED".equals(s)) {
            return SKIPPED;
        }
        // ERROR / FAILED / FAIL / ...
        return FAIL;
    }
}
