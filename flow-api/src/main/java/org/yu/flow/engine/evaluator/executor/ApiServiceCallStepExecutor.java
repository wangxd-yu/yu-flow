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

/**
 * 内部 Flow API 编排调用执行器。
 *
 * <p>从 V3 {@code inputs} 解析参数值；若 mapping 带 {@code paramSource}
 *（query/path/body/header），分别写入 {@code @QP}/{@code @PP}/{@code @BP}/headers，
 * 供被调 FLOW 的 {@code request} 与 DB 脚本使用。同时保留扁平 {@code @FP}。
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
            throw new FlowException("API_SERVICE_ID_EMPTY",
                    "API 节点 '" + step.getId() + "' 未配置 serviceId（目标）");
        }

        String targetType = step.getTargetType();
        boolean callService = targetType != null && "service".equalsIgnoreCase(targetType.trim());

        try {
            Object ret;
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
                // 兼容旧 args
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
                ret = svcExec.executeById(step.getServiceId(), input, "CALL", true);
            } else {
                FlowApiCrudService crudService = apiCrud();
                FlowApiExecutionService executionService = apiExec();
                FlowApiDO api = crudService.findById(step.getServiceId());
                if (api == null) {
                    throw new FlowException("API_NOT_FOUND",
                            "API 节点 '" + step.getId() + "' 找不到 serviceId=" + step.getServiceId());
                }

                Map<String, Object> calleeParams = buildCalleeParams(step, context, flow);
                Object pageable = context.getVariable("pageable");
                ret = executionService.executeApi(
                        api,
                        calleeParams,
                        pageable instanceof Pageable ? (Pageable) pageable : null,
                        null);
            }

            Map<String, Object> stepResult = new HashMap<>(2);
            stepResult.put(PortNames.OUT, ret);
            context.setVar(step.getId(), stepResult);

            if (step.getOutput() != null && !step.getOutput().isBlank()) {
                context.setVar(step.getOutput(), ret);
            }
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("API_CALL_FAILED",
                    "API 调用失败 [" + step.getId() + "]: " + e.getMessage(), e);
        }

        return PortNames.OUT;
    }

    /**
     * 组装传给被调 API 的独立参数上下文（避免父流程 @QP/@BP 泄漏）。
     */
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
                // 无来源标记：仅进 @FP（兼容旧 DSL / 手写参数）
                continue;
            }
            switch (source.toLowerCase()) {
                case "query" -> queryMap.put(key, value);
                case "path" -> pathMap.put(key, value);
                case "header", "headers" -> headerMap.put(key, value);
                case "body" -> putNested(bodyMap, key, value);
                default -> {
                    /* ignore unknown */
                }
            }
        }

        // 旧 args：补齐未覆盖的 key，仅进 @FP
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
        // DB 脚本常用裸名查找
        callee.putAll(fpMap);
        return callee;
    }

    /** inputs 中各 key 的 paramSource */
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

    /** body 支持 a.b.c 点路径写入嵌套 Map */
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
