package org.yu.flow.module.metrics;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MetricsKeysTest {

    @Test
    void parseBucketKey_ok() {
        LocalDateTime bucket = LocalDateTime.of(2026, 7, 22, 10, 30);
        String key = MetricsKeys.minuteBucketKey(MetricsAssetType.API, "aid1", "_", bucket);
        MetricsKeys.BucketKeyParts parts = MetricsKeys.parseBucketKey(key);
        assertNotNull(parts);
        assertEquals(MetricsAssetType.API, parts.assetType());
        assertEquals("aid1", parts.assetId());
        assertEquals("_", parts.triggerType());
        assertEquals(bucket, parts.bucketStart());
        assertEquals(key, parts.redisKey());
    }

    @Test
    void parseBucketKey_invalid() {
        assertNull(MetricsKeys.parseBucketKey(null));
        assertNull(MetricsKeys.parseBucketKey("flow:metrics:meta:API:x"));
        assertNull(MetricsKeys.parseBucketKey("flow:metrics:m:BAD:x:_:202607221030"));
    }

    @Test
    void parseMetaKey_ok() {
        String key = MetricsKeys.metaKey(MetricsAssetType.PLATFORM, "pid9");
        MetricsKeys.MetaKeyParts parts = MetricsKeys.parseMetaKey(key);
        assertNotNull(parts);
        assertEquals(MetricsAssetType.PLATFORM, parts.assetType());
        assertEquals("pid9", parts.assetId());
        assertEquals(key, parts.redisKey());
    }

    @Test
    void parseMetaKey_invalid() {
        assertNull(MetricsKeys.parseMetaKey(null));
        assertNull(MetricsKeys.parseMetaKey("flow:metrics:m:API:x:_:202607221030"));
        assertNull(MetricsKeys.parseMetaKey("flow:metrics:meta:NOPE:x"));
    }
}
