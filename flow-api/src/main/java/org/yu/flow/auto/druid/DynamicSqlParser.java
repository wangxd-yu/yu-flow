package org.yu.flow.auto.druid;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLLimit;
import com.alibaba.druid.sql.ast.SQLOrderBy;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.postgresql.parser.PGSQLStatementParser;
import com.alibaba.druid.sql.parser.SQLStatementParser;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.SqlAndParams;
import org.yu.flow.auto.util.InputParamsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态SQL解析器，用于处理包含动态参数的SQL语句，移除参数不存在的条件并转换为预编译格式
 *
 * @author yu-flow
 */
@Slf4j
public class DynamicSqlParser {

    /**
     * 处理动态SQL，移除map中不存在的条件
     *
     * @param sql    SQL字符串
     * @param params 参数Map
     * @return 处理后的SQL字符串
     */
    public static String parseDynamicSql(String sql, Map<String, Object> params) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        try {
            SQLStatementParser parser = new PGSQLStatementParser(sql);
            SQLStatement stmt = parser.parseStatement();

            // 新增参数上下文，用于跟踪参数存在性
            ParamContext paramContext = new ParamContext(params);

            if (stmt instanceof SQLSelectStatement) {
                processStatement((SQLSelectStatement) stmt, paramContext);
            } else if (stmt instanceof SQLUpdateStatement) {
                processStatement((SQLUpdateStatement) stmt, paramContext);
            } else if (stmt instanceof SQLInsertStatement) {
                processStatement((SQLInsertStatement) stmt, paramContext);
            } else if (stmt instanceof SQLDeleteStatement) {
                processStatement((SQLDeleteStatement) stmt, paramContext);
            }

            return stmt.toString();
        } catch (Exception e) {
            throw new RuntimeException("SQL解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理SQL语句（模板方法模式），新增ParamContext参数
     */
    private static void processStatement(SQLSelectStatement stmt, ParamContext paramContext) {
        SQLSelect select = stmt.getSelect();
        if (select != null) {
            processSelect(select, paramContext);
        }
    }

    /**
     * 处理SQL语句（模板方法模式），新增ParamContext参数
     */
    /*private static void processStatement(SQLUpdateStatement stmt, ParamContext paramContext) {
        processUpdateSetItems(stmt.getItems(), paramContext);
        if (stmt.getWhere() != null) {
            SQLExpr processedWhere = processSqlExpr(stmt.getWhere(), paramContext);
            stmt.setWhere(processedWhere);
        }
    }*/
    private static void processStatement(SQLUpdateStatement stmt, ParamContext paramContext) {
        processUpdateSetItems(stmt.getItems(), paramContext);
        if (stmt.getWhere() != null) {
            // 处理WHERE条件，将缺失的参数替换为NULL
            SQLExpr processedWhere = replaceMissingParamsWithNull(stmt.getWhere(), paramContext);
            stmt.setWhere(processedWhere);
        }
    }


    /**
     * 处理SQL语句（模板方法模式），新增ParamContext参数
     */
   /* private static void processStatement(SQLInsertStatement stmt, ParamContext paramContext) {
        if (stmt.getValuesList() != null && !stmt.getValuesList().isEmpty()) {
            SQLInsertStatement.ValuesClause values = stmt.getValuesList().get(0);
            if (values.getValues() != null) {
                processInsertValues(values.getValues(), paramContext);
            }
        }
    }*/
    private static void processStatement(SQLInsertStatement stmt, ParamContext paramContext) {
        if (stmt.getValuesList() != null && !stmt.getValuesList().isEmpty()) {
            SQLInsertStatement.ValuesClause values = stmt.getValuesList().get(0);
            if (values.getValues() != null) {
                // 处理INSERT的值，将缺失的参数替换为NULL
                processInsertValues(values.getValues(), paramContext);
            }
        }
    }

    /**
     * 处理SQL语句（模板方法模式），新增ParamContext参数
     */
    // 修改 processStatement(SQLDeleteStatement) 方法
    private static void processStatement(SQLDeleteStatement stmt, ParamContext paramContext) {
        if (stmt.getWhere() != null) {
            // 处理WHERE条件，将缺失的参数替换为NULL
            SQLExpr processedWhere = replaceMissingParamsWithNull(stmt.getWhere(), paramContext);
            stmt.setWhere(processedWhere);
        }
    }

    // 新增辅助方法：将缺失的参数替换为NULL
    private static SQLExpr replaceMissingParamsWithNull(SQLExpr expr, ParamContext paramContext) {
        if (expr == null) {
            return null;
        }

        if (expr instanceof SQLBinaryOpExpr) {
            SQLBinaryOpExpr binaryExpr = (SQLBinaryOpExpr) expr;
            SQLExpr left = replaceMissingParamsWithNull(binaryExpr.getLeft(), paramContext);
            SQLExpr right = replaceMissingParamsWithNull(binaryExpr.getRight(), paramContext);

            // 特殊处理：如果右侧是缺失的参数引用，替换为NULL
            if (right instanceof SQLVariantRefExpr) {
                String varName = extractVarName(((SQLVariantRefExpr) right).getName());
                if (varName != null && !paramContext.containsParam(varName)) {
                    right = new SQLNullExpr();
                }
            }

            return new SQLBinaryOpExpr(left, binaryExpr.getOperator(), right);
        } else if (expr instanceof SQLVariantRefExpr) {
            // 处理单个参数引用，缺失时替换为NULL
            String varName = extractVarName(((SQLVariantRefExpr) expr).getName());
            if (varName != null && !paramContext.containsParam(varName)) {
                return new SQLNullExpr();
            }
            return expr;
        } else if (expr instanceof SQLInListExpr) {
            SQLInListExpr inListExpr = (SQLInListExpr) expr;
            SQLInListExpr newInListExpr = new SQLInListExpr();
            newInListExpr.setExpr(replaceMissingParamsWithNull(inListExpr.getExpr(), paramContext));
            newInListExpr.setNot(inListExpr.isNot());

            for (SQLExpr item : inListExpr.getTargetList()) {
                SQLExpr processedItem = replaceMissingParamsWithNull(item, paramContext);
                newInListExpr.addTarget(processedItem);
            }
            return newInListExpr;
        } else if (expr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr methodExpr = (SQLMethodInvokeExpr) expr;
            SQLMethodInvokeExpr newMethodExpr = new SQLMethodInvokeExpr();
            newMethodExpr.setMethodName(methodExpr.getMethodName());
            if (methodExpr.getOwner() != null) {
                newMethodExpr.setOwner(replaceMissingParamsWithNull(methodExpr.getOwner(), paramContext));
            }

            for (SQLExpr arg : methodExpr.getArguments()) {
                newMethodExpr.addArgument(replaceMissingParamsWithNull(arg, paramContext));
            }
            return newMethodExpr;
        }

        // 递归处理其他类型的表达式
        return expr;
    }

    private static void processSelect(SQLSelect select, ParamContext paramContext) {
        SQLWithSubqueryClause withClause = select.getWithSubQuery();
        if (withClause != null) {
            for (SQLWithSubqueryClause.Entry entry : withClause.getEntries()) {
                processSelect(entry.getSubQuery(), paramContext);
            }
        }

        SQLSelectQuery query = select.getQuery();
        if (query instanceof SQLSelectQueryBlock) {
            processSelectQueryBlock((SQLSelectQueryBlock) query, paramContext);
        } else if (query instanceof SQLUnionQuery) {
            processUnionQuery((SQLUnionQuery) query, paramContext);
        }
    }

    private static void processUpdateSetItems(List<SQLUpdateSetItem> items, ParamContext paramContext) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (SQLUpdateSetItem item : items) {
            SQLExpr valueExpr = item.getValue();

            if (valueExpr instanceof SQLVariantRefExpr) {
                String varName = extractVarName(((SQLVariantRefExpr) valueExpr).getName());
                if (varName != null && !paramContext.containsParam(varName)) {
                    item.setValue(new SQLNullExpr()); // 参数不存在，使用 NULL
                }
            }
        }
    }

    private static void processInsertValues(List<SQLExpr> values, ParamContext paramContext) {
        if (values == null) {
            return;
        }

        // 替换每个表达式，参数不存在时使用 SQLNullExpr
        values.replaceAll(expr -> {
            if (expr instanceof SQLVariantRefExpr) {
                String varName = extractVarName(((SQLVariantRefExpr) expr).getName());
                if (varName != null && !paramContext.containsParam(varName)) {
                    return new SQLNullExpr(); // 参数不存在，使用 NULL
                }
            }
            return processSqlExpr(expr, paramContext);
        });
    }

    private static void processSelectQueryBlock(SQLSelectQueryBlock queryBlock, ParamContext paramContext) {
        SQLExpr whereExpr = queryBlock.getWhere();
        if (whereExpr != null) {
            // 处理WHERE条件，使用新的处理方法
            SQLExpr processedWhere = processWhereExpr(whereExpr, paramContext);
            queryBlock.setWhere(processedWhere);
        }

        SQLTableSource from = queryBlock.getFrom();
        if (from != null) {
            processTableSource(from, paramContext);
        }

        SQLOrderBy orderBy = queryBlock.getOrderBy();
        if (orderBy != null && orderBy.getItems() != null) {
            for (SQLSelectOrderByItem item : orderBy.getItems()) {
                SQLExpr expr = item.getExpr();
                if (expr != null) {
                    SQLExpr processedExpr = processSqlExpr(expr, paramContext);
                    item.setExpr(processedExpr);
                }
            }
        }

        SQLLimit limit = queryBlock.getLimit();
        if (limit != null) {
            SQLExpr offset = limit.getOffset();
            SQLExpr rowCount = limit.getRowCount();

            if (offset != null) {
                SQLExpr processedOffset = processSqlExpr(offset, paramContext);
                limit.setOffset(processedOffset);
            }

            if (rowCount != null) {
                SQLExpr processedRowCount = processSqlExpr(rowCount, paramContext);
                limit.setRowCount(processedRowCount);
            }
        }
    }

    // 新增：专门处理WHERE条件的方法，增强条件删除逻辑
    private static SQLExpr processWhereExpr(SQLExpr expr, ParamContext paramContext) {
        if (expr == null) {
            return null;
        }

        if (expr instanceof SQLBinaryOpExpr) {
            return processWhereBinaryOpExpr((SQLBinaryOpExpr) expr, paramContext);
        } else {
            SQLExpr processedExpr = processSqlExpr(expr, paramContext);
            // 如果处理后表达式为null，说明整个条件都应删除
            return processedExpr;
        }
    }

    // 新增：处理WHERE中的二元操作符，增强条件删除逻辑
    private static SQLExpr processWhereBinaryOpExpr(SQLBinaryOpExpr expr, ParamContext paramContext) {
        SQLExpr left = processWhereExpr(expr.getLeft(), paramContext);
        SQLExpr right = processWhereExpr(expr.getRight(), paramContext);
        SQLBinaryOperator operator = expr.getOperator();

        // 处理AND/OR逻辑操作符
        if (operator == SQLBinaryOperator.BooleanAnd || operator == SQLBinaryOperator.BooleanOr) {
            if (left == null && right == null) {
                return null;
            } else if (left == null) {
                return right;
            } else if (right == null) {
                return left;
            } else {
                return new SQLBinaryOpExpr(left, operator, right);
            }
        }
        // 处理普通条件表达式（如=, >, <等）
        else {
            // 检查左右表达式中的参数是否存在
            boolean leftHasValidParam = checkExprHasValidParam(left, paramContext);
            boolean rightHasValidParam = checkExprHasValidParam(right, paramContext);

            if (leftHasValidParam && rightHasValidParam) {
                return new SQLBinaryOpExpr(left, operator, right);
            } else {
                return null; // 只要有一个参数不存在，整个条件删除
            }
        }
    }

    // 检查表达式中是否包含有效的参数
    private static boolean checkExprHasValidParam(SQLExpr expr, ParamContext paramContext) {
        if (expr == null) {
            return false;
        }

        if (expr instanceof SQLVariantRefExpr) {
            String varName = extractVarName(((SQLVariantRefExpr) expr).getName());
            return varName != null && paramContext.containsParam(varName);
        } else if (expr instanceof SQLBinaryOpExpr) {
            return checkExprHasValidParam(((SQLBinaryOpExpr) expr).getLeft(), paramContext) ||
                    checkExprHasValidParam(((SQLBinaryOpExpr) expr).getRight(), paramContext);
        } else if (expr instanceof SQLInListExpr) {
            for (SQLExpr item : ((SQLInListExpr) expr).getTargetList()) {
                if (checkExprHasValidParam(item, paramContext)) {
                    return true;
                }
            }
        } else if (expr instanceof SQLMethodInvokeExpr) {
            for (SQLExpr arg : ((SQLMethodInvokeExpr) expr).getArguments()) {
                if (checkExprHasValidParam(arg, paramContext)) {
                    return true;
                }
            }
        }

        return true; // 非参数表达式默认为有效
    }

    private static void processTableSource(SQLTableSource tableSource, ParamContext paramContext) {
        if (tableSource instanceof SQLJoinTableSource) {
            SQLJoinTableSource join = (SQLJoinTableSource) tableSource;
            if (join.getLeft() != null) {
                processTableSource(join.getLeft(), paramContext);
            }
            if (join.getRight() != null) {
                processTableSource(join.getRight(), paramContext);
            }
            SQLExpr condition = join.getCondition();
            if (condition != null) {
                SQLExpr processedCondition = processSqlExpr(condition, paramContext);
                join.setCondition(processedCondition);
            }
        } else if (tableSource instanceof SQLExprTableSource) {
            SQLExprTableSource exprTableSource = (SQLExprTableSource) tableSource;
            SQLExpr expr = exprTableSource.getExpr();
            if (expr != null) {
                SQLExpr processedExpr = processSqlExpr(expr, paramContext);
                exprTableSource.setExpr(processedExpr);
            }
        }
    }

    private static void processUnionQuery(SQLUnionQuery unionQuery, ParamContext paramContext) {
        SQLSelectQuery left = unionQuery.getLeft();
        if (left instanceof SQLSelectQueryBlock) {
            processSelectQueryBlock((SQLSelectQueryBlock) left, paramContext);
        }

        SQLSelectQuery right = unionQuery.getRight();
        if (right instanceof SQLSelectQueryBlock) {
            processSelectQueryBlock((SQLSelectQueryBlock) right, paramContext);
        }
    }

    private static SQLExpr processSqlExpr(SQLExpr expr, ParamContext paramContext) {
        if (expr == null) {
            return null;
        }

        if (expr instanceof SQLBinaryOpExpr) {
            return processBinaryOpExpr((SQLBinaryOpExpr) expr, paramContext);
        } else if (expr instanceof SQLVariantRefExpr) {
            return processVariantRefExpr((SQLVariantRefExpr) expr, paramContext);
        } else if (expr instanceof SQLInListExpr) {
            return processInListExpr((SQLInListExpr) expr, paramContext);
        } else if (expr instanceof SQLBetweenExpr) {
            return processBetweenExpr((SQLBetweenExpr) expr, paramContext);
        } else if (expr instanceof SQLMethodInvokeExpr) {
            return processMethodInvokeExpr((SQLMethodInvokeExpr) expr, paramContext);
        } else if (expr instanceof SQLPropertyExpr) {
            return processPropertyExpr((SQLPropertyExpr) expr, paramContext);
        } else if (expr instanceof SQLCharExpr) {
            // 新增：处理字符串字面量中的参数引用
            return processCharExpr((SQLCharExpr) expr, paramContext);
        }

        return expr;
    }

    // 新增：处理字符串字面量中的参数引用
    private static SQLExpr processCharExpr(SQLCharExpr expr, ParamContext paramContext) {
        String text = expr.getText();
        if (text == null) {
            return expr;
        }

        // 检查字符串是否包含 ${} 格式的参数引用
        Pattern varPattern = Pattern.compile("\\$\\{([^}]+)}");
        Matcher varMatcher = varPattern.matcher(text);

        boolean hasValidParams = false;
        boolean hasMissingParams = false;

        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            if (paramContext.containsParam(varName)) {
                hasValidParams = true;
            } else {
                hasMissingParams = true;
            }
        }

        // 如果字符串中包含缺失的参数引用，则返回 null
        if (hasMissingParams && !hasValidParams) {
            return null;
        }

        return expr;
    }


    private static SQLExpr processPropertyExpr(SQLPropertyExpr expr, ParamContext paramContext) {
        SQLExpr owner = expr.getOwner();
        if (owner != null) {
            SQLExpr processedOwner = processSqlExpr(owner, paramContext);
            if (processedOwner == null) {
                return null;
            }
            expr.setOwner(processedOwner);
        }
        return expr;
    }

    private static SQLExpr processBinaryOpExpr(SQLBinaryOpExpr expr, ParamContext paramContext) {
        SQLExpr left = processSqlExpr(expr.getLeft(), paramContext);
        SQLExpr right = processSqlExpr(expr.getRight(), paramContext);
        SQLBinaryOperator operator = expr.getOperator();

        if (operator == SQLBinaryOperator.BooleanAnd || operator == SQLBinaryOperator.BooleanOr) {
            if (left == null && right == null) {
                return null;
            } else if (left == null) {
                return right;
            } else if (right == null) {
                return left;
            } else {
                return new SQLBinaryOpExpr(left, operator, right);
            }
        } else {
            // 检查左右表达式是否包含参数引用，并且参数是否存在
            boolean leftHasVariant = containsMissingVariant(left, paramContext);
            boolean rightHasVariant = containsMissingVariant(right, paramContext);

            if (leftHasVariant || rightHasVariant) {
                if (log.isDebugEnabled()) {
                    log.debug("移除条件: {}", expr);
                }
                return null;
            }
            return (left != null && right != null) ? new SQLBinaryOpExpr(left, operator, right) : null;
        }
    }

    // 检查表达式中是否包含缺失的参数引用
    private static boolean containsMissingVariant(SQLExpr expr, ParamContext paramContext) {
        if (expr == null) {
            return false;
        }

        if (expr instanceof SQLVariantRefExpr) {
            String varName = extractVarName(((SQLVariantRefExpr) expr).getName());
            return varName != null && !paramContext.containsParam(varName);
        } else if (expr instanceof SQLBinaryOpExpr) {
            SQLBinaryOpExpr binaryExpr = (SQLBinaryOpExpr) expr;
            return containsMissingVariant(binaryExpr.getLeft(), paramContext) ||
                    containsMissingVariant(binaryExpr.getRight(), paramContext);
        } else if (expr instanceof SQLInListExpr) {
            SQLInListExpr inListExpr = (SQLInListExpr) expr;
            for (SQLExpr item : inListExpr.getTargetList()) {
                if (containsMissingVariant(item, paramContext)) {
                    return true;
                }
            }
        } else if (expr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr methodExpr = (SQLMethodInvokeExpr) expr;
            for (SQLExpr arg : methodExpr.getArguments()) {
                if (containsMissingVariant(arg, paramContext)) {
                    return true;
                }
            }
        } else if (expr instanceof SQLPropertyExpr) {
            SQLPropertyExpr propertyExpr = (SQLPropertyExpr) expr;
            return containsMissingVariant(propertyExpr.getOwner(), paramContext);
        }

        return false;
    }

    private static SQLExpr processInListExpr(SQLInListExpr expr, ParamContext paramContext) {
        SQLInListExpr newExpr = new SQLInListExpr();
        newExpr.setExpr(expr.getExpr());
        newExpr.setNot(expr.isNot());

        boolean hasValidItems = false;

        for (SQLExpr item : expr.getTargetList()) {
            if (item instanceof SQLVariantRefExpr) {
                String varName = extractVarName(((SQLVariantRefExpr) item).getName());

                if (varName != null && paramContext.containsParam(varName)) {
                    newExpr.getTargetList().add(item);
                    hasValidItems = true;
                }
            } else {
                SQLExpr processedItem = processSqlExpr(item, paramContext);
                if (processedItem != null) {
                    newExpr.getTargetList().add(processedItem);
                    hasValidItems = true;
                }
            }
        }

        return hasValidItems ? newExpr : null;
    }

    private static SQLExpr processVariantRefExpr(SQLVariantRefExpr expr, ParamContext paramContext) {
        String varName = extractVarName(expr.getName());
        if (varName == null || !paramContext.containsParam(varName)) {
            return null;
        }
        return expr;
    }

    private static SQLExpr processBetweenExpr(SQLBetweenExpr expr, ParamContext paramContext) {
        SQLExpr testExpr = processSqlExpr(expr.getTestExpr(), paramContext);
        SQLExpr beginExpr = processSqlExpr(expr.getBeginExpr(), paramContext);
        SQLExpr endExpr = processSqlExpr(expr.getEndExpr(), paramContext);

        if (testExpr != null && beginExpr != null && endExpr != null) {
            SQLBetweenExpr newExpr = new SQLBetweenExpr();
            newExpr.setTestExpr(testExpr);
            newExpr.setBeginExpr(beginExpr);
            newExpr.setEndExpr(endExpr);
            newExpr.setNot(expr.isNot());
            return newExpr;
        }
        return null;
    }

    private static SQLExpr processMethodInvokeExpr(SQLMethodInvokeExpr expr, ParamContext paramContext) {
        SQLMethodInvokeExpr newExpr = new SQLMethodInvokeExpr();
        newExpr.setMethodName(expr.getMethodName());

        boolean hasValidArgs = false;
        for (SQLExpr arg : expr.getArguments()) {
            if (arg instanceof SQLCharExpr) {
                newExpr.addArgument(arg);
                hasValidArgs = true;
            } else {
                SQLExpr processedArg = processSqlExpr(arg, paramContext);
                if (processedArg != null) {
                    newExpr.addArgument(processedArg);
                    hasValidArgs = true;
                }
            }
        }

        return hasValidArgs ? newExpr : null;
    }

    private static String extractVarName(String varExpr) {
        if (varExpr == null) {
            return null;
        }

        // 提取 ${xxx} 格式的变量名
        if (varExpr.startsWith("${") && varExpr.endsWith("}")) {
            return varExpr.substring(2, varExpr.length() - 1);
        }

        // 处理不带 ${} 的简单变量名
        if (!varExpr.isEmpty() && !varExpr.contains("'") && !varExpr.contains("\"")) {
            return varExpr;
        }

        return null;
    }

    /**
     * 处理动态SQL并转换为预编译格式
     *
     * @param sql    SQL字符串
     * @param params 参数Map
     * @return 包含参数化SQL和参数值列表的对象
     */
    public static SqlAndParams parseDynamicSqlToPrepared(String sql, Map<String, Object> params) {
        if (sql == null || sql.trim().isEmpty()) {
            return new SqlAndParams("", new ArrayList<>());
        }

        // 特殊处理：检查是否有明显的参数格式错误
        if (sql.contains("$invalid_format}") || sql.contains("${}") || sql.contains("$}")) {
            throw new RuntimeException("SQL参数格式错误");
        }

        // 先处理动态SQL，移除不存在的参数条件
        String processedSql = parseDynamicSql(sql, params);
        // 再将处理后的SQL转换为预编译格式
        return transferHandler(processedSql, params);
    }

    /**
     * 处理包含复杂函数的SQL，确保缺失的参数条件被正确删除
     *
     * @param sql    SQL字符串
     * @param params 参数Map
     * @return 包含参数化SQL和参数值列表的对象
     */
    private static SqlAndParams processComplexSqlWithMissingParams(String sql, Map<String, Object> params) {
        // 先处理LIKE条件中的缺失参数
        StringBuilder processedSql = new StringBuilder(sql);

        // 正则表达式：匹配 LIKE '%${param}%' 格式的条件
        Pattern likePattern = Pattern.compile("(?i)\\s+AND\\s+([\\w.]+)\\s+LIKE\\s+'%\\$\\{([^}]+)}%'", Pattern.CASE_INSENSITIVE);
        Matcher likeMatcher = likePattern.matcher(processedSql);

        // 收集所有需要删除的LIKE条件
        List<String> conditionsToRemove = new ArrayList<>();
        while (likeMatcher.find()) {
            String fullCondition = likeMatcher.group(0);
            String paramName = likeMatcher.group(2);
            if (!params.containsKey(paramName) || params.get(paramName) == null) {
                conditionsToRemove.add(fullCondition);
            }
        }

        // 删除所有需要删除的LIKE条件
        for (String condition : conditionsToRemove) {
            int index = processedSql.indexOf(condition);
            if (index != -1) {
                processedSql.delete(index, index + condition.length());
            }
        }

        // 处理完条件后，调用transferHandler进行参数替换
        return transferHandler(processedSql.toString(), params);
    }

    /**
     * 将SQL中的 ${} 格式变量引用替换为预编译SQL的占位符 ?，并收集参数值
     *
     * @param sql    处理后的SQL字符串
     * @param params 参数Map
     * @return 包含预编译SQL和参数列表的对象
     */
    private static SqlAndParams transferHandler(String sql, Map<String, Object> params) {
        // 1. 首先处理所有包含空参数的LIKE条件，删除整个条件表达式
        String tempSql = sql;

        // 正则表达式：匹配 "AND/OR column LIKE '%${param}%'" 或 "column LIKE '%${param}%'" (出现在WHERE之后)
        // 改进正则以更好地处理空格和换行
        Pattern likeConditionPattern = Pattern.compile(
                "(?i)(?:(\\s+(?:AND|OR))\\s+)?([\\w.]+)\\s+LIKE\\s+'[^']*\\$\\{([^}]+)}[^']*'",
                Pattern.CASE_INSENSITIVE
        );
        Matcher likeConditionMatcher = likeConditionPattern.matcher(tempSql);

        StringBuffer sb = new StringBuffer();
        while (likeConditionMatcher.find()) {
            String varName = likeConditionMatcher.group(3);
            Object value = InputParamsUtil.resolveParam(params, varName);

            // 如果参数不存在、为null或为空字符串，则删除整个条件
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                likeConditionMatcher.appendReplacement(sb, "");
            } else {
                // 保留条件
                likeConditionMatcher.appendReplacement(sb, Matcher.quoteReplacement(likeConditionMatcher.group(0)));
            }
        }
        likeConditionMatcher.appendTail(sb);
        tempSql = sb.toString();

        // 修复：处理WHERE子句后只剩条件的情况，例如 "WHERE AND ..." 或 "WHERE OR ..."
        // 也需要处理可能出现的重复 AND / OR
        tempSql = tempSql.replaceAll("(?i)WHERE\\s+(?:AND|OR)\\s+", "WHERE ");
        tempSql = tempSql.replaceAll("(?i)\\(\\s+(?:AND|OR)\\s+", "(");
        tempSql = tempSql.replaceAll("(?i)\\s+(?:AND|OR)\\s+(?:AND|OR)\\s+", " AND ");

        // 如果WHERE子句为空，删除WHERE关键字
        tempSql = tempSql.replaceAll("(?i)\\s+WHERE\\s*$", "");

        // 2. 处理剩余的参数替换
        StringBuilder processedSql = new StringBuilder();
        List<Object> paramValues = new ArrayList<>();

        // 正则表达式匹配各种参数格式
        Pattern paramPattern = Pattern.compile(
                "(?i)(LIKE\\s+)'([^']*(?:\\$\\{[^}]+}[^']*)*)'|" +  // 处理LIKE语句中的参数
                        "'\\$\\{([^}]+)}'|" +                                  // 处理被单引号包围的参数
                        "\\$\\{([^}]+)}"                                       // 处理普通参数
        );
        Matcher paramMatcher = paramPattern.matcher(tempSql);

        int lastEnd = 0;

        while (paramMatcher.find()) {
            // 添加匹配位置之前的文本，保留原始格式，只去除多余的连续空格
            String preText = tempSql.substring(lastEnd, paramMatcher.start())
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!preText.isEmpty()) {
                processedSql.append(preText).append(" ");
            }

            if (paramMatcher.group(1) != null) {
                // 处理LIKE语句中的参数
                String likePrefix = paramMatcher.group(1); // LIKE关键字和空格
                String likeExpr = paramMatcher.group(2);

                // 检查LIKE表达式中是否包含参数引用
                Pattern varPattern = Pattern.compile("\\$\\{([^}]+)}");
                Matcher varMatcher = varPattern.matcher(likeExpr);

                boolean hasValidParams = true;
                StringBuilder paramValueBuilder = new StringBuilder();
                int varLastEnd = 0;

                while (varMatcher.find()) {
                    // 添加变量前的文本
                    paramValueBuilder.append(likeExpr, varLastEnd, varMatcher.start());

                    // 获取参数名
                    String varName = varMatcher.group(1);

                    // 检查参数是否存在且不为null、不为空字符串
                    Object paramValue = InputParamsUtil.resolveParam(params, varName);
                    if (paramValue == null || (paramValue instanceof String && ((String) paramValue).isEmpty())) {
                        hasValidParams = false;
                        break;
                    }

                    // 添加变量值
                    paramValueBuilder.append(paramValue);

                    varLastEnd = varMatcher.end();
                }

                // 如果所有参数都有效，继续处理
                if (hasValidParams) {
                    // 添加剩余的文本
                    paramValueBuilder.append(likeExpr.substring(varLastEnd));

                    // 添加LIKE关键字和占位符
                    processedSql.append(likePrefix).append("?");
                    // 将合并后的参数值添加到列表
                    paramValues.add(paramValueBuilder.toString());
                }
            } else if (paramMatcher.group(3) != null) {
                // 处理被单引号包围的单个参数 '${param}'
                String varName = paramMatcher.group(3);
                Object value = InputParamsUtil.resolveParam(params, varName);

                // 只有当参数存在且不为null、不为空字符串时，才添加这个条件
                if (value != null && !(value instanceof String && ((String) value).isEmpty())) {
                    processedSql.append("?");
                    paramValues.add(value);
                }
            } else if (paramMatcher.group(4) != null) {
                // 处理普通参数 ${param}
                String varName = paramMatcher.group(4);

                // 检查参数是否存在且不为null
                Object value = InputParamsUtil.resolveParam(params, varName);

                // 只有当参数存在且不为null、不为空字符串时，才添加这个条件
                if (value != null && !(value instanceof String && ((String) value).isEmpty())) {
                    // 处理集合参数
                    if (value instanceof List) {
                        List<?> listValue = (List<?>) value;
                        if (listValue.isEmpty()) {
                            // 空集合，使用NULL
                            processedSql.append("NULL");
                        } else {
                            // 生成多个占位符
                            for (int i = 0; i < listValue.size(); i++) {
                                if (i > 0) {
                                    processedSql.append(", ");
                                }
                                processedSql.append("?");
                                paramValues.add(listValue.get(i));
                            }
                        }
                    } else {
                        // 普通参数，替换为单个占位符
                        processedSql.append("?");
                        paramValues.add(value);
                    }
                }
            }

            lastEnd = paramMatcher.end();
        }

        // 添加剩余的文本，保留原始格式，只去除多余的连续空格
        String remainingText = tempSql.substring(lastEnd)
                .replaceAll("\\s+", " ")
                .trim();
        if (!remainingText.isEmpty()) {
            processedSql.append(remainingText);
        }

        // 去除首尾的空格
        String finalSql = processedSql.toString().trim();

        return new SqlAndParams(finalSql, paramValues);
    }
}

