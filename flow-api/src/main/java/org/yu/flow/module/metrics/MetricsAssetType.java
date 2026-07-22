package org.yu.flow.module.metrics;

/**
 * 计量资产类型。
 */
public enum MetricsAssetType {
    API,
    TASK,
    SERVICE;

    public static MetricsAssetType fromPath(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("assetType required");
        }
        return MetricsAssetType.valueOf(raw.trim().toUpperCase());
    }
}
