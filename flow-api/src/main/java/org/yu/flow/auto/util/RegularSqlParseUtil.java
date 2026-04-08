package org.yu.flow.auto.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.yu.flow.auto.dto.SqlAndParams;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yu.flow.exception.FlowException;

/**
 * @author yu-flow
 * @date 2025-05-03 15:19
 */
public class RegularSqlParseUtil {


    private static final String DB_TYPE = "mysql"; // 按需调整

    /** 安全的列名正则：仅允许字母、数字、下划线以及用于表别名的点号 */
    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\.]+$");




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
     * 构建 ORDER BY 子句
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
            // SQL 注入防护：严格校验排序字段名只能包含字母、数字、下划线
            String property = order.getProperty();
            if (!SAFE_COLUMN_PATTERN.matcher(property).matches()) {
                throw new FlowException("SQL_INJECTION_BLOCK",
                        "排序字段名包含非法字符，已拦截。字段: " + property);
            }
            orderByClause.append("`").append(property).append("`") // 排序字段
                    .append(" ")
                    .append(order.getDirection().name()); // 排序方向（ASC/DESC）
        }
        return orderByClause.toString();
    }

}
