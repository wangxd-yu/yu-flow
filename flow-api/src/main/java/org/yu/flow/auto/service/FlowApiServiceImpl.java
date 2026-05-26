package org.yu.flow.auto.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.util.CamelCaseColumnMapRowMapper;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.auto.druid.DynamicSqlParser;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.dto.SqlAndParams;

import org.yu.flow.auto.util.RegularSqlParseUtil;
import org.yu.flow.auto.util.ValidationRule;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yu.flow.module.api.service.FlowApiCrudServiceImpl;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.regex.Pattern;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.step.ResponseResult;
import org.yu.flow.module.executionlog.domain.FlowExecutionLogDO;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.executionlog.service.FlowExecutionLogService;

/**
 * FlowApi 执行服务实现 —— 仅负责动态 API 的运行时执行逻辑（SQL 执行、参数校验、Flow 编排引擎调用等）
 *
 * <p>所有 CRUD 管理操作已迁移至 {@link FlowApiCrudServiceImpl}。
 *
 * @author yu-flow
 * @date 2025-03-05 23:55
 */
@Slf4j
@Service
public class FlowApiServiceImpl implements FlowApiExecutionService, SqlExecutorService {

    @Lazy
    @Resource
    private DynamicDataSourceService dynamicDataSourceService;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private FlowExecutionLogService flowExecutionLogService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ============================= 策略与调度配置 =============================

    @FunctionalInterface
    private interface ServiceTypeStrategy {
        Object execute(FlowApiDO apiDO, String content, Map<String, Object> params, Pageable pageable, HttpServletResponse response, FlowInputSupplier flowInputSupplier) throws Exception;
    }

    @FunctionalInterface
    private interface DbResponseStrategy {
        Object execute(FlowApiDO apiDO, SqlAndParams sqlAndParams, Pageable pageable, HttpServletResponse response) throws Exception;
    }

    private final Map<String, ServiceTypeStrategy> serviceStrategyMap = new HashMap<>();
    private final Map<String, DbResponseStrategy> dbResponseStrategyMap = new HashMap<>();

    @javax.annotation.PostConstruct
    public void initStrategies() {
        serviceStrategyMap.put("FLOW", (apiDO, content, params, pageable, response, flowInputSupplier) -> {
            FlowEngine flowEngine = new FlowEngine();
            Map<String, Object> allInputs = flowInputSupplier.get();
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("headers", new HashMap<>());
            requestMap.put("params", allInputs.get("queryParams") != null ? allInputs.get("queryParams") : new HashMap<>());
            requestMap.put("body", allInputs.get("bodyParams") != null ? allInputs.get("bodyParams") : new HashMap<>());
            
            Map<String, Object> flowArgs = new HashMap<>();
            flowArgs.put("request", requestMap);
            
            return flowEngine.execute(content, flowArgs, true);
        });

        serviceStrategyMap.put("STRING", (apiDO, content, params, pageable, response, flowInputSupplier) -> {
            if (JSONUtil.isTypeJSON(content)) {
                try {
                    return OBJECT_MAPPER.readValue(content, Object.class);
                } catch (Exception e) {
                    return content;
                }
            }
            return content;
        });

        serviceStrategyMap.put("JSON", (apiDO, content, params, pageable, response, flowInputSupplier) -> {
            try {
                return OBJECT_MAPPER.readValue(content, Object.class);
            } catch (Exception e) {
                throw new RuntimeException("JSON parsing error", e);
            }
        });

        serviceStrategyMap.put("DB", (apiDO, content, params, pageable, response, flowInputSupplier) -> {
            SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(content, params);
            return dispatchDb(apiDO, sqlAndParams, pageable, response);
        });

        serviceStrategyMap.put("EXCEL", (apiDO, content, params, pageable, response, flowInputSupplier) ->
            Collections.singletonMap("error", "EXCEL 导出暂未实现！")
        );

        dbResponseStrategyMap.put("PAGE", (apiDO, sqlAndParams, pageable, response) ->
            executePageQuery(apiDO.getDatasource(), sqlAndParams, pageable)
        );

        dbResponseStrategyMap.put("LIST", (apiDO, sqlAndParams, pageable, response) ->
            executeListQuery(apiDO.getDatasource(), sqlAndParams, pageable)
        );

        dbResponseStrategyMap.put("OBJECT", (apiDO, sqlAndParams, pageable, response) ->
            executeObjectQuery(apiDO.getDatasource(), sqlAndParams)
        );

        dbResponseStrategyMap.put("UPDATE", (apiDO, sqlAndParams, pageable, response) ->
            executeUpdate(apiDO.getDatasource(), sqlAndParams)
        );

        dbResponseStrategyMap.put("INSERT", (apiDO, sqlAndParams, pageable, response) ->
            executeInsert(apiDO.getDatasource(), sqlAndParams)
        );
    }

    // ============================= executeApi 入口 =============================

    @Override
    public Object executeApi(FlowApiDO flowApiDO, Map<String, String> queryParams, Map<String, Object> bodyParams,
                             Map<String, Object> mergeParamsMap, Pageable pageable, HttpServletResponse response) throws Exception {
        long start = System.currentTimeMillis();
        FlowExecutionLogDO logDO = buildBaseLogDO(flowApiDO);
        try {
            Map<String, Object> requestMap = new HashMap<>();
            if (queryParams != null) requestMap.put("queryParams", queryParams);
            if (bodyParams != null) requestMap.put("bodyParams", bodyParams);
            logDO.setRequestParams(OBJECT_MAPPER.writeValueAsString(requestMap));
        } catch (Exception e) {
            logDO.setRequestParams("JSON parse error");
        }
        try {
            Object result = doExecute(queryParams, bodyParams, mergeParamsMap, pageable, response, flowApiDO);
            logDO.setStatus("SUCCESS");
            return resolveFlowResult(result, logDO, flowApiDO);
        } catch (Exception e) {
            if ("SUCCESS".equals(logDO.getStatus())) {
                // resolveFlowResult 内部抛出（FlowTrace error 状态），status 已被设置
            } else {
                logDO.setStatus("ERROR");
                truncateAndSetErrorMsg(logDO, e.getMessage());
                if (!"FLOW".equals(flowApiDO.getServiceType())) {
                    buildAndSetSyntheticTraceForError(logDO, flowApiDO, e);
                }
            }
            throw e;
        } finally {
            logDO.setCostTimeMs(System.currentTimeMillis() - start);
            flowExecutionLogService.saveLogAsync(logDO);
        }
    }

    @Override
    public Object executeApi(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable,
                             HttpServletResponse response) throws Exception {
        long start = System.currentTimeMillis();
        FlowExecutionLogDO logDO = buildBaseLogDO(flowApiDO);
        try {
            if (params != null) logDO.setRequestParams(OBJECT_MAPPER.writeValueAsString(params));
        } catch (Exception e) {
            logDO.setRequestParams("JSON parse error");
        }
        try {
            Object result = doExecute(params, pageable, response, flowApiDO);
            logDO.setStatus("SUCCESS");
            return resolveFlowResult(result, logDO, flowApiDO);
        } catch (Exception e) {
            if ("SUCCESS".equals(logDO.getStatus())) {
                // resolveFlowResult 内部抛出（FlowTrace error 状态），status 已被设置
            } else {
                logDO.setStatus("ERROR");
                truncateAndSetErrorMsg(logDO, e.getMessage());
                if (!"FLOW".equals(flowApiDO.getServiceType())) {
                    buildAndSetSyntheticTraceForError(logDO, flowApiDO, e);
                }
            }
            throw e;
        } finally {
            logDO.setCostTimeMs(System.currentTimeMillis() - start);
            flowExecutionLogService.saveLogAsync(logDO);
        }
    }

    /**
     * 构建日志 DO 基础字段（共用逻辑提取）
     */
    private FlowExecutionLogDO buildBaseLogDO(FlowApiDO flowApiDO) {
        FlowExecutionLogDO logDO = new FlowExecutionLogDO();
        logDO.setApiId(flowApiDO.getId());
        logDO.setApiName(flowApiDO.getName());
        logDO.setUrl(flowApiDO.getUrl());
        logDO.setServiceType(flowApiDO.getServiceType());
        logDO.setMethod(flowApiDO.getMethod());
        return logDO;
    }

    /**
     * 截断并设置 errorMsg，防止超出 TEXT 字段长度限制（65535 字节）。
     * 保守取前 2000 字符。
     */
    private void truncateAndSetErrorMsg(
            FlowExecutionLogDO logDO, String msg) {
        if (msg == null) msg = "未知错误";
        logDO.setErrorMsg(msg.length() > 2000 ? msg.substring(0, 2000) + "..." : msg);
    }

    /**
     * 统一 FlowTrace 解包与业务响应提取（问题四：消除两个重载的代码重复；问题五：修复 null errorMsg）。
     *
     * <p>规则：
     * <ul>
     *   <li>若 result 是 FlowTrace 且状态为 error → 更新 logDO 并抛出 RuntimeException</li>
     *   <li>若 result 是 FlowTrace 且输出包含 ResponseResult → 解包为原生 ResponseEntity</li>
     *   <li>若 result 是 FlowTrace 且为普通输出 → 包装为 ExecutionResult</li>
     *   <li>其他类型（DB / JSON / STRING）→ 直接返回，仅记录 responseBody</li>
     * </ul>
     */
    private Object resolveFlowResult(
            Object result,
            FlowExecutionLogDO logDO,
            FlowApiDO flowApiDO) throws Exception {
        if (result instanceof FlowTrace) {
            FlowTrace trace = (FlowTrace) result;
            try {
                // 保存当时执行的 DSL 快照，供排障回放时精确还原现场
                if ("FLOW".equals(flowApiDO.getServiceType())) {
                    trace.setDslSnapshot(flowApiDO.getDslContent());
                }
                logDO.setTraceData(OBJECT_MAPPER.writeValueAsString(trace));
            } catch (Exception ignored) {}

            if ("error".equals(trace.getStatus())) {
                // 问题五修复：errorMsg 可能为 null，改用兜底文案
                String errMsg = trace.getErrorMsg() != null
                        ? trace.getErrorMsg() : "流程执行失败（引擎未返回具体错误信息）";
                logDO.setStatus("ERROR");
                truncateAndSetErrorMsg(logDO, errMsg);
                throw new RuntimeException(errMsg);
            }

            Object outputs = trace.getGlobalOutputs();
            if (outputs instanceof ResponseResult) {
                ResponseResult rr = (ResponseResult) outputs;
                try {
                    logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(rr.getBody()));
                } catch (Exception ignored) {}
                org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
                if (rr.getHeaders() != null) {
                    rr.getHeaders().forEach(httpHeaders::add);
                }
                int status = Integer.parseInt(String.valueOf(rr.getStatus()));
                return new org.springframework.http.ResponseEntity<>(
                        rr.getBody(), httpHeaders, org.springframework.http.HttpStatus.valueOf(status));
            } else {
                ExecutionResult er = ExecutionResult.success(outputs);
                try {
                    logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(er));
                } catch (Exception ignored) {}
                return er;
            }
        } else {
            // 非 Flow 类型的返回值处理与合成快照构建
            if (result != null) {
                try {
                    logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(result));
                } catch (Exception ignored) {}
            }
            if (!"FLOW".equals(flowApiDO.getServiceType())) {
                buildAndSetSyntheticTraceForSuccess(logDO, flowApiDO, result);
            }
        }
        return result;
    }

    private void buildAndSetSyntheticTraceForSuccess(
            FlowExecutionLogDO logDO, 
            FlowApiDO flowApiDO, 
            Object result) {
        try {
            FlowTrace trace = new FlowTrace();
            trace.setTraceId(UUID.randomUUID().toString());
            trace.setStatus("success");
            
            ExecutionLog stepLog = new ExecutionLog();
            stepLog.setId("step_1");
            stepLog.setNodeId(flowApiDO.getServiceType().toLowerCase() + "_node");
            stepLog.setNodeName(flowApiDO.getServiceType() + " 节点");
            stepLog.setNodeType("DB".equalsIgnoreCase(flowApiDO.getServiceType()) ? "database" : flowApiDO.getServiceType().toLowerCase());
            stepLog.setStatus("success");
            
            Map<String, Object> inputs = new HashMap<>();
            if (logDO.getRequestParams() != null) {
                try {
                    inputs = OBJECT_MAPPER.readValue(logDO.getRequestParams(), new TypeReference<Map<String, Object>>(){});
                } catch(Exception ignored){}
            }
            if ("DB".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("actualSql", flowApiDO.getSqlContent()); 
            } else if ("JSON".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("jsonContent", flowApiDO.getJsonContent());
            } else if ("STRING".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("textContent", flowApiDO.getTextContent());
            }
            stepLog.setInputs(inputs);
            
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", result);
            stepLog.setOutputs(outputs);
            
            trace.setStepLogs(Collections.singletonList(stepLog));
            trace.setGlobalOutputs(result);
            
            logDO.setTraceData(OBJECT_MAPPER.writeValueAsString(trace));
        } catch (Exception ignored) {}
    }

    private void buildAndSetSyntheticTraceForError(
            FlowExecutionLogDO logDO, 
            FlowApiDO flowApiDO, 
            Exception e) {
        try {
            FlowTrace trace = new FlowTrace();
            trace.setTraceId(UUID.randomUUID().toString());
            trace.setStatus("error");
            trace.setErrorMsg(e.getMessage());
            
            ExecutionLog stepLog = new ExecutionLog();
            stepLog.setId("step_1");
            stepLog.setNodeId(flowApiDO.getServiceType().toLowerCase() + "_node");
            stepLog.setNodeName(flowApiDO.getServiceType() + " 节点");
            stepLog.setNodeType("DB".equalsIgnoreCase(flowApiDO.getServiceType()) ? "database" : flowApiDO.getServiceType().toLowerCase());
            stepLog.setStatus("error");
            stepLog.setError(e.getMessage());
            
            Map<String, Object> inputs = new HashMap<>();
            if (logDO.getRequestParams() != null) {
                try {
                    inputs = OBJECT_MAPPER.readValue(logDO.getRequestParams(), new TypeReference<Map<String, Object>>(){});
                } catch(Exception ignored){}
            }
            if ("DB".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("actualSql", flowApiDO.getSqlContent()); 
            }
            stepLog.setInputs(inputs);
            
            trace.setStepLogs(Collections.singletonList(stepLog));
            logDO.setTraceData(OBJECT_MAPPER.writeValueAsString(trace));
        } catch (Exception ignored) {}
    }

    // ============================= 核心执行逻辑 =============================

    /**
     * 分离参数模式执行
     */
    private Object doExecute(Map<String, String> queryParams, Map<String, Object> bodyParams,
                             Map<String, Object> mergeParamsMap, Pageable pageable,
                             HttpServletResponse response, FlowApiDO flowApiDO) throws Exception {
        // 参数校验
        if (StrUtil.isNotBlank(flowApiDO.getContract())) {
            Map<String, List<ValidationRule>> validationRules = OBJECT_MAPPER.readValue(
                    flowApiDO.getContract(),
                    new TypeReference<Map<String, List<ValidationRule>>>() {}
            );
            validateParams(queryParams, bodyParams, mergeParamsMap, validationRules);
        }

        // 根据请求类型分发
        return dispatch(flowApiDO, mergeParamsMap, pageable, response, () -> {
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("queryParams", queryParams);
            inputsMap.put("bodyParams", bodyParams);
            inputsMap.put("mergeParams", mergeParamsMap);
            inputsMap.put("pageable", pageable);
            return inputsMap;
        });
    }

    /**
     * 合并参数模式执行
     */
    private Object doExecute(Map<String, Object> params, Pageable pageable,
                             HttpServletResponse response, FlowApiDO flowApiDO) throws Exception {
        return dispatch(flowApiDO, params, pageable, response, () -> {
            Map<String, Object> inputsMapObj = new HashMap<>();
            inputsMapObj.put("pageable", pageable);
            inputsMapObj.putAll(params);
            return inputsMapObj;
        });
    }

    /**
     * 统一分发器：根据 serviceType 路由到不同的执行策略
     */
    @FunctionalInterface
    private interface FlowInputSupplier {
        Map<String, Object> get();
    }

    private Object dispatch(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable,
                            HttpServletResponse response, FlowInputSupplier flowInputSupplier) throws Exception {
        // 根据 serviceType 精准读取对应的隔离字段
        String content = resolveContent(flowApiDO);

        ServiceTypeStrategy strategy = serviceStrategyMap.get(flowApiDO.getServiceType());
        if (strategy == null) {
            throw new UnsupportedOperationException("接口配置错误，未知的 serviceType: " + flowApiDO.getServiceType());
        }
        return strategy.execute(flowApiDO, content, params, pageable, response, flowInputSupplier);
    }

    /**
     * 根据 serviceType 精准读取对应的隔离内容字段。
     *
     * @param flowApiDO API 定义实体
     * @return 当前引擎模式对应的脚本/配置内容
     */
    private String resolveContent(FlowApiDO flowApiDO) {
        String serviceType = flowApiDO.getServiceType();
        String content;
        switch (serviceType) {
            case "FLOW":
                content = flowApiDO.getDslContent();
                break;
            case "DB":
                content = flowApiDO.getSqlContent();
                break;
            case "JSON":
                content = flowApiDO.getJsonContent();
                break;
            case "STRING":
                content = flowApiDO.getTextContent();
                break;
            default:
                content = null;
        }
        // 降级兼容：新字段为空时回退到旧 config
        return content;
    }

    /**
     * DB 类型二级分发：根据 responseType 执行不同的数据库操作
     */
    private Object dispatchDb(FlowApiDO flowApiDO, SqlAndParams sqlAndParams, Pageable pageable,
                              HttpServletResponse response) throws Exception {
        DbResponseStrategy strategy = dbResponseStrategyMap.get(flowApiDO.getResponseType());
        if (strategy == null) {
            throw new UnsupportedOperationException("业务异常：未知的 responseType: " + flowApiDO.getResponseType());
        }
        return strategy.execute(flowApiDO, sqlAndParams, pageable, response);
    }

    // ============================= 参数校验 =============================

    /**
     * 校验参数
     */
    private static void validateParams(Map<String, String> queryParams, Map<String, Object> bodyParams,
                                       Map<String, Object> mergeParamsMap,
                                       Map<String, List<ValidationRule>> validationRules) {
        for (Map.Entry<String, List<ValidationRule>> entry : validationRules.entrySet()) {
            String paramName = entry.getKey();
            List<ValidationRule> rules = entry.getValue();
            String paramValue = queryParams.get(paramName);

            for (ValidationRule rule : rules) {
                if (rule.isRequired() && (paramValue == null || paramValue.trim().isEmpty())) {
                    throw new IllegalArgumentException(rule.getMessage());
                }

                if (paramValue != null && !paramValue.trim().isEmpty()) {
                    if (rule.getPattern() != null && !Pattern.matches(rule.getPattern(), paramValue)) {
                        throw new IllegalArgumentException(rule.getMessage());
                    }
                    if (rule.getMin() != null && paramValue.length() < rule.getMin()) {
                        throw new IllegalArgumentException(rule.getMessage());
                    }
                    if (rule.getMax() != null && paramValue.length() > rule.getMax()) {
                        throw new IllegalArgumentException(rule.getMessage());
                    }
                    if (rule.getType() != null) {
                        switch (rule.getType()) {
                            case "number":
                                if (!paramValue.matches("\\d+")) {
                                    throw new IllegalArgumentException(rule.getMessage());
                                }
                                break;
                            case "date":
                                break;
                        }
                    }
                }
            }
        }
    }

    // ============================= SQL 执行器（实现 SqlExecutorService） =============================

    @Override
    public Object executePageQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable) {
        String originalSql = sqlAndParams.getSql();
        String countSql;

        if (originalSql.toUpperCase().contains("WITH")) {
            String withClause = RegularSqlParseUtil.extractWithClause(originalSql);
            String mainQuery = originalSql.substring(withClause.length());
            countSql = withClause + "SELECT COUNT(*) FROM (" + mainQuery + ") AS total";
        } else {
            countSql = "SELECT COUNT(*) FROM (" + originalSql + ") AS total";
        }

        countSql = RegularSqlParseUtil.removeOrderByClause(countSql);
        countSql = RegularSqlParseUtil.removeLimitAndOffset(countSql);

        String finalCountSql = countSql;
        Integer totalSize = dynamicDataSourceService.execute(datasource, jt -> jt.queryForObject(
                finalCountSql,
                sqlAndParams.getParams().toArray(),
                Integer.class));
        if (totalSize == null || totalSize == 0) {
            return new PageBean<>(Collections.emptyList(), pageable.getPageSize(), pageable.getPageNumber(), 0, 0L);
        }

        int totalPage = (int) Math.ceil((double) totalSize / pageable.getPageSize());
        int offset = pageable.getPageNumber() * pageable.getPageSize();

        String pageSql = sqlAndParams.getSql();
        if (pageable.getSort().isSorted()) {
            pageSql = RegularSqlParseUtil.removeOrderByClause(pageSql);
            pageSql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
        }
        // 安全性修复：强制使用 %d 整数格式化拼接，阻断任何可能的注入
        pageSql += String.format(" LIMIT %d OFFSET %d", pageable.getPageSize(), offset);

        String finalPageSql = pageSql;
        List<Map<String, Object>> content = dynamicDataSourceService.executeInTransaction(datasource, jt -> jt.query(
                finalPageSql,
                sqlAndParams.getParams().toArray(),
                new CamelCaseColumnMapRowMapper(true)));

        return new PageBean<>(content, pageable.getPageSize(), pageable.getPageNumber(), totalPage, totalSize.longValue());
    }

    @Override
    public int executeUpdate(String datasource, SqlAndParams sqlAndParams) {
        // [Demo 模式] 禁止执行 UPDATE / DELETE SQL
        demoModeGuard.checkSqlWrite("UPDATE/DELETE");
        return dynamicDataSourceService.executeInTransaction(datasource, jt -> jt.update(
                sqlAndParams.getSql(),
                sqlAndParams.getParams().toArray()
        ));
    }

    @Override
    public int executeInsert(String datasource, SqlAndParams sqlAndParams) {
        // [Demo 模式] 禁止执行 INSERT SQL
        demoModeGuard.checkSqlWrite("INSERT");
        return dynamicDataSourceService.executeInTransaction(datasource, jt -> jt.update(
                sqlAndParams.getSql(),
                sqlAndParams.getParams().toArray()
        ));
    }

    @Override
    public Object executeListQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable) {
        String pageSql = sqlAndParams.getSql();
        if (pageable.getSort().isSorted()) {
            pageSql = RegularSqlParseUtil.removeOrderByClause(pageSql);
            pageSql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
        }
        String finalPageSql = pageSql;
        return dynamicDataSourceService.execute(datasource, jt -> jt.query(
                finalPageSql,
                sqlAndParams.getParams().toArray(),
                new CamelCaseColumnMapRowMapper(true)));
    }

    @Override
    public Map<String, Object> executeObjectQuery(String datasource, SqlAndParams sqlAndParams) {
        List<Map<String, Object>> result = dynamicDataSourceService.execute(datasource, jt -> jt.query(
                sqlAndParams.getSql(),
                sqlAndParams.getParams().toArray(),
                new CamelCaseColumnMapRowMapper(true)));
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    // ============================= Excel 导出（暂留） =============================

    private void exportToExcel(String datasource, SqlAndParams sqlAndParams, Pageable pageable,
                               HttpServletResponse response) {
        try {
            String pageSql = sqlAndParams.getSql();
            if (pageable.getSort().isSorted()) {
                pageSql = RegularSqlParseUtil.removeOrderByClause(pageSql);
                pageSql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
            }

            String finalPageSql = pageSql;
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");

            dynamicDataSourceService.execute(datasource, jt -> {
                return jt.query(finalPageSql, sqlAndParams.getParams().toArray(), rs -> {
                    com.alibaba.excel.ExcelWriter excelWriter = null;
                    try {
                        excelWriter = com.alibaba.excel.EasyExcel.write(response.getOutputStream()).build();
                        com.alibaba.excel.write.metadata.WriteSheet writeSheet = com.alibaba.excel.EasyExcel.writerSheet("数据").build();

                        java.sql.ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        List<List<String>> head = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            List<String> headColumn = new ArrayList<>();
                            headColumn.add(StrUtil.toCamelCase(metaData.getColumnLabel(i)));
                            head.add(headColumn);
                        }
                        writeSheet.setHead(head);

                        List<List<Object>> dataList = new ArrayList<>(1000);
                        while (rs.next()) {
                            List<Object> row = new ArrayList<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.add(rs.getObject(i));
                            }
                            dataList.add(row);

                            if (dataList.size() >= 1000) {
                                excelWriter.write(dataList, writeSheet);
                                dataList.clear();
                            }
                        }
                        if (!dataList.isEmpty()) {
                            excelWriter.write(dataList, writeSheet);
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException("Excel导出写入异常", ex);
                    } finally {
                        if (excelWriter != null) {
                            excelWriter.finish();
                        }
                    }
                    return null;
                });
            });
        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败", e);
        }
    }
}
