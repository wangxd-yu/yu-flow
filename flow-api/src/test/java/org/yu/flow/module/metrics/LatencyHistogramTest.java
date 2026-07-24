package org.yu.flow.module.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LatencyHistogramTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void indexOf_bounds() {
        assertEquals(0, LatencyHistogram.indexOf(0));
        assertEquals(0, LatencyHistogram.indexOf(5));
        assertEquals(1, LatencyHistogram.indexOf(6));
        assertEquals(LatencyHistogram.BUCKET_COUNT - 1, LatencyHistogram.indexOf(Long.MAX_VALUE));
    }

    @Test
    void mergeAndJsonRoundTrip() {
        long[] a = LatencyHistogram.empty();
        long[] b = LatencyHistogram.empty();
        LatencyHistogram.add(a, 10, 2);
        LatencyHistogram.add(b, 10, 3);
        LatencyHistogram.merge(a, b);
        assertEquals(5, a[LatencyHistogram.indexOf(10)]);

        String json = LatencyHistogram.toJson(a, mapper);
        long[] back = LatencyHistogram.fromJson(json, mapper);
        assertArrayEquals(a, back);
    }

    @Test
    void percentile_emptyIsNull() {
        assertNull(LatencyHistogram.percentile(LatencyHistogram.empty(), 0.95));
    }
}
