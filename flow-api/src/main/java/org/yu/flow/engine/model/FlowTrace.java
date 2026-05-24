package org.yu.flow.engine.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 完整流程执行追踪链路（用于调试模式）
 */
@Data
@Accessors(chain = true)
public class FlowTrace {
    private String traceId;
    private long startTime;
    private long endTime;
    private long totalDurationMs;
    
    /**
     * success | error
     */
    private String status;
    private String errorMsg;
    private String dslSnapshot;
    
    private Map<String, Object> globalInputs;
    private Object globalOutputs;
    
    private List<ExecutionLog> stepLogs;
}
