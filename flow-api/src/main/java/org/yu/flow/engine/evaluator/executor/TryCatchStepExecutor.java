package org.yu.flow.engine.evaluator.executor;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.TryCatchStep;
import org.yu.flow.exception.FlowException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * tryCatch：同步执行 try 子流；异常写入 error 并走 catch 口。
 */
@Slf4j
public class TryCatchStepExecutor extends AbstractStepExecutor<TryCatchStep> {

    private final FlowEngine engine;

    public TryCatchStepExecutor(FlowEngine engine) {
        this.engine = engine;
    }

    @Override
    public String execute(TryCatchStep step, ExecutionContext context, FlowDefinition flow) {
        String tryStart = resolveFirstStepId(step.getNext().get(PortNames.TRY));
        try {
            if (tryStart != null) {
                engine.runSubFlow(tryStart, context, flow);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            context.setVar(step.getId(), out);
            return PortNames.OUT;
        } catch (Exception e) {
            log.warn("TryCatch [{}] 子流异常: {}", step.getId(), e.getMessage());
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", e.getMessage());
            err.put("code", e instanceof FlowException fe ? fe.getErrorCode() : "TRY_CATCH_ERROR");
            err.put("stepId", step.getId());
            context.setVar("error", err);
            Map<String, Object> stepOut = new LinkedHashMap<>();
            stepOut.put("success", false);
            stepOut.put("error", err);
            context.setVar(step.getId(), stepOut);
            return PortNames.CATCH;
        }
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
