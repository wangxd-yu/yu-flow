package org.yu.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonNodeSpELTest {

    private JsonNode testData;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser();

    @BeforeEach
    void setUp() throws Exception {
        // 准备测试用的JSON数据
        String json = "{" +
                "\"user\": {" +
                "  \"name\": \"张三\"," +
                "  \"age\": 25," +
                "  \"address\": {" +
                "    \"city\": \"北京\"," +
                "    \"street\": \"朝阳区\"" +
                "  }," +
                "  \"active\": true," +
                "  \"scores\": [85, 90, 95]," +
                "  \"contacts\": {" +
                "    \"email\": \"zhangsan@example.com\"," +
                "    \"phone\": \"13800138000\"" +
                "  }" +
                "}," +
                "\"system\": {" +
                "  \"version\": \"1.0.0\"," +
                "  \"active\": true," +
                "  \"settings\": {\"darkMode\": false}" +
                "}," +
                "\"tags\": [\"java\", \"spring\", \"test\"]" + // 移到根路径
                "}";

        testData = objectMapper.readTree(json);
    }

    @Test
    void testBasicFieldAccess() {
        // 测试基本字段访问
        Object name = evaluate("['user']['name']");
        assertEquals("张三", name);

        Object age = evaluate("['user']['age']");
        assertEquals(25, age);
    }

    @Test
    void testNestedObjectAccess() {
        // 测试嵌套对象访问
        Object city = evaluate("['user']['address']['city']");
        assertEquals("北京", city);

        Object street = evaluate("['user']['address']['street']");
        assertEquals("朝阳区", street);
    }

    @Test
    void testArrayAccess() {
        // 测试数组访问
        Object firstScore = evaluate("['user']['scores'][0]");
        assertEquals(85, firstScore);

        Object lastTag = evaluate("['tags'][2]");
        assertEquals("test", lastTag);
    }

    @Test
    void testBooleanField() {
        // 测试布尔值字段
        Object active = evaluate("['user']['active']");
        assertEquals(true, active);

        Object darkMode = evaluate("['system']['settings']['darkMode']");
        assertEquals(false, darkMode);
    }

    @Test
    void testNonExistentPath() {
        // 测试不存在的路径
        Object nonExistent = evaluate("['user']['nonExistent']");
        assertNull(nonExistent);

        Object deepNonExistent = evaluate("['user']['address']['postcode']");
        assertNull(deepNonExistent);
    }

    @Test
    void testRootLevelAccess() {
        // 测试根级别访问
        Object userNode = evaluate("['user']");
        assertInstanceOf(Map.class, userNode);
        assertTrue(((Map<?, ?>) userNode).containsKey("name"));

        Object systemNode = evaluate("['system']");
        assertInstanceOf(Map.class, systemNode);
        assertTrue(((Map<?, ?>) systemNode).containsKey("version"));
    }

    @ParameterizedTest
    @CsvSource({
        "['user']['name'], 张三",
        "['user']['age'], 25",
        "['user']['active'], true",
        "['tags'][0], java",
        "['system']['version'], 1.0.0"
    })
    void testParameterizedAccess(String expression, String expected) {
        // 参数化测试多种访问路径
        Object result = evaluate(expression);
        assertEquals(expected, result == null ? "null" : String.valueOf(result));
    }

    @Test
    void testConvertedToJavaTypes() {
        // 测试自动类型转换
        Object name = evaluate("['user']['name']");
        assertInstanceOf(String.class, name);

        Object age = evaluate("['user']['age']");
        assertInstanceOf(Integer.class, age);

        Object active = evaluate("['user']['active']");
        assertInstanceOf(Boolean.class, active);

        Object score = evaluate("['user']['scores'][1]");
        assertInstanceOf(Integer.class, score);
    }

    /**
     * 使用SpringEL解析JsonNode的辅助方法
     */
    private Object evaluate(String expression) {
        try {
            // 将JsonNode转换为Map以便SpEL可以访问属性
            Map<String, Object> dataMap = objectMapper.convertValue(testData, Map.class);
            StandardEvaluationContext context = new StandardEvaluationContext(dataMap);
            Expression exp = parser.parseExpression(expression);
            return exp.getValue(context);
        } catch (Exception e) {
            return null;
        }
    }
}
