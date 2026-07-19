package org.yu.flow.engine.evaluator.executor;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.DelayStep;
import org.yu.flow.exception.FlowException;
import cn.hutool.extra.spring.SpringUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 延迟节点执行器：Thread.sleep(delayMs)。
 */
@Slf4j
public class DelayStepExecutor extends AbstractStepExecutor<DelayStep> {

    @Override
    public String execute(DelayStep step, ExecutionContext context, FlowDefinition flow) {
        long ms = resolveDelayMs(step, context, flow);

        try {
            DemoModeGuard guard = SpringUtil.getBean(DemoModeGuard.class);
            if (guard != null) {
                guard.checkDelayMs(ms, step.getId());
            }
        } catch (FlowException e) {
            throw e;
        } catch (Exception ignored) {
            // 非 Spring 环境忽略
        }

        if (ms > 0) {
            try {
                log.debug("DelayStep [{}]: sleep {}ms", step.getId(), ms);
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FlowException("DELAY_INTERRUPTED", "延迟被中断", step.getId());
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put(PortNames.OUT, ms);
        context.setVar(step.getId(), out);
        return PortNames.OUT;
    }

    private long resolveDelayMs(DelayStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = prepareInputs(step, context, flow);
        Object fromInput = inputs.get("delayMs");
        if (fromInput != null) {
            try {
                return Math.max(0, Long.parseLong(String.valueOf(fromInput).trim()));
            } catch (NumberFormatException e) {
                throw new FlowException("INVALID_DELAY", "delayMs 无法解析为数字: " + fromInput, step.getId());
            }
        }
        Long configured = step.getDelayMs();
        return configured != null ? Math.max(0, configured) : 1000L;
    }
}
