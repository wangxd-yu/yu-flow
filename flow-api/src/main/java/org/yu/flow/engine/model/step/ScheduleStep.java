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
 * Schedule 定时调度入口节点
 *
 * <p>作为定时任务流程的起点，在 FlowEngine 中注册为 "schedule" 类型。
 * 不接受外部入参，执行时将任务元信息（taskName、cron、triggerTime）注入上下文，
 * 供下游节点通过 {@code $.schedule.*} 引用。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduleStep extends Step {

    /** 任务名称（由调度器透传，供下游节点引用） */
    private String taskName;

    /** Cron 表达式（元信息，仅供日志记录使用） */
    private String cron;

    @Override
    public String getType() {
        return NodeType.SCHEDULE;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
