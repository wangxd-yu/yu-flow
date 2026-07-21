package org.yu.flow.engine.model;

import org.yu.flow.config.YuFlowProperties;

/**
 * Trace 变量快照限深 / 限长配置。
 */
public record TraceSnapshotLimits(
        int maxDepth,
        int maxCollectionSize,
        int maxStringLength,
        int maxMapEntries
) {
    public static final TraceSnapshotLimits DEFAULTS = new TraceSnapshotLimits(6, 32, 1024, 64);

    public static TraceSnapshotLimits from(YuFlowProperties.Engine engine) {
        if (engine == null) {
            return DEFAULTS;
        }
        return new TraceSnapshotLimits(
                Math.max(1, engine.getTraceSnapshotMaxDepth()),
                Math.max(1, engine.getTraceSnapshotMaxCollectionSize()),
                Math.max(64, engine.getTraceSnapshotMaxStringLength()),
                Math.max(1, engine.getTraceSnapshotMaxMapEntries())
        );
    }
}
