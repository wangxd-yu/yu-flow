package org.yu.flow.module.mqtask.dto;

import lombok.Data;

import java.util.Map;

/**
 * MQ 任务手动模拟触发请求
 *
 * @author yu-flow
 */
@Data
public class FlowMqTaskSimulateRequestDTO {

    /** 模拟消息体（UTF-8 文本，JSON 会被 mqTrigger 节点自动解析） */
    private String message;

    /** 模拟消息头（可选，与真实消费 headers 注入路径一致） */
    private Map<String, Object> headers;
}
