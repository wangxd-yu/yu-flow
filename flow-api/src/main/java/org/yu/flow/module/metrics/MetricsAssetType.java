package org.yu.flow.module.metrics;

/**
 * 计量资产类型。
 */
public enum MetricsAssetType {
    API,
    TASK,
    /** MQ 任务（消息触发流程） */
    MQ_TASK,
    SERVICE,
    /** 开放平台（第三方调用方） */
    PLATFORM,
    /** 系统内部依赖，仅保留底层可观测性，不参与业务资产排行与异常展示 */
    SYSTEM;

    public static MetricsAssetType fromPath(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("assetType required");
        }
        return MetricsAssetType.valueOf(raw.trim().toUpperCase());
    }
}
