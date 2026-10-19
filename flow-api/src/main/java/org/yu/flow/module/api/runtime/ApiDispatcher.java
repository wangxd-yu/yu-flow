package org.yu.flow.module.api.runtime;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.config.SchemaValidatorService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.PublishedApiSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 接口运行时的参数绑定与分发：按契约做类型转换和 JSON Schema 校验，读取发布快照（或草稿）里的内容，
 * 再按 serviceType 交给对应的 {@link ApiTypeExecutor}。
 */
@Slf4j
@Component
public class ApiDispatcher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, ApiTypeExecutor> executors;
    private final ContractParamTypeConverter contractParamTypeConverter;
    private final SchemaValidatorService schemaValidatorService;

    public ApiDispatcher(List<ApiTypeExecutor> executors,
                         ContractParamTypeConverter contractParamTypeConverter,
                         SchemaValidatorService schemaValidatorService) {
        Map<String, ApiTypeExecutor> byType = new HashMap<>();
        for (ApiTypeExecutor executor : executors) {
            for (String type : executor.serviceTypes()) {
                ApiTypeExecutor existing = byType.putIfAbsent(type, executor);
                if (existing != null) {
                    throw new IllegalStateException("serviceType " + type + " 重复注册: "
                            + existing.getClass().getSimpleName() + ", " + executor.getClass().getSimpleName());
                }
            }
        }
        this.executors = Collections.unmodifiableMap(byType);
        this.contractParamTypeConverter = contractParamTypeConverter;
        this.schemaValidatorService = schemaValidatorService;
    }

    /**
     * 分离参数模式（网关入口）：query / body 分开传入，path 与 headers 在 mergeParamsMap 里。
     */
    public Object execute(FlowApiDO flowApiDO, Map<String, String> queryParams, Map<String, Object> bodyParams,
                          Map<String, Object> mergeParamsMap, Pageable pageable, HttpServletResponse response,
                          RuntimeLogContext logContext) throws Exception {
        // 已发布时读快照契约，避免草稿污染线上
        String contract = PublishedApiSnapshot.resolveContract(flowApiDO);
        Map<String, Object> typedQueryParams = contractParamTypeConverter.convertSection(contract, "query", queryParams);
        Map<String, Object> typedBodyParams = contractParamTypeConverter.convertSection(contract, "body", bodyParams);
        Map<String, Object> typedPathParams = contractParamTypeConverter.convertSection(contract, "pathParams",
                ApiParams.toObjectMap(ApiParams.first(mergeParamsMap, "pathParams", "@PP")));
        Map<String, Object> typedHeaders = contractParamTypeConverter.convertSection(contract, "headers",
                ApiParams.toObjectMap(ApiParams.first(mergeParamsMap, "headers")));

        if (StrUtil.isNotBlank(contract)) {
            schemaValidatorService.validateFromContract(
                    contract, typedBodyParams, typedQueryParams, typedPathParams, typedHeaders);
        }

        Map<String, Object> typedMergeParams = mergeParamsMap == null ? new HashMap<>() : new HashMap<>(mergeParamsMap);
        typedQueryParams.forEach((key, value) -> {
            typedMergeParams.put("query." + key, value);
            typedMergeParams.putIfAbsent(key, value);
        });
        typedBodyParams.forEach((key, value) -> {
            typedMergeParams.put("body." + key, value);
            typedMergeParams.put(key, value);
        });
        typedMergeParams.put("@PP", typedPathParams);
        typedMergeParams.put("pathParams", typedPathParams);
        typedMergeParams.put("headers", typedHeaders);

        return dispatch(flowApiDO, typedMergeParams, pageable, response, () -> {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("queryParams", typedQueryParams);
            inputs.put("bodyParams", typedBodyParams);
            inputs.put("mergeParams", typedMergeParams);
            inputs.put("pageable", pageable);
            return inputs;
        }, logContext);
    }

    /**
     * 合并参数模式（流程内调用、DB 调试运行）：query / body 已按 params、body 等键放在同一个 Map 里。
     */
    public Object execute(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable,
                          HttpServletResponse response, RuntimeLogContext logContext) throws Exception {
        Map<String, Object> typedParams = convertContextParams(PublishedApiSnapshot.resolveContract(flowApiDO), params);
        return dispatch(flowApiDO, typedParams, pageable, response, () -> {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("pageable", pageable);
            inputs.putAll(typedParams);
            return inputs;
        }, logContext);
    }

    private Map<String, Object> convertContextParams(String contract, Map<String, Object> params) {
        Map<String, Object> result = params == null ? new HashMap<>() : new HashMap<>(params);
        Object querySource = ApiParams.first(result, "params", "queryParams", "@QP");
        Object bodySource = ApiParams.first(result, "body", "bodyParams", "@BP");

        Map<String, Object> query = contractParamTypeConverter.convertSection(
                contract, "query", querySource == null ? result : ApiParams.toObjectMap(querySource));
        Map<String, Object> body = contractParamTypeConverter.convertSection(
                contract, "body", bodySource == null ? Collections.emptyMap() : ApiParams.toObjectMap(bodySource));
        Map<String, Object> headers = contractParamTypeConverter.convertSection(
                contract, "headers", ApiParams.toObjectMap(ApiParams.first(result, "headers")));
        Map<String, Object> pathParams = contractParamTypeConverter.convertSection(
                contract, "pathParams", ApiParams.toObjectMap(ApiParams.first(result, "pathParams", "@PP")));

        result.put("@QP", query);
        result.put("@BP", body);
        result.put("@PP", pathParams);
        result.put("params", query);
        result.put("queryParams", query);
        result.put("body", body);
        result.put("bodyParams", body);
        result.put("headers", headers);
        result.putAll(query);
        result.putAll(body);
        return result;
    }

    private Object dispatch(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable,
                            HttpServletResponse response, Supplier<Map<String, Object>> flowInputs,
                            RuntimeLogContext logContext) throws Exception {
        String content = resolveContent(flowApiDO);
        ApiTypeExecutor executor = executors.get(flowApiDO.getServiceType());
        if (executor == null) {
            throw new UnsupportedOperationException("接口配置错误，未知的 serviceType: " + flowApiDO.getServiceType());
        }
        return executor.execute(new ApiTypeExecutor.Invocation(
                flowApiDO, content, params, pageable, response, flowInputs, logContext));
    }

    /**
     * 当前 serviceType 对应的脚本 / 配置。已发布且有快照时从快照读，保证草稿编辑不影响线上；快照损坏时降级读草稿。
     */
    private String resolveContent(FlowApiDO flowApiDO) {
        if (flowApiDO.getPublishStatus() != null
                && flowApiDO.getPublishStatus() == 1
                && flowApiDO.getPublishedSnapshot() != null) {
            try {
                JsonNode snap = OBJECT_MAPPER.readTree(flowApiDO.getPublishedSnapshot());
                String serviceType = snap.has("serviceType") && !snap.get("serviceType").isNull()
                        ? snap.get("serviceType").asText() : flowApiDO.getServiceType();
                String field = contentField(serviceType);
                if (field == null) {
                    return null;
                }
                JsonNode node = snap.get(field);
                return node != null && !node.isNull() ? node.asText() : null;
            } catch (Exception e) {
                log.warn("[FlowApiService] 解析 publishedSnapshot 失败，降级为草稿字段读取。apiId={}", flowApiDO.getId(), e);
            }
        }
        return switch (StrUtil.nullToEmpty(flowApiDO.getServiceType())) {
            case "FLOW" -> flowApiDO.getDslContent();
            case "DB" -> flowApiDO.getSqlContent();
            case "JSON" -> flowApiDO.getJsonContent();
            case "STRING" -> flowApiDO.getTextContent();
            default -> null;
        };
    }

    private static String contentField(String serviceType) {
        return switch (StrUtil.nullToEmpty(serviceType)) {
            case "FLOW" -> "dslContent";
            case "DB" -> "sqlContent";
            case "JSON" -> "jsonContent";
            case "STRING" -> "textContent";
            default -> null;
        };
    }
}
