package org.yu.flow.module.metrics;

import org.junit.jupiter.api.Test;
import org.yu.flow.module.host.HostCatalogReserved;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetMetricsRecorderTest {

    @Test
    void classify_hostCatalogApiAsSystemAsset() {
        assertEquals(MetricsAssetType.SYSTEM,
                AssetMetricsRecorder.classify(MetricsAssetType.API, HostCatalogReserved.DIR_USER));
    }

    @Test
    void classify_keepsRegularAssetType() {
        assertEquals(MetricsAssetType.API,
                AssetMetricsRecorder.classify(MetricsAssetType.API, "123"));
        assertEquals(MetricsAssetType.SERVICE,
                AssetMetricsRecorder.classify(MetricsAssetType.SERVICE, HostCatalogReserved.DIR_USER));
    }
}
