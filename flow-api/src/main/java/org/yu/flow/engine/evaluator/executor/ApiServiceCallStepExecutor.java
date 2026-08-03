package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.auto.util.InputParamsUtil;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.ApiServiceCallStep;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowExecutionService;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 内部 Flow API 编排调用执行器（含超时 / 重试 / success·fail 软失败）。
 */
public class ApiServiceCallStepExecutor extends AbstractStepExecutor<ApiServiceCallStep> {

    private static final String PAYLOAD_KEY = "payload";

    private FlowServiceFlowExecutionService serviceFlowExecutionService;
    private FlowApiCrudService flowApiCrudService;
    private FlowApiExecutionService flowApiExecutionService;

    public void setServiceFlowExecutionService(FlowServiceFlowExecutionService serviceFlowExecutionService) {
        this.serviceFlowExecutionService = serviceFlowExecutionService;
    }

    public void setFlowApiCrudService(FlowApiCrudService flowApiCrudService) {
        this.flowApiCrudService = flowApiCrudService;
    }

    public void setFlowApiExecutionService(FlowApiExecutionService flowApiExecutionService) {
        this.flowApiExecutionService = flowApiExecutionService;
    }

    private FlowServiceFlowExecutionService serviceExec() {
        if (serviceFlowExecutionService == null) {
            throw new FlowException("API_EXECUTOR_NOT_WIRED",
                    "ApiServiceCallStepExecutor 未注入 FlowServiceFlowExecutionService");
        }
        return serviceFlowExecutionService;
    }

    private FlowApiCrudService apiCrud() {
        if (flowApiCrudService == null) {
            throw new FlowException("API_EXECUTOR_NOT_WIRED",
                    "ApiServiceCallStepExecutor 未注入 FlowApiCrudService");
        }
        return flowApiCrudService;
    }

    private FlowApiExecutionService apiExec() {
        if (flowApiExecutionService == null) {
            throw new FlowException("API_EXECUTOR_NOT_WIRED",
                    "ApiServiceCallStepExecutor 未注入 FlowApiExecutionService");
        }
        return flowApiExecutionService;
    }

    @Override
    public String execute(ApiServiceCallStep step, ExecutionContext context, FlowDefinition flow) {
        if (step.getServiceId() == null || step.getServiceId().isBlank()) {
            return fail(step, context, "API 节点 '" + step.getId() + "' 未配置 serviceId（目标）");
        }

        int extraRetries = step.getRetryCount() == null ? 0 : Math.max(0, step.getRetryCount());
        long retryIntervalMs = step.getRetryIntervalMs() == null ? 1000L : Math.max(0L, step.getRetryIntervalMs());
        int timeoutMs = step.getTimeoutMs() == null || step.getTimeoutMs() <= 0 ? 30000 : step.getTimeoutMs();
        int maxAttempts = 1 + extraRetries;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Object ret = invokeOnce(step, context, flow, timeoutMs);
                Map<String, Object> stepResult = new HashMap<>(2);
                stepResult.put(PortNames.OUT, ret);
                stepResult.put("success", true);
                context.setVar(step.getId(), stepResult);
                if (step.getOutput() != null && !step.getOutput().isBlank()) {
                    context.setVar(step.getOutput(), ret);
                }
                return PortNames.SUCCESS;
            } catch (Exception e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    if (retryIntervalMs > 0) {
                        try {
                            Thread.sleep(retryIntervalMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return fail(step, context, e.getMessage());
                        }
                    }
                }
            }
        }

        String msg = lastError != null ? lastError.getMessage() : "未知错误";
        return fail(step, context, msg);
    }

    private String fail(ApiServiceCallStep step, ExecutionContext context, String message) {
        Map<String, Object> stepResult = new LinkedHashMap<>();
        stepResult.put(PortNames.OUT, null);
        stepResult.put("error", message);
        stepResult.put("success", false);
        context.setVar(step.getId(), stepResult);
        return PortNames.FAIL;
    }

    private Object invokeOnce(ApiServiceCallStep step, ExecutionContext context,
                              FlowDefinition flow, int timeoutMs) throws Exception {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                return doCall(step, context, flow);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new FlowException("API_CALL_TIMEOUT",
                    "API 调用超时 [" + step.getId() + "] " + timeoutMs + "ms");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re && re.getCause() instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private Object doCall(ApiServiceCallStep step, ExecutionContext context, FlowDefinition flow) throws Exception {
        String targetType = step.getTargetType();
        boolean callService = targetType != null && "service".equalsIgnoreCase(targetType.trim());

        if (callService) {
            FlowServiceFlowExecutionService svcExec = serviceExec();
            Map<String, Object> prepared = prepareInputs(step, context, flow);
            Map<String, Object> input = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : prepared.entrySet()) {
                if (PAYLOAD_KEY.equals(e.getKey()) || e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                input.put(e.getKey(), e.getValue());
            }
            Map<String, String> legacyArgs = step.getArgs();
            if (legacyArgs != null && !legacyArgs.isEmpty()) {
                Map<String, Object> vars = context.getVar();
                legacyArgs.forEach((key, value) -> {
                    if (key == null || key.isBlank() || input.containsKey(key)) {
                        return;
                    }
                    input.put(key, InputParamsUtil.resolveParam(vars, value));
                });
            }
            return svcExec.executeById(step.getServiceId(), input, "CALL", true);
        }

        FlowApiCrudService crudService = apiCrud();
        FlowApiExecutionService executionService = apiExec();
        FlowApiDO api = crudService.findById(step.getServiceId());
        if (api == null) {
            throw new FlowException("API_NOT_FOUND",
                    "API 节点 '" + step.getId() + "' 找不到 serviceId=" + step.getServiceId());
        }
        Map<String, Object> calleeParams = buildCalleeParams(step, context, flow);
        Object pageable = context.getVariable("pageable");
        return executionService.executeApi(
                api,
                calleeParams,
                pageable instanceof Pageable ? (Pageable) pageable : null,
                null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildCalleeParams(
            ApiServiceCallStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> prepared = prepareInputs(step, context, flow);
        Map<String, String> sources = extractParamSources(step.getInputs());

        Map<String, Object> fpMap = new LinkedHashMap<>();
        Map<String, Object> queryMap = new LinkedHashMap<>();
        Map<String, Object> pathMap = new LinkedHashMap<>();
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        Map<String, Object> headerMap = new LinkedHashMap<>();

        for (Map.Entry<String, Object> e : prepared.entrySet()) {
            String key = e.getKey();
            if (PAYLOAD_KEY.equals(key) || key == null || key.isBlank()) {
                continue;
            }
            Object value = e.getValue();
            fpMap.put(key, value);

            String source = sources.get(key);
            if (source == null || source.isBlank()) {
                continue;
            }
            switch (source.toLowerCase()) {
                case "query" -> queryMap.put(key, value);
                case "path" -> pathMap.put(key, value);
                case "header", "headers" -> headerMap.put(key, value);
                case "body" -> putNested(bodyMap, key, value);
                default -> { /* ignore */ }
            }
        }

        Map<String, String> legacyArgs = step.getArgs();
        if (legacyArgs != null && !legacyArgs.isEmpty()) {
            Map<String, Object> vars = context.getVar();
            legacyArgs.forEach((key, value) -> {
                if (key == null || key.isBlank() || fpMap.containsKey(key)) {
                    return;
                }
                fpMap.put(key, InputParamsUtil.resolveParam(vars, value));
            });
        }

        Map<String, Object> callee = new HashMap<>();
        callee.put("@FP", fpMap);
        callee.put("@QP", queryMap);
        callee.put("@PP", pathMap);
        callee.put("@BP", bodyMap);
        callee.put("params", queryMap);
        callee.put("queryParams", queryMap);
        callee.put("pathParams", pathMap);
        callee.put("body", bodyMap);
        callee.put("bodyParams", bodyMap);
        callee.put("headers", headerMap);
        callee.putAll(fpMap);
        return callee;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractParamSources(Map<String, Object> inputsConfig) {
        Map<String, String> sources = new HashMap<>();
        if (inputsConfig == null || inputsConfig.isEmpty()) {
            return sources;
        }
        for (Map.Entry<String, Object> entry : inputsConfig.entrySet()) {
            Object config = entry.getValue();
            if (config instanceof Map) {
                Object src = ((Map<String, Object>) config).get("paramSource");
                if (src != null && !String.valueOf(src).isBlank()) {
                    sources.put(entry.getKey(), String.valueOf(src).trim());
                }
            }
        }
        return sources;
    }

    @SuppressWarnings("unchecked")
    private void putNested(Map<String, Object> root, String path, Object value) {
        if (path == null || path.isBlank()) {
            return;
        }
        if (!path.contains(".")) {
            root.put(path, value);
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String p = parts[i];
            Object next = cur.get(p);
            if (!(next instanceof Map)) {
                Map<String, Object> child = new LinkedHashMap<>();
                cur.put(p, child);
                cur = child;
            } else {
                cur = (Map<String, Object>) next;
            }
        }
        cur.put(parts[parts.length - 1], value);
    }
}
