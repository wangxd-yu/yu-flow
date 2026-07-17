package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import cn.hutool.extra.spring.SpringUtil;
import org.yu.flow.auto.druid.DynamicSqlParser;
import org.yu.flow.auto.dto.SqlAndParams;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.ExpressionEvaluator;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.DatabaseStep;
import org.yu.flow.engine.service.SqlExecutorService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.ExecutionLog;

/**
 * Database 节点执行器
 * 桥接底层 SQL 执行服务
 */
public class DatabaseNodeExecutor extends AbstractStepExecutor<DatabaseStep> {

    private SqlExecutorService sqlExecutorService;
    private final ExpressionEvaluator evaluator;

    public DatabaseNodeExecutor(SqlExecutorService sqlExecutorService, ExpressionEvaluator evaluator) {
        this.sqlExecutorService = sqlExecutorService;
        this.evaluator = evaluator;
    }

    public void setSqlExecutorService(SqlExecutorService sqlExecutorService) {
        this.sqlExecutorService = sqlExecutorService;
    }

    @Override
    public String execute(DatabaseStep step, ExecutionContext context, FlowDefinition flow) {
        try {
            // 1. 准备参数 (从 inputs 提取)
            Map<String, Object> mergeParams = prepareParams(step, context, flow);

            String sql = step.getSql();
            Object result = null;
            String sqlType = step.getSqlType();
            if (sqlType == null) {
                throw new FlowException("CONFIG_ERROR", "sqlType 不能为空");
            }
            sqlType = sqlType.toUpperCase();

            // 约定：INSERT + inputs.rows=List<Map> → JDBC 批量插入（跳过用 rows 整体去解析单行模板）
            List<Map<String, Object>> batchRows = null;
            if ("INSERT".equals(sqlType) || "BATCH_INSERT".equals(sqlType)) {
                batchRows = extractBatchRows(mergeParams.get("rows"));
            }
            boolean useBatchInsert = "BATCH_INSERT".equals(sqlType) || batchRows != null;

            // 2. 准备 SQL 和 参数（批量模式按行解析，这里只记模板）
            SqlAndParams sqlAndParams = null;
            if (!useBatchInsert) {
                sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(sql, mergeParams);
            }

            if (context.isTraceEnabled()) {
                FlowTrace trace = context.getFlowTrace();
                if (trace != null && trace.getStepLogs() != null && !trace.getStepLogs().isEmpty()) {
                    ExecutionLog currentLog = trace.getStepLogs().get(trace.getStepLogs().size() - 1);
                    if (currentLog.getInputs() == null) {
                        currentLog.setInputs(new HashMap<>());
                    }
                    if (useBatchInsert) {
                        currentLog.getInputs().put("actualSql", sql);
                        currentLog.getInputs().put("batchSize", batchRows != null ? batchRows.size() : 0);
                    } else if (sqlAndParams != null) {
                        currentLog.getInputs().put("actualSql",
                                buildDisplaySql(sqlAndParams.getSql(), sqlAndParams.getParams()));
                    }
                }
            }

            String datasourceId = step.getDatasourceId();
            if (sqlExecutorService == null) {
                try {
                    sqlExecutorService = SpringUtil.getBean(SqlExecutorService.class);
                } catch (Exception e) {
                    throw new FlowException("CONFIG_ERROR", "SqlExecutorService 未配置，且无法从 Spring 容器获取，无法执行数据库操作");
                }
            }

            if (sqlExecutorService == null) {
                throw new FlowException("CONFIG_ERROR", "SqlExecutorService 未配置，无法执行数据库操作");
            }

            switch (sqlType) {
                case "INSERT":
                case "BATCH_INSERT":
                    if (useBatchInsert) {
                        result = sqlExecutorService.executeBatchInsert(
                                datasourceId, sql, batchRows != null ? batchRows : new ArrayList<>());
                    } else {
                        result = sqlExecutorService.executeInsert(datasourceId, sqlAndParams);
                    }
                    break;
                case "UPDATE":
                case "DELETE":
                    result = sqlExecutorService.executeUpdate(datasourceId, sqlAndParams);
                    break;
                case "SELECT":
                    String returnType = step.getReturnType();
                    if (returnType == null) {
                        returnType = "OBJECT"; // 默认
                    }
                    switch (returnType.toUpperCase()) {
                         case "PAGE":
                             Pageable pageable = buildPageable(mergeParams, context);
                             result = sqlExecutorService.executePageQuery(datasourceId, sqlAndParams, pageable);
                             break;
                         case "LIST":
                             result = sqlExecutorService.executeListQuery(datasourceId, sqlAndParams, Pageable.unpaged());
                             break;
                         case "OBJECT":
                             result = sqlExecutorService.executeObjectQuery(datasourceId, sqlAndParams);
                             break;
                         default:
                             throw new FlowException("INVALID_RETURN_TYPE", "SELECT 操作不支持的返回类型: " + returnType);
                    }
                    break;
                default:
                    throw new FlowException("INVALID_SQL_TYPE", "不支持的 SQL 操作类型: " + sqlType);
            }

            // 4. 设置结果（统一使用 PortNames.OUT，不再写冗余的 "result" 别名）
            Map<String, Object> output = new HashMap<>();
            output.put(PortNames.OUT, result);
            context.setVar(step.getId(), output);

            return PortNames.OUT;

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("DB_EXECUTE_ERROR", "数据库节点执行失败: " + e.getMessage(), e);
        }
    }

    private Pageable buildPageable(Map<String, Object> params, ExecutionContext context) {
        Object pageableObj = context.getVariable("pageable");
        if (!params.containsKey("page") && !params.containsKey("size") && pageableObj instanceof Pageable) {
            return (Pageable) pageableObj;
        }

        // 默认值
        int page = 1;
        int size = 20;

        try {
            if (params.containsKey("page")) {
                page = Integer.parseInt(params.get("page").toString());
            }
            if (params.containsKey("size")) {
                size = Integer.parseInt(params.get("size").toString());
            }
        } catch (NumberFormatException e) {
            // ignore
        }

        // Spring Data PageRequest page is 0-indexed
        int pageIndex =  (page > 0) ? page - 1 : 0;
        return PageRequest.of(pageIndex, size);
    }

    private Map<String, Object> prepareParams(DatabaseStep step, ExecutionContext context, FlowDefinition flow) {
        return this.prepareInputs(step, context, flow);
    }

    /**
     * 识别批量插入行集：非空或空的 List&lt;Map&gt; 均视为批量模式；其他类型返回 null（走单条 INSERT）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractBatchRows(Object rowsObj) {
        if (!(rowsObj instanceof List)) {
            return null;
        }
        List<?> raw = (List<?>) rowsObj;
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof Map)) {
                return null;
            }
            rows.add((Map<String, Object>) item);
        }
        return rows;
    }

    private String buildDisplaySql(String preparedSql, java.util.List<Object> params) {
        if (params == null || params.isEmpty()) {
            return preparedSql;
        }
        String result = preparedSql;
        for (Object param : params) {
            String valStr = "null";
            if (param != null) {
                if (param instanceof String || param instanceof java.util.Date) {
                    valStr = "'" + param.toString().replace("'", "''") + "'";
                } else {
                    valStr = param.toString();
                }
            }
            result = result.replaceFirst("\\?", java.util.regex.Matcher.quoteReplacement(valStr));
        }
        return result;
    }
}
