package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import cn.hutool.extra.spring.SpringUtil;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.auto.util.InputParamsUtil;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.ApiServiceCallStep;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.Map;

/**
 * API 服务调用执行器
 */
public class ApiServiceCallStepExecutor extends AbstractStepExecutor<ApiServiceCallStep> {
    @Override
    public String execute(ApiServiceCallStep step, ExecutionContext context, FlowDefinition flow) {
        FlowApiCrudService crudService = SpringUtil.getBean(FlowApiCrudService.class);
        FlowApiExecutionService executionService = SpringUtil.getBean(FlowApiExecutionService.class);
        try {
            if (step.getArgs() != null && !step.getArgs().isEmpty()) {
                Map<String, Object> fpMap = new HashMap<>(step.getArgs().size());
                step.getArgs().forEach((key, value) -> fpMap.put(key, InputParamsUtil.resolveParam(context.getVar(), value)));
                context.setVar("@FP", fpMap);
            }

            Object ret = executionService.executeApi(crudService.findById(step.getServiceId()), context.getVar(), (Pageable) context.getVariable("pageable"), null);
            context.setVar(step.getOutput(), ret);
        } catch (Exception e) {
            throw new RuntimeException("API 调用失败: " + e.getMessage(), e);
        }
        return PortNames.OUT;
    }
}
