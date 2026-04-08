package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.step.EvaluateStep;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluate 表达式计算节点执行器
 * 支持多表达式语言 (Aviator / SpEL)
 */
public class EvaluateStepExecutor extends AbstractExpressionStepExecutor<EvaluateStep> {

    @Override
    protected String getExpression(EvaluateStep step) {
        return step.getExpression();
    }

    @Override
    protected String getLanguage(EvaluateStep step) {
        return step.getLanguage();
    }

    @Override
    protected String handleResult(Object evalResult, EvaluateStep step, ExecutionContext context) {
        // 将结果存储到上下文 (使用 nodeId.result 格式, 同时添加 out 别名以对齐 autoFillExtractPath)
        Map<String, Object> nodeResult = new HashMap<>();
        nodeResult.put(ContextKeys.RESULT, evalResult);
        nodeResult.put(PortNames.OUT, evalResult);
        context.setVar(step.getId(), nodeResult);

        return PortNames.OUT;
    }
}
