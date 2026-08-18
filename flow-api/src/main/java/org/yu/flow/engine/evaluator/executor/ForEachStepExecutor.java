package org.yu.flow.engine.evaluator.executor;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.ForEachStep;
import org.yu.flow.exception.FlowException;
import cn.hutool.extra.spring.SpringUtil;

import java.util.*;

/**
 * 串行 forEach：同步按序对每个元素执行 item 下游子流，全部完成后走 done。
 */
@Slf4j
public class ForEachStepExecutor extends AbstractStepExecutor<ForEachStep> {

    private final FlowEngine engine;

    public ForEachStepExecutor(FlowEngine engine) {
        this.engine = engine;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(ForEachStep step, ExecutionContext context, FlowDefinition flow) {
        List<?> items = resolveList(step, context, flow);
        if (items == null) {
            items = Collections.emptyList();
        }

        try {
            DemoModeGuard guard = SpringUtil.getBean(DemoModeGuard.class);
            if (guard != null) {
                guard.checkForLoopSize(items.size(), step.getId());
            }
        } catch (FlowException e) {
            throw e;
        } catch (Exception ignored) {
            // ignore
        }

        String bodyStartId = resolveFirstStepId(step.getNext().get(PortNames.ITEM));
        log.debug("ForEachStep [{}]: 串行循环 length={}, body={}", step.getId(), items.size(), bodyStartId);

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            Map<String, Object> iterOut = new HashMap<>();
            iterOut.put(PortNames.ITEM, item);
            iterOut.put("index", i);
            context.setVar(step.getId(), iterOut);

            if (bodyStartId != null) {
                try {
                    engine.runSubFlow(bodyStartId, context, flow);
                } catch (FlowException e) {
                    throw e;
                } catch (Exception e) {
                    throw new FlowException("FOREACH_BODY_FAILED",
                            "串行循环第 " + i + " 项执行失败: " + e.getMessage(),
                            step.getId(), null, e, FlowException.Severity.ERROR);
                }
            }
        }

        Map<String, Object> doneOut = new HashMap<>();
        doneOut.put("count", items.size());
        doneOut.put(PortNames.ITEM, items.isEmpty() ? null : items.get(items.size() - 1));
        context.setVar(step.getId(), doneOut);
        return PortNames.DONE;
    }

    @SuppressWarnings("unchecked")
    private List<?> resolveList(ForEachStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = prepareInputs(step, context, flow);
        if (inputs.containsKey("list") && inputs.get("list") != null) {
            return toList(inputs.get("list"));
        }
        if (inputs.containsKey("collection") && inputs.get("collection") != null) {
            return toList(inputs.get("collection"));
        }
        for (Object val : inputs.values()) {
            List<?> list = toList(val);
            if (list != null) {
                return list;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<?> toList(Object val) {
        if (val instanceof List) {
            return (List<?>) val;
        }
        if (val instanceof Object[]) {
            return Arrays.asList((Object[]) val);
        }
        return null;
    }

    private String resolveFirstStepId(Object nextTarget) {
        if (nextTarget instanceof String) {
            return (String) nextTarget;
        }
        if (nextTarget instanceof List && !((List<?>) nextTarget).isEmpty()) {
            return ((List<?>) nextTarget).get(0).toString();
        }
        return null;
    }
}
