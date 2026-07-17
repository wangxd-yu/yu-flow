package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.ScheduleStep;

import java.util.HashMap;
import java.util.Map;

/**
 * Schedule 定时调度入口节点执行器
 *
 * <p>将任务元信息（taskName、cron、triggerTime）注入执行上下文，
 * 供下游节点通过 {@code $.schedule.taskName} / {@code $.schedule.triggerTime} 等路径引用。
 *
 * <p>该节点无输入端口，不接受外部参数，执行后直接返回 {@code "out"} 端口。
 */
public class ScheduleStepExecutor extends AbstractStepExecutor<ScheduleStep> {

    @Override
    public String execute(ScheduleStep step, ExecutionContext context, FlowDefinition flow) {
        // 优先使用步骤字段；调度器会通过 execute(args) 注入 taskName/cron 作为上下文变量
        String taskName = firstNonBlank(step.getTaskName(), context.getVariable("taskName"));
        String cron = firstNonBlank(step.getCron(), context.getVariable("cron"));

        Map<String, Object> scheduleData = new HashMap<>();
        scheduleData.put("taskName", taskName);
        scheduleData.put("cron", cron);
        scheduleData.put("triggerTime", System.currentTimeMillis());
        context.setVar("schedule", scheduleData);
        return PortNames.OUT;
    }

    private static String firstNonBlank(String primary, Object fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null) {
            String value = String.valueOf(fallback);
            if (!value.isBlank() && !"null".equals(value)) {
                return value;
            }
        }
        return "";
    }
}
