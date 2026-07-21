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

    /**
     * 历史字段：曾内嵌完整 DSL。落库时改为 null，回放请用 {@link #dslContentHash} + 资产当前内容。
     */
    private String dslSnapshot;

    /**
     * 执行当时 DSL 的 SHA-256（内容寻址引用，不存全文）。
     */
    private String dslContentHash;

    private Map<String, Object> globalInputs;
    private Object globalOutputs;
    
    private List<ExecutionLog> stepLogs;
}
