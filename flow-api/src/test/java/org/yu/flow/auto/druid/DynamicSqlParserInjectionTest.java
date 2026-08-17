package org.yu.flow.auto.druid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yu.flow.auto.dto.SqlAndParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicSqlParser 注入与标识位误用回归用例。
 *
 * <p>锁定行为：业务参数必须走 {@code ?} 绑定；标识位禁止 {@code ${}}。</p>
 */
class DynamicSqlParserInjectionTest {

    @Test
    void classicOrInjection_staysBoundParameter() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "' OR 1=1--");

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "SELECT * FROM users WHERE name = ${name}", params);

        assertEquals("SELECT * FROM users WHERE name = ?", normalize(result.getSql()));
        assertEquals(List.of("' OR 1=1--"), result.getParams());
        assertFalse(result.getSql().toUpperCase().contains("OR 1=1"));
    }

    @Test
    void likeInjection_staysBoundParameter() {
        Map<String, Object> params = new HashMap<>();
        params.put("kw", "%' OR '1'='1");

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "SELECT * FROM users WHERE name LIKE '%${kw}%'", params);

        assertTrue(result.getSql().contains("LIKE ?"));
        assertEquals(List.of("%%' OR '1'='1%"), result.getParams());
        assertFalse(result.getSql().contains("' OR "));
    }

    @Test
    void quotedLiteralPlaceholder_notTreatedAsIdentifier() {
        Map<String, Object> params = Map.of("name", "ok");
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "SELECT * FROM users WHERE name = '${name}'", params);
        assertTrue(result.getSql().contains("?"));
        assertEquals(List.of("ok"), result.getParams());
    }

    @Test
    void commentContainingFromPlaceholder_notBlocked() {
        Map<String, Object> params = Map.of("id", 1);
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "SELECT * FROM users WHERE id = ${id} -- FROM ${table}", params);
        assertTrue(result.getSql().contains("id = ?"));
        assertEquals(List.of(1), result.getParams());
    }

    @ParameterizedTest(name = "标识位 ${} 应拒绝: {0}")
    @ValueSource(strings = {
            "SELECT * FROM ${table} WHERE id = 1",
            "SELECT * FROM users u JOIN ${t} x ON u.id = x.id",
            "INSERT INTO ${table} (name) VALUES ('a')",
            "UPDATE ${table} SET name = 'a'",
            "SELECT * FROM users ORDER BY ${col}",
            "SELECT * FROM users ORDER BY id, ${col} DESC",
            "SELECT * FROM users GROUP BY ${col}",
            "SELECT u.${col} FROM users u",
            "SELECT * FROM ${schema}.users",
            "UPDATE users SET ${col} = 'x' WHERE id = 1",
            "UPDATE users SET name = 'a', ${col} = 'b' WHERE id = 1"
    })
    void identifierPlaceholder_rejected(String sql) {
        Map<String, Object> params = new HashMap<>();
        params.put("table", "users");
        params.put("t", "orders");
        params.put("col", "name");
        params.put("schema", "public");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DynamicSqlParser.parseDynamicSqlToPrepared(sql, params));
        assertTrue(ex.getMessage().contains("标识位"), ex.getMessage());
    }

    @Test
    void valuePlaceholders_stillAllowed() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "n");
        params.put("age", 18);
        params.put("ids", List.of(1, 2));

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "UPDATE users SET name = ${name} WHERE age > ${age} AND id IN (${ids})", params);

        assertEquals("UPDATE users SET name = ? WHERE age > ? AND id IN (?, ?)",
                normalize(result.getSql()));
        assertEquals(List.of("n", 18, 1, 2), result.getParams());
    }

    @Test
    void selectListValuePlaceholder_stillAllowed() {
        Map<String, Object> params = Map.of("param1", "v1", "param2", "v2");
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "SELECT \"dataTypeNum\", ${param1}, \"algoTypeNum\" FROM table WHERE column = '${param2}'",
                params);
        assertEquals("SELECT \"dataTypeNum\", ?, \"algoTypeNum\" FROM table WHERE column = ?",
                result.getSql());
        assertEquals(List.of("v1", "v2"), result.getParams());
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
