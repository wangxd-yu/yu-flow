package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.ServiceStep;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service 内部服务入口执行器。
 *
 * <p>写出 {@code $.service.serviceName / serviceId / input / triggerTime}。
 */
public class ServiceStepExecutor extends AbstractStepExecutor<ServiceStep> {

    @Override
    public String execute(ServiceStep step, ExecutionContext context, FlowDefinition flow) {
        String serviceName = firstNonBlank(step.getServiceName(), context.getVariable("serviceName"));
        Object serviceId = context.getVariable("serviceId");
        Object input = context.getVariable("input");

        Map<String, Object> serviceData = new HashMap<>();
        serviceData.put("serviceName", serviceName != null ? serviceName : "");
        serviceData.put("serviceId", serviceId != null ? String.valueOf(serviceId) : "");
        serviceData.put("input", input instanceof Map ? input : (input != null ? input : Collections.emptyMap()));
        serviceData.put("triggerTime", System.currentTimeMillis());
        context.setVar("service", serviceData);
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
