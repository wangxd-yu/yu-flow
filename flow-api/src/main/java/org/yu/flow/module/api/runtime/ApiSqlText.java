package org.yu.flow.module.api.runtime;

import org.springframework.data.domain.Pageable;
import org.yu.flow.dto.SqlAndParams;
import org.yu.flow.util.RegularSqlParseUtil;

import java.util.List;
import java.util.regex.Matcher;

/**
 * 接口 DB 模式的 SQL 改写（计数、排序、分页）与日志展示。执行与日志共用同一套改写，保证日志里的 SQL 就是实际执行的。
 */
public final class ApiSqlText {

    private ApiSqlText() {
    }

    public static String countSql(String originalSql) {
        String countSql;
        if (originalSql.toUpperCase().contains("WITH")) {
            String withClause = RegularSqlParseUtil.extractWithClause(originalSql);
            String mainQuery = originalSql.substring(withClause.length());
            countSql = withClause + "SELECT COUNT(*) FROM (" + mainQuery + ") AS total";
        } else {
            countSql = "SELECT COUNT(*) FROM (" + originalSql + ") AS total";
        }
        countSql = RegularSqlParseUtil.removeOrderByClause(countSql);
        return RegularSqlParseUtil.removeLimitAndOffset(countSql);
    }

    /** 分页参数带排序时，用它替换 SQL 自带的 ORDER BY */
    public static String sortedSql(String sql, Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return sql;
        }
        return RegularSqlParseUtil.removeOrderByClause(sql)
                + " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
    }

    public static String pageSql(String originalSql, Pageable pageable) {
        int offset = pageable.getPageNumber() * pageable.getPageSize();
        // 页码与页大小只按 %d 拼接，不接受字符串
        return sortedSql(originalSql, pageable)
                + String.format(" LIMIT %d OFFSET %d", pageable.getPageSize(), offset);
    }

    /** 参数内联后的 SQL，仅用于日志展示；PAGE 同时给出计数与分页两条 */
    public static String forLog(String responseType, SqlAndParams sqlAndParams, Pageable pageable) {
        String sql = sqlAndParams.getSql();
        List<Object> params = sqlAndParams.getParams();
        if ("PAGE".equalsIgnoreCase(responseType) && pageable != null) {
            return "COUNT SQL:\n" + inline(countSql(sql), params)
                    + "\n\nPAGE SQL:\n" + inline(pageSql(sql, pageable), params);
        }
        if ("LIST".equalsIgnoreCase(responseType) && pageable != null) {
            sql = sortedSql(sql, pageable);
        }
        return inline(sql, params);
    }

    private static String inline(String preparedSql, List<Object> params) {
        if (params == null || params.isEmpty()) {
            return preparedSql;
        }
        String result = preparedSql;
        for (Object param : params) {
            result = result.replaceFirst("\\?", Matcher.quoteReplacement(literal(param)));
        }
        return result;
    }

    private static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }
}
