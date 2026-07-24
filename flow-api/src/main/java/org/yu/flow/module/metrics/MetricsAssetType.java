package org.yu.flow.module.metrics;

/**
 * 计量资产类型。
 */
public enum MetricsAssetType {
    API,
    TASK,
    SERVICE,
    /** 开放平台（第三方调用方） */
    PLATFORM;

    public static MetricsAssetType fromPath(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("assetType required");
        }
        return MetricsAssetType.valueOf(raw.trim().toUpperCase());
    }
}
