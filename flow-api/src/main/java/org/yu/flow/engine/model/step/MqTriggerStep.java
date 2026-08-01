package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.Arrays;
import java.util.List;

/**
 * MqTrigger 消息队列触发入口节点
 *
 * <p>作为 MQ 任务流程的起点，在 FlowEngine 中注册为 "mqTrigger" 类型。
 * 真实的连接 / topic / 消费组配置在「MQ 任务」资产上（与 schedule 节点的 cron 在任务资产上同理），
 * 消费者收到消息后经 execute(args) 注入消息元信息，执行时写入上下文，
 * 供下游节点通过 {@code $.mq.message} / {@code $.mq.headers} / {@code $.mq.topic} 等路径引用。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MqTriggerStep extends Step {

    /** Topic 提示（仅画布展示用元信息，实际订阅配置在 MQ 任务资产上） */
    private String topicHint;

    @Override
    public String getType() {
        return NodeType.MQ_TRIGGER;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
