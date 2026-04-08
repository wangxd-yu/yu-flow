package org.yu.flow.auto.druid;

import cn.hutool.core.collection.ListUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yu.flow.auto.dto.SqlAndParams;
import org.yu.flow.auto.util.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DynamicSqlParserJupiterTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicSqlParserJupiterTest.class);

    // 基础测试用例：参数化SQL转换与参数验证
    @Test
    void testBasicConditionRemoval() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("age", 25);

        String sql = "SELECT * FROM users WHERE name = ${name} AND age > ${age} AND department = ${dept}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM users WHERE name = ? AND age > ?", result.getSql());
        List<Object> expectedParams = Arrays.asList("张三", 25);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试WITH子句参数化
    @Test
    void testWithClause() {
        Map<String, Object> params = new HashMap<>();
        params.put("region", "华东");

        String sql = "WITH temp AS (SELECT * FROM employees WHERE region = ${region}) " +
                "SELECT * FROM temp WHERE age > ${age}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("WITH temp AS (SELECT * FROM employees WHERE region = ?) " +
                "SELECT * FROM temp", result.getSql());
        List<Object> expectedParams = Arrays.asList("华东");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试WITH子句条件参数缺失过滤
    @Test
    void testWithClauseConditionRemoval() {
        Map<String, Object> params = new HashMap<>();
        params.put("region", "华东");

        String sql = "WITH temp AS (SELECT * FROM employees WHERE region = ${region} AND dept = ${dept}) " +
                "SELECT * FROM temp WHERE age > ${age}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("WITH temp AS (SELECT * FROM employees WHERE region = ?) " +
                "SELECT * FROM temp", result.getSql());
        List<Object> expectedParams = Arrays.asList("华东");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试UNION查询参数化
    @Test
    void testUnionQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("dept1", "研发部");
        params.put("status", "active");

        String sql = "SELECT * FROM employees WHERE department = ${dept1} " +
                "UNION " +
                "SELECT * FROM former_employees WHERE status = ${status} AND join_date > ${date}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM employees WHERE department = ? " +
                "UNION " +
                "SELECT * FROM former_employees WHERE status = ?", result.getSql());
        List<Object> expectedParams = Arrays.asList("研发部", "active");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试子查询参数化
    @Test
    void testSubQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("min_salary", 10000);
        params.put("title", "经理");

        String sql = "SELECT * FROM employees WHERE salary > (SELECT AVG(salary) FROM positions WHERE title = ${title}) " +
                "AND department = ${dept}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM employees WHERE salary > (SELECT AVG(salary) FROM positions WHERE title = ?)", result.getSql());
        List<Object> expectedParams = Arrays.asList("经理");
        assertEquals(expectedParams, result.getParams());
    }

    // 参数化测试：LIKE条件参数化
    @ParameterizedTest
    @MethodSource("likeConditionProvider")
    void testLikeConditions(String input, String expectedSql, Map<String, Object> params, List<Object> expectedParams) {
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(input, params);
        TestUtils.assertSqlEquals(expectedSql, result.getSql());
        assertEquals(expectedParams, result.getParams());
    }

    static Stream<Arguments> likeConditionProvider() {
        Map<String, Object> params1 = new HashMap<>();
        params1.put("name_part", "张");
        params1.put("code_pattern", "A1%");

        Map<String, Object> params2 = new HashMap<>();
        params2.put("search_term", "技术");

        return Stream.of(
                arguments(
                        "SELECT * FROM users WHERE name LIKE '%${name_part}%' AND code LIKE '${code_pattern}' AND status = ${status}",
                        "SELECT * FROM users WHERE name LIKE ? AND code LIKE ?",
                        params1,
                        Arrays.asList("%张%", "A1%")
                ),
                arguments(
                        "SELECT * FROM articles WHERE description LIKE '%${search_term}%' OR title LIKE '%${search_term}%'",
                        "SELECT * FROM articles WHERE description LIKE ? OR title LIKE ?",
                        params2,
                        Arrays.asList("%技术%", "%技术%")
                )
        );
    }

    // 参数化测试：不同LIKE模式参数化
    @ParameterizedTest
    @MethodSource("likePatternProvider")
    void testLikePatterns(String input, String expectedSql, Map<String, Object> params, List<Object> expectedParams) {
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(input, params);
        TestUtils.assertSqlEquals(expectedSql, result.getSql());
        assertEquals(expectedParams, result.getParams());
    }

    static Stream<Arguments> likePatternProvider() {
        Map<String, Object> params = new HashMap<>();
        params.put("prefix", "张");
        params.put("suffix", "工");
        params.put("contains", "技");
        params.put("exact", "经理");

        return Stream.of(
                arguments(
                        "SELECT * FROM users WHERE role LIKE '${suffix}%'",
                        "SELECT * FROM users WHERE role LIKE ?",
                        params,
                        Arrays.asList("工%")
                ),
                arguments(
                        "SELECT * FROM users WHERE name LIKE '%${prefix}'",
                        "SELECT * FROM users WHERE name LIKE ?",
                        params,
                        Arrays.asList("%张")
                ),
                arguments(
                        "SELECT * FROM users WHERE title LIKE '%${contains}%'",
                        "SELECT * FROM users WHERE title LIKE ?",
                        params,
                        Arrays.asList("%技%")
                ),
                arguments(
                        "SELECT * FROM users WHERE position LIKE '${exact}'",
                        "SELECT * FROM users WHERE position LIKE ?",
                        params,
                        Arrays.asList("经理")
                ),
                arguments(
                        "SELECT * FROM users WHERE name LIKE '%${prefix}' " +
                                "AND role LIKE '${suffix}%' " +
                                "AND title LIKE '%${contains}%' " +
                                "AND position LIKE '${exact}'",
                        "SELECT * FROM users WHERE name LIKE ? AND role LIKE ? AND title LIKE ? AND position LIKE ?",
                        params,
                        Arrays.asList("%张", "工%", "%技%", "经理")
                )
        );
    }

    // 测试复杂嵌套条件参数化
    @Test
    void testComplexNestedConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "full-time");
        params.put("level", 3);

        String sql = "SELECT * FROM employees WHERE (department = ${dept} OR (type = ${type} AND level > ${level})) " +
                "AND (status = ${status} OR join_date > ${date})";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM employees WHERE type = ? AND level > ?", result.getSql());
        List<Object> expectedParams = Arrays.asList("full-time", 3);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试空参数Map场景
    @Test
    void testEmptyParams() {
        Map<String, Object> params = new HashMap<>();

        String sql = "SELECT * FROM users WHERE name = ${name} AND age > ${age}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM users", result.getSql());
        assertTrue(result.getParams().isEmpty());
    }

    // 测试null SQL输入
    @Test
    void testNullSql() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "test");

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(null, params);

        TestUtils.assertSqlEquals("", result.getSql());
        assertTrue(result.getParams().isEmpty());
    }

    // 测试OR条件参数化
    @Test
    void testOrConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("dept1", "销售部");
        params.put("dept2", "市场部");

        String sql = "SELECT * FROM employees WHERE department = ${dept1} OR department = ${dept2} OR department = ${dept3}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        String expectedSql = "SELECT * FROM employees WHERE department = ? OR department = ?";
        TestUtils.assertSqlEquals(expectedSql, result.getSql());
        List<Object> expectedParams = Arrays.asList("销售部", "市场部");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试IN条件参数化
    @Test
    void testInCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("status1", "active");
        params.put("status2", "pending");

        String sql = "SELECT * FROM employees WHERE status IN (${status1}, ${status2}, ${status3}) AND department = ${dept}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM employees WHERE status IN (?, ?)", result.getSql());
        List<Object> expectedParams = Arrays.asList("active", "pending");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试BETWEEN条件参数化
    @Test
    void testBetweenCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("min_age", 20);
        params.put("max_age", 30);

        String sql = "SELECT * FROM employees WHERE age BETWEEN ${min_age} AND ${max_age} AND salary > ${min_salary}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM employees WHERE age BETWEEN ? AND ?", result.getSql());
        List<Object> expectedParams = Arrays.asList(20, 30);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试JOIN查询参数化
    @Test
    void testJoinQuery() {
        Map<String, Object> params = new HashMap<>();
        params.put("dept_id", 10);

        String sql = "SELECT e.* FROM employees e JOIN departments d ON e.dept_id = d.id " +
                "WHERE d.id = ${dept_id} AND e.status = ${status}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT e.* FROM employees e JOIN departments d ON e.dept_id = d.id " +
                "WHERE d.id = ?", result.getSql());
        List<Object> expectedParams = Arrays.asList(10);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试CASE WHEN表达式参数化
    @Test
    void testCaseWhen() {
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", 5000);

        String sql = "SELECT name, CASE WHEN salary > ${threshold} THEN 'high' ELSE 'low' END AS level " +
                "FROM employees WHERE department = ${dept}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT name, CASE WHEN salary > ? THEN 'high' ELSE 'low' END AS level " +
                "FROM employees", result.getSql());
        List<Object> expectedParams = Arrays.asList(5000);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试空字符串输入
    @Test
    void testEmptySql() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "test");

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared("", params);

        TestUtils.assertSqlEquals("", result.getSql());
        assertTrue(result.getParams().isEmpty());
    }

    // 测试无效的${}格式（需配合解析器增强）
    @Test
    void testInvalidParameterFormat() {
        Map<String, Object> params = new HashMap<>();
        params.put("valid_param", "value");

        String sql = "SELECT * FROM employees WHERE name = ${valid_param} AND age = $invalid_format}";

        assertThrows(RuntimeException.class, () -> DynamicSqlParser.parseDynamicSqlToPrepared(sql, params), "应抛出参数格式错误异常");
    }

    // 测试基础参数替换
    @Test
    void testBasicParameterReplacement() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "李四");
        params.put("age", 30);

        String sql = "SELECT * FROM users WHERE name = ${name} AND age = ${age}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM users WHERE name = ? AND age = ?", result.getSql());
        List<Object> expectedParams = Arrays.asList("李四", 30);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试复杂条件表达式参数化
    @Test
    void testComplexConditionExpression() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "王五");
        params.put("minAge", 22);
        params.put("maxAge", 45);
        params.put("deptIds", Arrays.asList(101, 102));

        String sql = "SELECT * FROM employees " +
                "WHERE name LIKE CONCAT('%', ${name}, '%') " +
                "AND age BETWEEN ${minAge} AND ${maxAge} " +
                "AND dept_id IN ${deptIds}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM employees " +
                "WHERE name LIKE CONCAT('%', ?, '%') " +
                "AND age BETWEEN ? AND ? " +
                "AND dept_id IN (?, ?)", result.getSql());

        List<Object> expectedParams = Arrays.asList("王五", 22, 45, 101, 102);
        assertEquals(expectedParams, result.getParams());
    }

    // IN 条件参数过滤测试
    @Test
    void testInConditionParameterFiltering() {
        Map<String, Object> params = new HashMap<>();
        params.put("validIds", Arrays.asList(1, 3, 5));

        String sql = "SELECT * FROM items " +
                "WHERE id IN ${validIds} " +
                "AND category IN ${invalidIds}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM items WHERE id IN (?, ?, ?)", result.getSql());
        List<Object> expectedParams = Arrays.asList(1, 3, 5);
        assertEquals(expectedParams, result.getParams());
    }

    // 多表关联查询参数化测试
    @Test
    void testJoinQueryWithParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("deptName", "研发部");
        params.put("minSalary", 10000);

        String sql = "SELECT u.name, d.dept_name, s.salary " +
                "FROM users u " +
                "JOIN departments d ON u.dept_id = d.id " +
                "JOIN salaries s ON u.id = s.user_id " +
                "WHERE d.dept_name = ${deptName} " +
                "AND s.salary > ${minSalary}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT u.name, d.dept_name, s.salary " +
                "FROM users u JOIN departments d ON u.dept_id = d.id JOIN salaries s ON u.id = s.user_id " +
                "WHERE d.dept_name = ? AND s.salary > ?", result.getSql());

        List<Object> expectedParams = Arrays.asList("研发部", 10000);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试带ORDER BY和LIMIT的查询参数化
    @Test
    void testQueryWithOrderByAndLimit() {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "java");
        params.put("offset", 0);
        params.put("limit", 10);

        String sql = "SELECT * FROM articles " +
                "WHERE title LIKE CONCAT('%', ${keyword}, '%') " +
                "OR content LIKE CONCAT('%', ${keyword}, '%') " +
                "ORDER BY create_time DESC " +
                "LIMIT ${limit} OFFSET ${offset}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM articles " +
                "WHERE title LIKE CONCAT('%', ?, '%') " +
                "OR content LIKE CONCAT('%', ?, '%') " +
                "ORDER BY create_time DESC " +
                "LIMIT ? OFFSET ?", result.getSql());

        List<Object> expectedParams = Arrays.asList("java", "java", 10, 0);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试参数缺失时的条件过滤
    @Test
    void testConditionFilteringWhenParameterMissing() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "赵六");

        String sql = "SELECT * FROM users " +
                "WHERE name = ${name} " +
                "AND age > ${age} " +
                "AND dept = ${dept}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM users WHERE name = ?", result.getSql());
        List<Object> expectedParams = Arrays.asList("赵六");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试空参数Map的情况
    @Test
    void testEmptyParameterMap() {
        Map<String, Object> params = new HashMap<>();

        String sql = "SELECT * FROM users WHERE name = ${name} AND age = ${age}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM users", result.getSql());
        assertTrue(result.getParams().isEmpty());
    }

    // 测试特殊字符和转义处理
    @Test
    void testSpecialCharactersAndEscaping() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "O'Connor");
        params.put("title", "Java Developer's Guide");

        String sql = "SELECT * FROM users " +
                "WHERE name = ${name} " +
                "AND title LIKE '${title}'";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM users WHERE name = ? AND title LIKE ?", result.getSql());

        List<Object> expectedParams = Arrays.asList("O'Connor", "Java Developer's Guide");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试子查询中的参数替换
    @Test
    void testSubqueryParameterReplacement() {
        Map<String, Object> params = new HashMap<>();
        params.put("deptId", 101);

        String sql = "SELECT * FROM users " +
                "WHERE id IN (SELECT user_id FROM user_dept WHERE dept_id = ${deptId})";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("SELECT * FROM users " +
                "WHERE id IN (SELECT user_id FROM user_dept WHERE dept_id = ?)", result.getSql());

        List<Object> expectedParams = Arrays.asList(101);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试UPDATE语句参数化
    @Test
    void testUpdateStatementParameterization() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "钱七");
        params.put("age", 35);
        params.put("userId", 1001);

        String sql = "UPDATE users " +
                "SET name = ${name}, age = ${age} " +
                "WHERE id = ${userId}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("UPDATE users SET name = ?, age = ? WHERE id = ?", result.getSql());

        List<Object> expectedParams = Arrays.asList("钱七", 35, 1001);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试DELETE语句参数化
    @Test
    void testDeleteStatementParameterization() {
        Map<String, Object> params = new HashMap<>();
        params.put("deptId", 201);
        params.put("minAge", 50);

        String sql = "DELETE FROM employees " +
                "WHERE dept_id = ${deptId} " +
                "AND age >= ${minAge}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("DELETE FROM employees WHERE dept_id = ? AND age >= ?", result.getSql());

        List<Object> expectedParams = Arrays.asList(201, 50);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试INSERT语句参数化
    @Test
    void testInsertStatementParameterization() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "孙八");
        params.put("email", "sunba@example.com");
        params.put("deptId", 301);

        String sql = "INSERT INTO users (name, email, dept_id) " +
                "VALUES (${name}, ${email}, ${deptId})";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        TestUtils.assertSqlEquals("INSERT INTO users (name, email, dept_id) VALUES (?, ?, ?)", result.getSql());

        List<Object> expectedParams = Arrays.asList("孙八", "sunba@example.com", 301);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试复杂LIKE模式参数化
    @Test
    public void testComplexLikePatterns() {
        Map<String, Object> params = new HashMap<>();
        params.put("id", "123");
        params.put("name", "John");
        params.put("title", "Manager");

        String sql = "SELECT * FROM flow_api_info " +
                "WHERE id LIKE '%${id}%' " +
                "AND name LIKE '%${name}' " +
                "AND title LIKE '${title}%'";

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        String expectedSql = "SELECT * FROM flow_api_info WHERE id LIKE ? AND name LIKE ? AND title LIKE ?";
        TestUtils.assertSqlEquals(expectedSql, result.getSql());

        List<Object> expectedParams = ListUtil.of(
                "%123%",    // 对应 id LIKE '%${id}%'
                "%John",    // 对应 name LIKE '%${name}'
                "Manager%"  // 对应 title LIKE '${title}%'
        );
        assertEquals(expectedParams, result.getParams());
    }

    // 测试多个参数在LIKE中的情况
    @Test
    public void testMultipleParamsInLike() {
        Map<String, Object> params = new HashMap<>();
        params.put("prefix", "DEV");
        params.put("suffix", "001");

        String sql = "SELECT * FROM tasks WHERE code LIKE '${prefix}_%_${suffix}'";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        String expectedSql = "SELECT * FROM tasks WHERE code LIKE ?";
        TestUtils.assertSqlEquals(expectedSql, result.getSql());

        List<Object> expectedParams = ListUtil.of("DEV_%_001");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试转义通配符
    @Test
    public void testEscapedWildcards() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Product%");

        String sql = "SELECT * FROM items WHERE name LIKE '${name}[%]'";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        String expectedSql = "SELECT * FROM items WHERE name LIKE ?";
        TestUtils.assertSqlEquals(expectedSql, result.getSql());

        List<Object> expectedParams = ListUtil.of("Product%[%]");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试复杂SQL（含CTE、JOIN和多种条件）
    @Test
    public void testComplexSqlWithCTEAndLike() {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 123);
        params.put("name", "TechCorp");
        params.put("type", "Software");
        params.put("field", "AI");
        params.put("title", "Intelligent");

        String sql =
                "WITH favorites AS (\n" +
                        "    SELECT * FROM console_favorites WHERE account_id = 0 AND account_id = ${userId}\n" +
                        "), \n" +
                        "company AS (\n" +
                        "    SELECT\n" +
                        "        cp.*,\n" +
                        "        COUNT(fav.account_id) AS ct\n" +
                        "    FROM\n" +
                        "        db_company_product cp\n" +
                        "        LEFT JOIN favorites fav ON cp.ID = fav.product_id\n" +
                        "    WHERE\n" +
                        "        company_name = '${name}' \n" +
                        "        AND TYPE = '${type}' \n" +
                        "        AND field = '${field}' \n" +
                        "        AND title LIKE '%${title}%' \n" +
                        "        AND is_delete = 0 \n" +
                        "    GROUP BY cp.ID\n" +
                        "    ORDER BY\n" +
                        "        sort\n" +
                        ")\n" +
                        "SELECT \n" +
                        "        id,\n" +
                        "    type,\n" +
                        "    company_name,\n" +
                        "    info,\n" +
                        "    price,\n" +
                        "    sale_num,\n" +
                        "    view_num,\n" +
                        "    publish_date,\n" +
                        "    is_delete,\n" +
                        "    sort,\n" +
                        "    title,\n" +
                        "    image_path,\n" +
                        "    field,\n" +
                        "    label,\n" +
                        "    detail,\n" +
                        "  (CASE WHEN ct > 0 THEN TRUE ELSE FALSE END) AS is_favorites\n" +
                        "FROM company";

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        String expectedSql =
                "WITH favorites AS ( SELECT * FROM console_favorites WHERE account_id = 0 AND account_id = ? ), " +
                        "company AS ( SELECT cp.*, COUNT(fav.account_id) AS ct FROM db_company_product cp " +
                        "LEFT JOIN favorites fav ON cp.ID = fav.product_id " +
                        "WHERE company_name = ? AND TYPE = ? AND field = ? AND title LIKE ? AND is_delete = 0 " +
                        "GROUP BY cp.ID ORDER BY sort ) " +
                        "SELECT id, type, company_name, info, price, sale_num, view_num, publish_date, is_delete, " +
                        "sort, title, image_path, field, label, detail, (CASE WHEN ct > 0 THEN TRUE ELSE FALSE END) AS is_favorites FROM company";

        String normalizedExpected = expectedSql.replaceAll("\\s+", " ").trim();
        String normalizedActual = result.getSql().replaceAll("\\s+", " ").trim();
        TestUtils.assertSqlEquals(normalizedExpected, normalizedActual);

        List<Object> expectedParams = Arrays.asList(
                123,               // ${userId}
                "TechCorp",        // ${name}
                "Software",        // ${type}
                "AI",              // ${field}
                "%Intelligent%"   // ${title} with wildcards
        );

        assertEquals(expectedParams, result.getParams());
    }

    @Test
    public void testComplexSqlWithCTEAndLike_MissingTypeParameter() {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 123);
        params.put("name", "TechCorp");
        params.put("field", "AI");
        params.put("title", "Intelligent");
        // 故意不设置 type 参数，测试条件删除逻辑

        String sql =
                "WITH favorites AS (\n" +
                        "    SELECT * FROM console_favorites WHERE account_id = 0 AND account_id = ${userId}\n" +
                        "), \n" +
                        "company AS (\n" +
                        "    SELECT\n" +
                        "        cp.*,\n" +
                        "        COUNT(fav.account_id) AS ct\n" +
                        "    FROM\n" +
                        "        db_company_product cp\n" +
                        "        LEFT JOIN favorites fav ON cp.ID = fav.product_id\n" +
                        "    WHERE\n" +
                        "        company_name = '${name}' \n" +
                        "        AND TYPE = '${type}' \n" +
                        "        AND field = '${field}' \n" +
                        "        AND title LIKE '%${title}%' \n" +
                        "        AND is_delete = 0 \n" +
                        "    GROUP BY cp.ID\n" +
                        "    ORDER BY\n" +
                        "        sort\n" +
                        ")\n" +
                        "SELECT \n" +
                        "        id,\n" +
                        "    type,\n" +
                        "    company_name,\n" +
                        "    info,\n" +
                        "    price,\n" +
                        "    sale_num,\n" +
                        "    view_num,\n" +
                        "    publish_date,\n" +
                        "    is_delete,\n" +
                        "    sort,\n" +
                        "    title,\n" +
                        "    image_path,\n" +
                        "    field,\n" +
                        "    label,\n" +
                        "    detail,\n" +
                        "  (CASE WHEN ct > 0 THEN TRUE ELSE FALSE END) AS is_favorites\n" +
                        "FROM company";

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 预期 SQL 中应删除 TYPE = '${type}' 条件
        String expectedSql =
                "WITH favorites AS ( SELECT * FROM console_favorites WHERE account_id = 0 AND account_id = ? ), " +
                        "company AS ( SELECT cp.*, COUNT(fav.account_id) AS ct FROM db_company_product cp " +
                        "LEFT JOIN favorites fav ON cp.ID = fav.product_id " +
                        "WHERE company_name = ? AND field = ? AND title LIKE ? AND is_delete = 0 " +
                        "GROUP BY cp.ID ORDER BY sort ) " +
                        "SELECT id, type, company_name, info, price, sale_num, view_num, publish_date, is_delete, " +
                        "sort, title, image_path, field, label, detail, (CASE WHEN ct > 0 THEN TRUE ELSE FALSE END) AS is_favorites FROM company";

        String normalizedExpected = expectedSql.replaceAll("\\s+", " ").trim();
        String normalizedActual = result.getSql().replaceAll("\\s+", " ").trim();
        TestUtils.assertSqlEquals(normalizedExpected, normalizedActual);

        List<Object> expectedParams = Arrays.asList(
                123,               // ${userId}
                "TechCorp",        // ${name}
                "AI",              // ${field}
                "%Intelligent%"   // ${title} with wildcards
        );

        assertEquals(expectedParams, result.getParams());
    }

    // 测试表别名与星号
    @Test
    public void testTableAliasWithAsterisk() {
        String sql = "SELECT cp.* FROM db_company_product cp";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, new HashMap<>());

        assertTrue(result.getSql().contains("cp.*"));
        TestUtils.assertSqlEquals("SELECT cp.* FROM db_company_product cp", result.getSql());
    }


    // 测试INSERT语句参数缺失时设为NULL
    @Test
    void testInsertStatementWithMissingParamsAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "孙八");
        // 故意不设置email和deptId参数

        String sql = "INSERT INTO users (name, email, dept_id) " +
                "VALUES (${name}, ${email}, ${deptId})";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 预期：所有字段保留，缺失的参数对应为NULL
        TestUtils.assertSqlEquals("INSERT INTO users (name, email, dept_id) VALUES (?, NULL, NULL)", result.getSql());

        List<Object> expectedParams = Arrays.asList("孙八");
        assertEquals(expectedParams, result.getParams());
    }

    // 测试UPDATE语句参数缺失时设为NULL
    @Test
    void testUpdateStatementWithMissingParamsAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "钱七");
        params.put("userId", 1001);
        // 故意不设置age参数

        String sql = "UPDATE users " +
                "SET name = ${name}, age = ${age} " +
                "WHERE id = ${userId}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 预期：所有SET字段保留，缺失的参数对应为NULL
        TestUtils.assertSqlEquals("UPDATE users SET name = ?, age = NULL WHERE id = ?", result.getSql());

        List<Object> expectedParams = Arrays.asList("钱七", 1001);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试DELETE语句中参数缺失时设为NULL
    @Test
    void testDeleteStatementWithMissingParamsAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("deptId", 201);
        // 故意不设置minAge参数

        String sql = "DELETE FROM employees " +
                "WHERE dept_id = ${deptId} " +
                "AND age >= ${minAge}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 预期：所有条件保留，缺失的参数对应为NULL
        TestUtils.assertSqlEquals("DELETE FROM employees WHERE dept_id = ? AND age >= NULL", result.getSql());

        List<Object> expectedParams = Arrays.asList(201);
        assertEquals(expectedParams, result.getParams());
    }

    // 测试多表UPDATE中参数缺失设为NULL
    @Test
    void testMultiTableUpdateWithMissingParamsAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "李四");
        params.put("deptId", 101);
        // 故意不设置age参数

        String sql = "UPDATE users u " +
                "JOIN departments d ON u.dept_id = d.id " +
                "SET u.name = ${name}, u.age = ${age} " +
                "WHERE d.id = ${deptId}";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 预期：所有SET字段保留，缺失的参数对应为NULL
        String expectedSql = "UPDATE users u JOIN departments d ON u.dept_id = d.id " +
                "SET u.name = ?, u.age = NULL WHERE d.id = ?";
        TestUtils.assertSqlEquals(expectedSql, result.getSql());

        List<Object> expectedParams = Arrays.asList("李四", 101);
        assertEquals(expectedParams, result.getParams());
    }

    @Test
    void test1() {
        Map<String, Object> params = new HashMap<>();

        String sql = "WITH power AS (\n" +
                "    SELECT\n" +
                "        center_name,\n" +
                "        chip_model\n" +
                "    FROM v_computing_centers  GROUP BY center_name, chip_model\n" +
                "),\n" +
                "     company_count as (select count(DISTINCT legal_card_number) as num from console_user_authentication where inst_type = 1 and is_valid = true and is_delete = false and legal_card_number is not null),\n" +
                "     power_count AS ( SELECT COUNT ( distinct(chip_model) ) AS \"powerTypeNum\", count(1) as \"powerNum\"  FROM POWER )\n" +
                "SELECT\n" +
                "    \"dataTypeNum\",\n" +
                "    \"dataNum\",\n" +
                "    \"algoTypeNum\",\n" +
                "    \"algoNum\",\n" +
                "    p.\"powerTypeNum\",\n" +
                "    p.\"powerNum\",\n" +
                "    \"solutionNum\",\n" +
                "    '100T' AS \"dataSpaceSize\",\n" +
                "    '7' AS \"modelCount\",\n" +
                "    C.num AS \"companyCount\",\n" +
                "    '300' AS \"sceneNum\",\n" +
                "    '100+' AS \"traceNum\",\n" +
                "    '400' AS \"aiNum\",\n" +
                "    '0' AS \"rewardNum\"\n" +
                "FROM\n" +
                "    screen_product_num,\n" +
                "    power_count P,\n" +
                "    company_count C";
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 验证SQL解析成功，不抛出异常
        assertNotNull(result);
        assertNotNull(result.getSql());
        // 验证返回的SQL包含预期的关键字
        assertTrue(result.getSql().contains("WITH"));
        assertTrue(result.getSql().contains("power_count"));
        assertTrue(result.getSql().contains("company_count"));
        // 验证参数列表为空，因为SQL中没有参数
        assertTrue(result.getParams().isEmpty());
    }

    @Test
    public void testPreserveDoubleQuotes() {
        // 测试SQL，包含双引号包裹的列名和参数
        String sql = "SELECT \"dataTypeNum\", ${param1}, \"algoTypeNum\" FROM table WHERE column = '${param2}'";

        // 测试参数（可以为空，因为我们主要测试引号处理）
        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", "value2");

        // 执行解析
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 预期的预处理SQL
        String expectedSql = "SELECT \"dataTypeNum\", ?, \"algoTypeNum\" FROM table WHERE column = ?";

        // 验证结果
        assertEquals(expectedSql, result.getSql());
        assertEquals(2, result.getParams().size());
        assertEquals("value1", result.getParams().get(0));
        assertEquals("value2", result.getParams().get(1));
    }

    @Test
    public void testComplexSqlWithDoubleQuotes() {
        // 测试SQL使用问题中的实际SQL
        String sql = "WITH power AS (\n" +
                "    SELECT\n" +
                "        center_name,\n" +
                "        chip_model\n" +
                "    FROM v_computing_centers  GROUP BY center_name, chip_model\n" +
                "),\n" +
                "     company_count as (select count(DISTINCT legal_card_number) as num from console_user_authentication where inst_type = 1 and is_valid = true and is_delete = false and legal_card_number is not null),\n" +
                "     power_count AS ( SELECT COUNT ( distinct(chip_model) ) AS \"powerTypeNum\", count(1) as \"powerNum\"  FROM POWER )\n" +
                "SELECT\n" +
                "    \"dataTypeNum\",\n" +
                "    \"dataNum\",\n" +
                "    \"algoTypeNum\",\n" +
                "    \"algoNum\",\n" +
                "    p.\"powerTypeNum\",\n" +
                "    p.\"powerNum\",\n" +
                "    \"solutionNum\",\n" +
                "    '100T' AS \"dataSpaceSize\",\n" +
                "    '7' AS \"modelCount\",\n" +
                "    C.num AS \"companyCount\",\n" +
                "    '300' AS \"sceneNum\",\n" +
                "    '100+' AS \"traceNum\",\n" +
                "    '400' AS \"aiNum\",\n" +
                "    '0' AS \"rewardNum\"\n" +
                "FROM\n" +
                "    screen_product_num,\n" +
                "    power_count P,\n" +
                "    company_count C";

        // 测试参数（可以为空，因为我们主要测试引号处理）
        Map<String, Object> params = new HashMap<>();

        // 执行解析
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 验证双引号是否保留（验证实际存在的列名"powerTypeNum"）
        String processedSql = result.getSql();
        assertEquals(true, processedSql.contains("powerTypeNum"));
    }

    @Test
    public void testComplexSqlWithDoubleQuotes1() {
        // 测试SQL使用问题中的实际SQL
        String sql = "WITH price_extracted AS (\n" +
                "    SELECT \n" +
                "        pp.id AS price_id,\n" +
                "        MIN(NULLIF((elem->>'realPrice'), '')::numeric) AS min_real_price\n" +
                "    FROM \n" +
                "        console_product_price pp,\n" +
                "        LATERAL (SELECT * FROM json_array_elements(pp.platform_price::json->'tableData')) elem\n" +
                "    WHERE \n" +
                "        NULLIF(elem->>'realPrice', '') IS NOT NULL\n" +
                "    GROUP BY \n" +
                "        pp.id\n" +
                ")\n" +
                "SELECT\n" +
                "    dp.id,\n" +
                "    dp.product_name,\n" +
                "    dp.inst_name,\n" +
                "    dp.view_num,\n" +
                "    dp.image_list_thumbnail,\n" +
                "    pp.platform_price,\n" +
                "    pp.is_communicated,\n" +
                "    CASE \n" +
                "        WHEN pp.is_communicated = true THEN '面议' \n" +
                "        ELSE COALESCE(pe.min_real_price::text, NULL)\n" +
                "    END AS min_real_price,\n" +
                "\t\tdict.label as \"productScene\"\n" +
                "FROM \n" +
                "    console_data_product AS dp\n" +
                "\t\tLEFT JOIN (select * from bms_dict_detail where dict_code = 'application_scene') as dict on (dict.value = dp.product_scene)\n" +
                "    LEFT JOIN console_product_price AS pp ON dp.price_id = pp.\"id\"\n" +
                "    LEFT JOIN price_extracted pe ON pp.id = pe.price_id\n" +
                "WHERE\n" +
                "    dp.delivery_method = 5 \n" +
                "    AND dp.shelf_status = 1\n" +
                "\t\tand dp.cost_type <> 4";

        // 测试参数（可以为空，因为我们主要测试引号处理）
        Map<String, Object> params = new HashMap<>();

        // 执行解析
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);

        // 验证双引号是否保留（验证实际存在的列名"id"）
        String processedSql = result.getSql();
        assertEquals(true, processedSql.contains("\"id\""));
    }

    @Test
    public void testLikeConditionWithMissingParam() {
        String sql = "SELECT * FROM console_user_order o LEFT JOIN console_user_order_detail od ON o.order_detail_id = od.id WHERE o.pay_status in (1,3) AND od.product_name LIKE '%${productName}%'";

        Map<String, Object> params = new HashMap<>();
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);
        String processedSql = result.getSql();

        assertFalse(processedSql.contains("LIKE"));
        assertFalse(processedSql.contains("product_name"));
        assertEquals(0, result.getParams().size());
    }

    @Test
    public void testLikeConditionWithValidParam() {
        String sql = "SELECT * FROM console_user_order o LEFT JOIN console_user_order_detail od ON o.order_detail_id = od.id WHERE o.pay_status in (1,3) AND od.product_name LIKE '%${productName}%'";

        Map<String, Object> params = new HashMap<>();
        params.put("productName", "测试产品");
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params);
        String processedSql = result.getSql();

        assertTrue(processedSql.contains("LIKE"));
        assertTrue(processedSql.contains("?"));
        assertEquals(1, result.getParams().size());
        assertEquals("%测试产品%", result.getParams().get(0));
    }

    @Test
    public void testGenerateSeriesWithLikeCondition() {
        String sql = "WITH month_all_dates AS ( SELECT generate_series( DATE_TRUNC('month', CURRENT_TIMESTAMP)::DATE, (DATE_TRUNC('month', CURRENT_TIMESTAMP) + INTERVAL '1 month' - INTERVAL '1 day')::DATE, INTERVAL '1 day' )::DATE AS sales_date ) SELECT mad.sales_date, COALESCE(od.daily_sales, 0.00) AS daily_sales FROM month_all_dates mad LEFT JOIN ( SELECT DATE(o.create_time) AS sales_date, COALESCE(ROUND(SUM(CAST(o.toal_fee AS NUMERIC)) / 100, 2), 0.00) AS daily_sales FROM console.console_user_order o LEFT JOIN console.console_user_order_detail od_detail ON o.order_detail_id = od_detail.id WHERE o.pay_status in (1,3) AND DATE_TRUNC('month', o.create_time) = DATE_TRUNC('month', CURRENT_TIMESTAMP) AND od_detail.product_name LIKE '%${productName}%' GROUP BY DATE(o.create_time) ) od ON mad.sales_date = od.sales_date ORDER BY mad.sales_date ASC;";

        // 测试场景1：不传递productName参数
        Map<String, Object> params1 = new HashMap<>();
        SqlAndParams result1 = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params1);
        String processedSql1 = result1.getSql();
        assertFalse(processedSql1.contains("LIKE"));
        assertFalse(processedSql1.contains("productName"));
        assertEquals(0, result1.getParams().size());

        // 测试场景2：传递productName参数
        Map<String, Object> params2 = new HashMap<>();
        params2.put("productName", "测试产品");
        SqlAndParams result2 = DynamicSqlParser.parseDynamicSqlToPrepared(sql, params2);
        String processedSql2 = result2.getSql();
        assertTrue(processedSql2.contains("LIKE"));
        assertTrue(processedSql2.contains("?"));
        assertEquals(1, result2.getParams().size());
        assertEquals("%测试产品%", result2.getParams().get(0));
    }


    /**
     * 测试1：删除空参数对应的条件（独立方法，无外部变量）
     */
    @Test
    public void testRemoveEmptyParamConditions() {
        // 1. 内部定义原始SQL（无外部变量）
        String originalSql = "WITH month_all_dates AS (\n" +
                "  SELECT\n" +
                "    generate_series(\n" +
                "      DATE_TRUNC('month', CURRENT_TIMESTAMP)::DATE,\n" +
                "      (DATE_TRUNC('month', CURRENT_TIMESTAMP) + INTERVAL '1 month' - INTERVAL '1 day')::DATE,\n" +
                "      INTERVAL '1 day'\n" +
                "    )::DATE AS sales_date\n" +
                ")\n" +
                "SELECT\n" +
                "  mad.sales_date,\n" +
                "  COALESCE(od.daily_sales, 0.00) AS daily_sales\n" +
                "FROM\n" +
                "  month_all_dates mad\n" +
                "LEFT JOIN (\n" +
                "  SELECT\n" +
                "    DATE(o.create_time) AS sales_date,\n" +
                "    COALESCE(ROUND(SUM(CAST(o.toal_fee AS NUMERIC)) / 100, 2), 0.00) AS daily_sales\n" +
                "  FROM\n" +
                "    \"console\".\"console_user_order\" o\n" +
                "    LEFT JOIN \"console\".\"console_user_order_detail\" od_detail \n" +
                "      ON o.order_detail_id = od_detail.id\n" +
                "  WHERE\n" +
                "    o.pay_status in (1,3)\n" +
                "    AND DATE_TRUNC('month', o.create_time) = DATE_TRUNC('month', CURRENT_TIMESTAMP)\n" +
                "    AND od_detail.product_name LIKE '%${productName}%'\n" +
                "  GROUP BY\n" +
                "    DATE(o.create_time)\n" +
                ") od ON mad.sales_date = od.sales_date\n" +
                "ORDER BY\n" +
                "  mad.sales_date ASC;";

        // 2. 内部定义空参数Map（无外部变量）
        Map<String, Object> emptyParamMap = new HashMap<>();
        emptyParamMap.put("productName", "");
        // 执行方法
        SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(originalSql, emptyParamMap);
        log.info("testRemoveEmptyParamConditions: {}", sqlAndParams.getSql());
        String normalizedExpected = "WITH month_all_dates AS (\n" +
                "  SELECT\n" +
                "    generate_series(\n" +
                "      DATE_TRUNC('month', CURRENT_TIMESTAMP)::DATE,\n" +
                "      (DATE_TRUNC('month', CURRENT_TIMESTAMP) + INTERVAL '1 month' - INTERVAL '1 day')::DATE,\n" +
                "      INTERVAL '1 day'\n" +
                "    )::DATE AS sales_date\n" +
                ")\n" +
                "SELECT\n" +
                "  mad.sales_date,\n" +
                "  COALESCE(od.daily_sales, 0.00) AS daily_sales\n" +
                "FROM\n" +
                "  month_all_dates mad\n" +
                "LEFT JOIN (\n" +
                "  SELECT\n" +
                "    DATE(o.create_time) AS sales_date,\n" +
                "    COALESCE(ROUND(SUM(CAST(o.toal_fee AS NUMERIC)) / 100, 2), 0.00) AS daily_sales\n" +
                "  FROM\n" +
                "    \"console\".\"console_user_order\" o\n" +
                "    LEFT JOIN \"console\".\"console_user_order_detail\" od_detail \n" +
                "      ON o.order_detail_id = od_detail.id\n" +
                "  WHERE\n" +
                "    o.pay_status in (1,3)\n" +
                "    AND DATE_TRUNC('month', o.create_time) = DATE_TRUNC('month', CURRENT_TIMESTAMP)\n" +
                "  GROUP BY\n" +
                "    DATE(o.create_time)\n" +
                ") od ON mad.sales_date = od.sales_date\n" +
                "ORDER BY\n" +
                "  mad.sales_date ASC;";

        log.info(sqlAndParams.getSql());
        // 断言验证：验证空参数条件已被删除
        assertFalse(sqlAndParams.getSql().contains("product_name"), "空参数条件未被删除");
        // 核心条件检查：使用更宽松的正则或仅匹配关键词
        String actualSql = sqlAndParams.getSql();
        assertTrue(actualSql.contains("o.pay_status IN (1, 3)") || actualSql.contains("o.pay_status in (1,3)"), "核心条件 pay_status 被意外删除或格式变化");
        assertTrue(actualSql.contains("DATE_TRUNC"), "核心条件 DATE_TRUNC 被意外删除");

        TestUtils.assertSqlEquals(normalizedExpected, actualSql);

        // 3. 内部定义非空参数Map（无外部变量）
        Map<String, Object> nonEmptyParamMap = new HashMap<>();
        nonEmptyParamMap.put("productName", "会员套餐");
        // 执行方法
        SqlAndParams sqlAndParams1 = DynamicSqlParser.parseDynamicSqlToPrepared(originalSql, nonEmptyParamMap);
        // 断言验证：验证非空参数条件被保留
        assertTrue(sqlAndParams1.getSql().contains("od_detail.product_name LIKE ?"), "错误删除非空参数的条件");
    }


}
