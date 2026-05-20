package org.yu.flow.engine.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 节点执行日志（用于调试模式）
 */
@Data
@Accessors(chain = true)
public class ExecutionLog {
    private String id;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    
    /**
     * 执行状态：success | error | running | skipped
     */
    private String status;
    private String startTime;
    private long duration; // 耗时（ms）
    
    private Map<String, Object> inputs;
    private Object outputs;
    
    private String error;
}
