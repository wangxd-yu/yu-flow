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

import java.util.HashMap;
import java.util.Map;

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

            // 2. 准备 SQL 和 参数
            String sql = step.getSql();
            // 解析动态 SQL (根据 FlowApiServiceImpl 逻辑)
            SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(sql, mergeParams);

            // 3. 执行操作
            Object result = null;
            String sqlType = step.getSqlType();
            if (sqlType == null) {
                // 如果为空，尝试兼容旧逻辑或抛出异常；这里直接假定必须有
                throw new FlowException("CONFIG_ERROR", "sqlType 不能为空");
            }
            sqlType = sqlType.toUpperCase();

            // 智能容错：根据 SQL 实际内容的首个关键字判定类型，防止前端 sqlType 配置（如下拉框未切换）与实际 SQL 相矛盾
            //String cleanSql = sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--.*", "").trim().toUpperCase();
            /*if (cleanSql.startsWith("INSERT")) {
                sqlType = "INSERT";
            } else if (cleanSql.startsWith("UPDATE")) {
                sqlType = "UPDATE";
            } else if (cleanSql.startsWith("DELETE")) {
                sqlType = "DELETE";
            } else if (cleanSql.startsWith("SELECT")) {
                sqlType = "SELECT";
            }*/

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
                    result = sqlExecutorService.executeInsert(datasourceId, sqlAndParams);
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
                             Pageable pageable = buildPageable(mergeParams);
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

            // 4. 设置结果
            Map<String, Object> output = new HashMap<>();
            output.put(PortNames.OUT, result);
            output.put(ContextKeys.RESULT, result);
            context.setVar(step.getId(), output);

            return PortNames.OUT;

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("DB_EXECUTE_ERROR", "数据库节点执行失败: " + e.getMessage(), e);
        }
    }

    private Pageable buildPageable(Map<String, Object> params) {
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
}
