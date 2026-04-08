package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.ExpressionEvaluator;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.ServiceCallStep;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 本地服务调用执行器
 * 支持通过 inputs 字段使用 JSONPath 提取参数
 */
public class ServiceCallStepExecutor extends AbstractStepExecutor<ServiceCallStep> {
    private final Map<String, Object> serviceMap;
    private final ExpressionEvaluator evaluator;

    public ServiceCallStepExecutor(Map<String, Object> serviceMap, ExpressionEvaluator evaluator) {
        this.serviceMap = serviceMap;
        this.evaluator = evaluator;
    }

    @Override
    public String execute(ServiceCallStep step, ExecutionContext context, FlowDefinition flow) {
        try {
            // 1. 获取服务实例
            Object service = serviceMap.get(step.getService());
            if (service == null) {
                throw new FlowException("SERVICE_NOT_FOUND", String.format("服务'%s'未注册", step.getService()));
            }

            // 2. 获取方法对象
            Method method = findMethod(service, step.getMethod(), step.getArgs());
            method.setAccessible(true);

            // 3. 准备参数值
            Object[] args = prepareArguments(step, context, flow);

            // 4. 反射调用
            Object result = method.invoke(service, args);

            // 5. 存储结果到步骤的 result 中
            Map<String, Object> stepData = new HashMap<>();
            stepData.put(ContextKeys.RESULT, result);
            context.setVar(step.getId(), stepData);

            if (step.getOutput() != null && !step.getOutput().trim().isEmpty()) {
                context.setVar(step.getOutput(), result);
            }

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("SERVICE_CALL_FAILED", "服务调用失败: " + e.getMessage(), e);
        }
        return PortNames.OUT;
    }

    /**
     * 准备方法参数
     * 1. 如果有 inputs 配置，先从 context 中通过 JSONPath 提取值
     * 2. 然后按 args 列表顺序构建参数数组
     */
    @SuppressWarnings("unchecked")
    private Object[] prepareArguments(ServiceCallStep step, ExecutionContext context, FlowDefinition flow) {
        // 如果有 inputs 配置，先提取变量
        Map<String, Object> extractedInputs = this.prepareInputs(step, context, flow);

        // 按 args 顺序构建参数数组
        List<String> argNames = step.getArgs();
        if (argNames == null || argNames.isEmpty()) {
            return new Object[0];
        }

        Object[] result = new Object[argNames.size()];
        for (int i = 0; i < argNames.size(); i++) {
            String argName = argNames.get(i);
            if (extractedInputs.containsKey(argName)) {
                result[i] = extractedInputs.get(argName);
            } else {
                // 尝试用 evaluator 计算表达式
                try {
                    result[i] = evaluator.evaluateArguments(Collections.singletonList(argName), context)[0];
                } catch (Exception e) {
                    result[i] = null;
                }
            }
        }
        return result;
    }

    private Method findMethod(Object service, String methodName, List<String> argExpressions) throws FlowException {
        try {
            Class<?> serviceClass = service.getClass();
            if (argExpressions == null || argExpressions.isEmpty()) {
                return serviceClass.getMethod(methodName);
            }
            Method[] methods = serviceClass.getMethods();
            List<Method> candidateMethods = Arrays.stream(methods)
                    .filter(m -> m.getName().equals(methodName))
                    .collect(Collectors.toList());

            if (candidateMethods.isEmpty()) {
                throw new FlowException("METHOD_NOT_FOUND", "找不到方法 " + methodName);
            }
            return candidateMethods.get(0);
        } catch (NoSuchMethodException e) {
            throw new FlowException("METHOD_NOT_FOUND", "找不到方法 " + methodName, e);
        }
    }
}
