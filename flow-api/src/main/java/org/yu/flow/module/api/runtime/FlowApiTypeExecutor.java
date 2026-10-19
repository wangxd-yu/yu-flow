package org.yu.flow.module.api.runtime;

import org.springframework.stereotype.Component;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.module.api.domain.FlowApiDO;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 流程编排接口：把网关入参整理成 request 节点读取的结构后交给流程引擎。
 */
@Component
public class FlowApiTypeExecutor implements ApiTypeExecutor {

    @Override
    public Set<String> serviceTypes() {
        return Set.of("FLOW");
    }

    @Override
    public Object execute(Invocation invocation) throws Exception {
        FlowApiDO api = invocation.api();
        Map<String, Object> allInputs = invocation.flowInputs().get();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("headers", ApiParams.toObjectMap(ApiParams.first(allInputs, "headers")));
        // query + path 一并进入 request.params，便于被调流程 $.request.params.xxx 读取
        Map<String, Object> mergedParams = new HashMap<>(
                ApiParams.toObjectMap(ApiParams.first(allInputs, "queryParams", "params", "@QP")));
        mergedParams.putAll(ApiParams.toObjectMap(ApiParams.first(allInputs, "pathParams", "@PP")));
        requestMap.put("params", mergedParams);
        requestMap.put("body", ApiParams.toObjectMap(ApiParams.first(allInputs, "bodyParams", "body", "@BP")));
        // 编排入参：供 DB/表达式风格读取；Request 节点仍以 params/body 为准
        if (allInputs.get("@FP") instanceof Map<?, ?> fp && !fp.isEmpty()) {
            requestMap.put("fp", ApiParams.toObjectMap(fp));
        }

        Map<String, Object> flowArgs = new HashMap<>();
        flowArgs.put("request", requestMap);
        flowArgs.put("pageable", allInputs.get("pageable") != null
                ? allInputs.get("pageable") : invocation.pageable());
        // 调用方身份：网关注入在 allInputs 上，需显式转发才能被编排读到（${@AUTH.userId}）
        Object authCtx = allInputs.get("@AUTH");
        if (authCtx != null) {
            flowArgs.put("@AUTH", authCtx);
        }

        boolean traceEnabled = invocation.logContext() != null && invocation.logContext().traceEnabled();
        return new FlowEngine().execute(invocation.content(), flowArgs, traceEnabled, "API",
                api.getId(), api.getName());
    }
}
