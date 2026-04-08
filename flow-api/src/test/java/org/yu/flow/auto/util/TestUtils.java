package org.yu.flow.auto.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.mysql.parser.MySqlStatementParser;
import com.alibaba.druid.sql.dialect.postgresql.parser.PGSQLStatementParser;
import com.alibaba.druid.sql.parser.SQLStatementParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {
    public static void assertSqlEquals(String expected, String actual) {
        assertEquals(
                formatSql(expected),
                formatSql(actual)
        );
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    /**
     * 判断SQL语句的语法风格
     * @param sql 待判断的SQL语句
     * @return true if PostgreSQL style, false if MySQL style
     */
    private static boolean isPostgreSQLStyle(String sql) {
        // PostgreSQL特征：
        // 1. 使用LATERAL关键字
        // 2. 使用generate_series函数
        // 3. 使用json_array_elements函数
        // 4. 使用::类型转换语法
        // 5. 使用双引号作为标识符引号

        // MySQL特征：
        // 1. 使用LIMIT offset, limit语法
        // 2. 使用反引号作为标识符引号

        sql = sql.toLowerCase();

        // 检查PostgreSQL特征
        if (sql.contains("lateral") ||
            sql.contains("generate_series") ||
            sql.contains("json_array_elements") ||
            sql.contains("::") ||
            sql.contains("\"")) {
            return true;
        }

        // 检查MySQL特征
        if (sql.contains("limit ") && sql.contains(",") ||
            sql.contains("`")) {
            return false;
        }

        // 默认返回PostgreSQL风格
        return true;
    }

    private static String formatSql(String sql) {
        if(StrUtil.isBlank(sql)) return "";

        // 根据SQL风格选择合适的解析器
        boolean isPgStyle = isPostgreSQLStyle(sql);

        try {
            SQLStatementParser parser = isPgStyle ?
                new PGSQLStatementParser(sql) :
                new MySqlStatementParser(sql);
            SQLStatement stmt = parser.parseStatement();
            return stmt.toString();
        } catch (Exception e) {
            // 如果解析失败，尝试使用另一种解析器
            try {
                SQLStatementParser parser = isPgStyle ?
                    new MySqlStatementParser(sql) :
                    new PGSQLStatementParser(sql);
                SQLStatement stmt = parser.parseStatement();
                return stmt.toString();
            } catch (Exception ex) {
                // 如果两种解析器都失败，回退到简单的SQL规范化
                return normalizeSql(sql);
            }
        }
    }
}
