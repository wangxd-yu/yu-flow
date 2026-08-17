package org.yu.flow.auto.util;

import org.yu.flow.exception.FlowException;
import org.springframework.data.domain.Sort;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yu-flow
 * @date 2025-05-03 15:19
 */
public class RegularSqlParseUtil {


    private static final String DB_TYPE = "mysql"; // 按需调整

    /**
     * 提取 SQL 中的所有 WITH 子句
     */
    public static String extractWithClause(String sql) {
        // 使用正则表达式匹配所有 WITH 子句
        Pattern pattern = Pattern.compile("(?i)(WITH\\s+.+?\\s+AS\\s*\\([^)]+\\)\\s*(,\\s*.+?\\s+AS\\s*\\([^)]+\\)\\s*)*)");
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 移除 SQL 中的 LIMIT 和 OFFSET 子句
     */
    public static String removeLimitAndOffset(String sql) {
        return sql.replaceAll("(?i)LIMIT\\s+\\d+\\s*(OFFSET\\s+\\d+)?", "");
    }

    /**
     * 移除 SQL 中的 ORDER BY 子句
     *
     * @param sql 原始 SQL 语句
     * @return 移除 ORDER BY 子句后的 SQL
     */
    public static String removeOrderByClause(String sql) {
        // 使用正则表达式匹配 ORDER BY 子句并移除
        // 匹配 ORDER BY 及其后的所有内容（包括函数、多列、可选的 ASC/DESC），直到遇到 `;`、`)` 或 SQL 结尾
        return sql.replaceAll("(?i)\\s+ORDER\\s+BY\\s+.*?(?:\\s+(?:ASC|DESC))?(?=\\s*(?:;|\\)|$))", "");
    }

    /**
     * 构建 ORDER BY 子句。
     *
     * <p>排序字段统一经 {@link SqlIdentifierSanitizer} 白名单校验后按段加反引号拼接，
     * 禁止将未校验的用户输入直接拼入 SQL。</p>
     *
     * @param sort 排序信息
     * @return 构建好的 ORDER BY 子句
     */
    public static String buildOrderByClause(Sort sort) {
        StringBuilder orderByClause = new StringBuilder();
        for (Sort.Order order : sort) {
            if (orderByClause.length() > 0) {
                orderByClause.append(", ");
            }
            String property = order.getProperty();
            try {
                orderByClause.append(SqlIdentifierSanitizer.quoteMysql(property));
            } catch (IllegalArgumentException e) {
                throw new FlowException("SQL_INJECTION_BLOCK",
                        "排序字段名包含非法字符，已拦截。字段: " + property);
            }
            orderByClause.append(" ").append(order.getDirection().name());
        }
        return orderByClause.toString();
    }

}
