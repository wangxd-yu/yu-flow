package org.yu.flow.module.api.runtime;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.config.SchemaValidatorService;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowDbDebugRequestDTO;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 数据库接口的调试运行：用编辑器里的草稿 SQL 执行一次并返回单步轨迹。默认在回滚事务里执行，写操作不提交。
 */
@Slf4j
@Component
public class DbApiDebugRunner {

    private static final DateTimeFormatter TRACE_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DynamicDataSourceService dynamicDataSourceService;
    private final ContractParamTypeConverter contractParamTypeConverter;
    private final SchemaValidatorService schemaValidatorService;
    private final ApiDispatcher dispatcher;

    public DbApiDebugRunner(@Lazy DynamicDataSourceService dynamicDataSourceService,
                            ContractParamTypeConverter contractParamTypeConverter,
                            SchemaValidatorService schemaValidatorService,
                            ApiDispatcher dispatcher) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.contractParamTypeConverter = contractParamTypeConverter;
        this.schemaValidatorService = schemaValidatorService;
        this.dispatcher = dispatcher;
    }

    public FlowTrace run(FlowDbDebugRequestDTO request) {
        long startMs = System.currentTimeMillis();
        String startTimeStr = LocalTime.now(ZONE_SH).format(TRACE_CLOCK);

        if (StrUtil.isBlank(request.getSqlContent())) {
            return errorTrace(startMs, startTimeStr, "SQL 内容不能为空", null, null);
        }
        if (StrUtil.isBlank(request.getDatasource())) {
            return errorTrace(startMs, startTimeStr, "请选择数据源", null, null);
        }
        if (StrUtil.isBlank(request.getResponseType())) {
            return errorTrace(startMs, startTimeStr, "请选择响应类型", null, null);
        }

        FlowApiDO apiDO = new FlowApiDO()
                .setId(request.getSourceRef())
                .setName(StrUtil.blankToDefault(request.getSourceName(), "DB Debug"))
                .setServiceType("DB")
                .setSqlContent(request.getSqlContent())
                .setDatasource(request.getDatasource())
                .setResponseType(request.getResponseType())
                .setPublishStatus(0)
                .setLogEnabled(false);

        Map<String, String> queryParams = request.getQueryParams() != null
                ? new LinkedHashMap<>(request.getQueryParams()) : new LinkedHashMap<>();
        Map<String, Object> bodyParams = parseBody(request.getBody());
        Map<String, Object> headers = new LinkedHashMap<>();
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(headers::put);
        }

        Map<String, Object> execQuery = new LinkedHashMap<>(queryParams);
        Map<String, Object> execBody = bodyParams;
        Map<String, Object> execHeaders = headers;
        Map<String, Object> execPath = new LinkedHashMap<>();

        if (StrUtil.isNotBlank(request.getContract())) {
            String contract = request.getContract();
            try {
                execQuery = contractParamTypeConverter.convertSection(contract, "query", queryParams);
                execBody = contractParamTypeConverter.convertSection(contract, "body", bodyParams);
                execHeaders = contractParamTypeConverter.convertSection(contract, "headers", headers);
                execPath = contractParamTypeConverter.convertSection(contract, "pathParams", queryParams);
                schemaValidatorService.validateFromContract(contract, execBody, execQuery, execPath, execHeaders);
            } catch (Exception e) {
                return errorTrace(startMs, startTimeStr, e.getMessage(), null, null);
            }
        }

        int page = resolveInt(request.getPage(), queryParams.get("page"), 0);
        int size = resolveInt(request.getSize(), queryParams.get("size"), 10);
        if (size <= 0) {
            size = 10;
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Map<String, Object> inputParamsMap = new HashMap<>(8);
        inputParamsMap.put("@QP", execQuery);
        inputParamsMap.put("@BP", execBody);
        inputParamsMap.put("@PP", execPath);
        inputParamsMap.put("headers", execHeaders);
        inputParamsMap.put("params", execQuery);
        inputParamsMap.put("queryParams", execQuery);
        inputParamsMap.put("body", execBody);
        inputParamsMap.put("bodyParams", execBody);

        RuntimeLogContext logContext = new RuntimeLogContext(null);
        boolean rollbackTransaction = request.getRollbackTransaction() == null
                || Boolean.TRUE.equals(request.getRollbackTransaction());
        try {
            Object result;
            if (rollbackTransaction) {
                result = dynamicDataSourceService.executeInTransactionThenRollback(request.getDatasource(), jt -> {
                    try {
                        return dispatcher.execute(apiDO, inputParamsMap, pageable, null, logContext);
                    } catch (RuntimeException re) {
                        throw re;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex.getMessage(), ex);
                    }
                });
            } else {
                result = dispatcher.execute(apiDO, inputParamsMap, pageable, null, logContext);
            }
            long endMs = System.currentTimeMillis();

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("queryParams", queryParams);
            inputs.put("bodyParams", bodyParams);
            inputs.put("headers", headers);
            inputs.put("datasource", apiDO.getDatasource());
            inputs.put("responseType", apiDO.getResponseType());
            inputs.put("rollbackTransaction", rollbackTransaction);
            inputs.put("actualSql", logContext.actualSqlOr(apiDO.getSqlContent()));

            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("result", result);
            if (rollbackTransaction) {
                outputs.put("transactionNote", "已开启事务回退：本次调试中的写操作不会提交到数据库");
            }

            ExecutionLog stepLog = dbStep("success", startTimeStr, endMs - startMs)
                    .setInputs(inputs)
                    .setOutputs(outputs);
            return new FlowTrace()
                    .setTraceId(UUID.randomUUID().toString())
                    .setStartTime(startMs)
                    .setEndTime(endMs)
                    .setTotalDurationMs(endMs - startMs)
                    .setStatus("success")
                    .setGlobalInputs(inputs)
                    .setGlobalOutputs(result)
                    .setStepLogs(Collections.singletonList(stepLog));
        } catch (Exception e) {
            log.error("DB debug run failed", e);
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("queryParams", queryParams);
            inputs.put("bodyParams", bodyParams);
            inputs.put("actualSql", logContext.actualSqlOr(apiDO.getSqlContent()));
            return errorTrace(startMs, startTimeStr, e.getMessage(), inputs, e);
        }
    }

    private static FlowTrace errorTrace(long startMs, String startTimeStr, String errorMsg,
                                        Map<String, Object> inputs, Exception e) {
        long endMs = System.currentTimeMillis();
        String msg = StrUtil.blankToDefault(errorMsg, e != null ? e.getClass().getSimpleName() : "未知错误");
        ExecutionLog stepLog = dbStep("error", startTimeStr, endMs - startMs)
                .setInputs(inputs != null ? inputs : Collections.emptyMap())
                .setError(msg);
        return new FlowTrace()
                .setTraceId(UUID.randomUUID().toString())
                .setStartTime(startMs)
                .setEndTime(endMs)
                .setTotalDurationMs(endMs - startMs)
                .setStatus("error")
                .setErrorMsg(msg)
                .setStepLogs(Collections.singletonList(stepLog));
    }

    private static ExecutionLog dbStep(String status, String startTimeStr, long durationMs) {
        return new ExecutionLog()
                .setId("step_1")
                .setNodeId("db_node")
                .setNodeName("Database")
                .setNodeType("database")
                .setStatus(status)
                .setStartTime(startTimeStr)
                .setDuration(durationMs);
    }

    private static Map<String, Object> parseBody(String body) {
        if (StrUtil.isBlank(body)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        try {
            Object parsed = OBJECT_MAPPER.readValue(body, Object.class);
            if (parsed instanceof Map) {
                return ApiParams.toObjectMap(parsed);
            }
            wrap.put("value", parsed);
        } catch (Exception e) {
            wrap.put("raw", body);
        }
        return wrap;
    }

    private static int resolveInt(Integer primary, String fromQuery, int defaultValue) {
        if (primary != null) {
            return primary;
        }
        if (StrUtil.isNotBlank(fromQuery)) {
            try {
                return Integer.parseInt(fromQuery.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
