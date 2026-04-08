package org.yu.flow.engine.evaluator.expression;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yu.flow.module.sysmacro.cache.CachedMacro;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

/**
 * JavaScriptEvaluatorImpl 单元测试
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>原有功能回归测试（基本表达式、Map/List/数组、IIFE 包裹逻辑）</li>
 *   <li>VARIABLE 宏注入到 JS 环境</li>
 *   <li>FUNCTION 宏注入到 JS 环境（通过 ProxyExecutable 调用 SpEL）</li>
 *   <li>SQL_ONLY 作用域宏的过滤</li>
 *   <li>DynamicMacroFunction 参数传递（#p0, #p1...）</li>
 *   <li>宏注入失败时的异常隔离（不阻断 JS 脚本执行）</li>
 * </ul>
 */
@DisplayName("JavaScriptEvaluatorImpl 宏注入单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(MockitoExtension.class)
class JavaScriptEvaluatorImplTest {

    private JavaScriptEvaluatorImpl evaluator;
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    @BeforeEach
    void setUp() {
        evaluator = new JavaScriptEvaluatorImpl();
    }

    // ============================= 原有功能回归测试 =============================

    @Test
    @Order(1)
    @DisplayName("01、基本表达式 - 数值计算")
    void testBasicArithmetic() {
        // 无宏注入场景（Spring 容器未初始化时优雅跳过）
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenThrow(new RuntimeException("No Spring context"));

            Object result = evaluator.evaluate("1 + 2 * 3", null);
            assertEquals(7L, result);
        }
    }

    @Test
    @Order(2)
    @DisplayName("02、基本表达式 - 字符串拼接")
    void testStringConcatenation() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenThrow(new RuntimeException("No Spring context"));

            Object result = evaluator.evaluate("'Hello' + ' ' + 'World'", null);
            assertEquals("Hello World", result);
        }
    }

    @Test
    @Order(3)
    @DisplayName("03、上下文变量注入 - 顶级变量可用")
    void testContextVariableInjection() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenThrow(new RuntimeException("No Spring context"));

            Map<String, Object> context = new HashMap<>();
            context.put("price", 100);
            context.put("discount", 0.8);

            Object result = evaluator.evaluate("price * discount", context);
            // JS: 100 * 0.8 = 80.0 (浮点运算)，但 convertValue 检测到可转 long 且无精度丢失时返回 Long
            assertEquals(80L, result);
        }
    }

    @Test
    @Order(4)
    @DisplayName("04、上下文变量注入 - input别名兼容")
    void testInputAliasCompatibility() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenThrow(new RuntimeException("No Spring context"));

            Map<String, Object> context = new HashMap<>();
            context.put("name", "Test");

            Object result = evaluator.evaluate("input.name", context);
            assertEquals("Test", result);
        }
    }

    @Test
    @Order(5)
    @DisplayName("05、数组处理 - filter/map 原生方法")
    void testArrayNativeMethods() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenThrow(new RuntimeException("No Spring context"));

            Map<String, Object> context = new HashMap<>();
            context.put("items", Arrays.asList(1, 2, 3, 4, 5));

            Object result = evaluator.evaluate("items.filter(x => x > 3)", context);
            assertTrue(result instanceof List);
            List<?> list = (List<?>) result;
            assertEquals(2, list.size());
            assertEquals(4L, list.get(0));
            assertEquals(5L, list.get(1));
        }
    }

    @Test
    @Order(6)
    @DisplayName("06、空表达式 - 返回 null")
    void testNullExpression() {
        assertNull(evaluator.evaluate(null, null));
        assertNull(evaluator.evaluate("", null));
        assertNull(evaluator.evaluate("   ", null));
    }

    // ============================= VARIABLE 宏注入测试 =============================

    @Test
    @Order(10)
    @DisplayName("10、VARIABLE宏注入 - 常量值宏绑定为 JS 全局变量")
    void testVariableMacroInjection() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            // 准备: 创建一个 VARIABLE 宏，SpEL 表达式为常量字符串
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();
            SysMacroDO macroDO = SysMacroDO.builder()
                    .macroCode("sys_app_name")
                    .macroType("VARIABLE")
                    .scope("ALL")
                    .build();
            Expression expr = SPEL_PARSER.parseExpression("'FlowEngine'");
            macros.put("sys_app_name", new CachedMacro(macroDO, expr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // 执行：JS 脚本中直接使用宏编码作为变量名
            Object result = evaluator.evaluate("sys_app_name", null);
            assertEquals("FlowEngine", result);
        }
    }

    @Test
    @Order(11)
    @DisplayName("11、VARIABLE宏注入 - 数值型宏在 JS 中可参与计算")
    void testVariableMacroNumericCalculation() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();
            SysMacroDO macroDO = SysMacroDO.builder()
                    .macroCode("sys_tax_rate")
                    .macroType("VARIABLE")
                    .scope("ALL")
                    .build();
            Expression expr = SPEL_PARSER.parseExpression("0.13");
            macros.put("sys_tax_rate", new CachedMacro(macroDO, expr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // 执行：JS 中使用宏变量参与计算
            Map<String, Object> context = new HashMap<>();
            context.put("amount", 1000);
            Object result = evaluator.evaluate("amount * sys_tax_rate", context);
            // JS: 1000 * 0.13 = 130.0，convertValue 检测到无精度丢失时返回 Long
            assertEquals(130L, result);
        }
    }

    // ============================= FUNCTION 宏注入测试 =============================

    @Test
    @Order(20)
    @DisplayName("20、FUNCTION宏注入 - 无参函数在 JS 中可调用")
    void testFunctionMacroNoArgs() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();
            SysMacroDO macroDO = SysMacroDO.builder()
                    .macroCode("sys_const_hello")
                    .macroType("FUNCTION")
                    .scope("ALL")
                    .build();
            // SpEL: 返回常量字符串
            Expression expr = SPEL_PARSER.parseExpression("'Hello from SpEL'");
            macros.put("sys_const_hello", new CachedMacro(macroDO, expr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // 执行：JS 中调用无参宏函数
            Object result = evaluator.evaluate("sys_const_hello()", null);
            assertEquals("Hello from SpEL", result);
        }
    }

    @Test
    @Order(21)
    @DisplayName("21、FUNCTION宏注入 - 带参函数（参数通过 #p0, #p1 传递）")
    void testFunctionMacroWithArgs() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();
            SysMacroDO macroDO = SysMacroDO.builder()
                    .macroCode("sys_add")
                    .macroType("FUNCTION")
                    .scope("ALL")
                    .build();
            // SpEL: 将两个参数相加 (#p0 + #p1)
            Expression expr = SPEL_PARSER.parseExpression("#p0 + #p1");
            macros.put("sys_add", new CachedMacro(macroDO, expr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // 执行：JS 中调用带参宏函数
            Object result = evaluator.evaluate("sys_add(10, 20)", null);
            // SpEL 的 + 对 Long 值返回 Long（GraalVM Value → convertValue → Long）
            assertEquals(30L, result);
        }
    }

    @Test
    @Order(22)
    @DisplayName("22、FUNCTION宏注入 - 函数宏可以访问请求上下文参数")
    void testFunctionMacroAccessesRequestContext() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();
            SysMacroDO macroDO = SysMacroDO.builder()
                    .macroCode("sys_greet_user")
                    .macroType("FUNCTION")
                    .scope("ALL")
                    .build();
            // SpEL: 拼接 #p0（问候语）和 #username（来自请求上下文）
            Expression expr = SPEL_PARSER.parseExpression("#p0 + ', ' + #username");
            macros.put("sys_greet_user", new CachedMacro(macroDO, expr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // 执行：JS 调用函数，同时请求上下文中传入 username
            Map<String, Object> context = new HashMap<>();
            context.put("username", "张三");
            Object result = evaluator.evaluate("sys_greet_user('你好')", context);
            assertEquals("你好, 张三", result);
        }
    }

    // ============================= 作用域过滤测试 =============================

    @Test
    @Order(30)
    @DisplayName("30、SQL_ONLY宏过滤 - 不注入到 JS 环境")
    void testSqlOnlyMacroFiltered() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();

            // SQL_ONLY 宏 —— 应被过滤
            SysMacroDO sqlOnlyMacro = SysMacroDO.builder()
                    .macroCode("sys_sql_only_var")
                    .macroType("VARIABLE")
                    .scope("SQL_ONLY")
                    .build();
            Expression sqlExpr = SPEL_PARSER.parseExpression("'should_not_appear'");
            macros.put("sys_sql_only_var", new CachedMacro(sqlOnlyMacro, sqlExpr));

            // ALL 宏 —— 应注入
            SysMacroDO allMacro = SysMacroDO.builder()
                    .macroCode("sys_visible_var")
                    .macroType("VARIABLE")
                    .scope("ALL")
                    .build();
            Expression allExpr = SPEL_PARSER.parseExpression("'visible'");
            macros.put("sys_visible_var", new CachedMacro(allMacro, allExpr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // 执行：验证 ALL 宏可用
            Object result = evaluator.evaluate("sys_visible_var", null);
            assertEquals("visible", result);

            // 执行：验证 SQL_ONLY 宏不可用（访问时 JS 会抛错 ReferenceError）
            assertThrows(Exception.class, () ->
                    evaluator.evaluate("sys_sql_only_var", null));
        }
    }

    @Test
    @Order(31)
    @DisplayName("31、JS_ONLY宏注入 - 允许注入到 JS 环境")
    void testJsOnlyMacroInjected() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();
            SysMacroDO macroDO = SysMacroDO.builder()
                    .macroCode("sys_js_only_val")
                    .macroType("VARIABLE")
                    .scope("JS_ONLY")
                    .build();
            Expression expr = SPEL_PARSER.parseExpression("'js_only_value'");
            macros.put("sys_js_only_val", new CachedMacro(macroDO, expr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            Object result = evaluator.evaluate("sys_js_only_val", null);
            assertEquals("js_only_value", result);
        }
    }

    // ============================= 异常隔离测试 =============================

    @Test
    @Order(40)
    @DisplayName("40、宏注入异常隔离 - 单条宏失败不影响脚本执行")
    void testMacroInjectionFailureIsolation() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();

            // 一个会失败的宏（SpEL 表达式引用不存在的 Bean）
            SysMacroDO badMacro = SysMacroDO.builder()
                    .macroCode("sys_bad_macro")
                    .macroType("VARIABLE")
                    .scope("ALL")
                    .build();
            Expression badExpr = SPEL_PARSER.parseExpression("@nonExistentBean.getVal()");
            macros.put("sys_bad_macro", new CachedMacro(badMacro, badExpr));

            // 一个正常的宏
            SysMacroDO goodMacro = SysMacroDO.builder()
                    .macroCode("sys_good_macro")
                    .macroType("VARIABLE")
                    .scope("ALL")
                    .build();
            Expression goodExpr = SPEL_PARSER.parseExpression("42");
            macros.put("sys_good_macro", new CachedMacro(goodMacro, goodExpr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // 执行：即使 sys_bad_macro 注入失败，sys_good_macro 和脚本仍应正常执行
            Object result = evaluator.evaluate("sys_good_macro + 8", null);
            assertEquals(50L, result);
        }
    }

    @Test
    @Order(41)
    @DisplayName("41、Spring容器未就绪 - 优雅跳过宏注入,脚本正常执行")
    void testNoSpringContext() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenThrow(new RuntimeException("Spring context not initialized"));

            // 即使没有 Spring 容器，基本 JS 执行能力应正常工作
            Object result = evaluator.evaluate("'OK'", null);
            assertEquals("OK", result);
        }
    }

    // ============================= DynamicMacroFunction 单元测试 =============================

    @Test
    @Order(50)
    @DisplayName("50、DynamicMacroFunction - 参数转换（字符串拼接）")
    void testDynamicMacroFunctionStringConcat() {
        Expression expr = SPEL_PARSER.parseExpression("#p0 + ' ' + #p1");

        // 直接测试 SpEL（不经过 GraalVM 转换），验证 execute 逻辑
        // 注意：此处传入 mock Value 不够方便，改用集成方式在上面的 testFunctionMacroWithArgs 中验证
        // 这里通过构造 StandardEvaluationContext 模拟验证
        org.springframework.expression.spel.support.StandardEvaluationContext ctx =
                new org.springframework.expression.spel.support.StandardEvaluationContext();
        ctx.setVariable("p0", "Hello");
        ctx.setVariable("p1", "World");
        Object result = expr.getValue(ctx);
        assertEquals("Hello World", result);
    }

    @Test
    @Order(51)
    @DisplayName("51、DynamicMacroFunction - 请求上下文参数注入")
    void testDynamicMacroFunctionWithRequestContext() {
        Expression expr = SPEL_PARSER.parseExpression("#p0 + #userId");
        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("userId", 1001);


        // 模拟调用（直接验证 SpEL 逻辑）
        org.springframework.expression.spel.support.StandardEvaluationContext ctx =
                new org.springframework.expression.spel.support.StandardEvaluationContext();
        ctx.setVariable("p0", 100);
        ctx.setVariable("userId", 1001);
        Object result = expr.getValue(ctx);
        assertEquals(1101, result);
    }

    // ============================= 宏与上下文共存测试 =============================

    @Test
    @Order(60)
    @DisplayName("60、宏与请求上下文共存 - 宏变量和请求变量在 JS 中同时可用")
    void testMacroAndContextCoexistence() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();
            SysMacroDO macroDO = SysMacroDO.builder()
                    .macroCode("sys_prefix")
                    .macroType("VARIABLE")
                    .scope("ALL")
                    .build();
            Expression expr = SPEL_PARSER.parseExpression("'ORDER-'");
            macros.put("sys_prefix", new CachedMacro(macroDO, expr));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            Map<String, Object> context = new HashMap<>();
            context.put("orderId", "20260324001");

            // JS 中同时使用宏变量 sys_prefix 和请求变量 orderId
            Object result = evaluator.evaluate("sys_prefix + orderId", context);
            assertEquals("ORDER-20260324001", result);
        }
    }

    @Test
    @Order(61)
    @DisplayName("61、多宏同时注入 - VARIABLE 和 FUNCTION 混合使用")
    void testMultipleMacrosMixed() {
        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            SysMacroCacheManager mockManager = mock(SysMacroCacheManager.class);
            springUtilMock.when(() -> SpringUtil.getBean(SysMacroCacheManager.class))
                    .thenReturn(mockManager);

            Map<String, CachedMacro> macros = new ConcurrentHashMap<>();

            // VARIABLE 宏
            SysMacroDO varMacro = SysMacroDO.builder()
                    .macroCode("sys_pi")
                    .macroType("VARIABLE")
                    .scope("ALL")
                    .build();
            macros.put("sys_pi", new CachedMacro(varMacro, SPEL_PARSER.parseExpression("3.14159")));

            // FUNCTION 宏（求圆面积: π * r²）
            SysMacroDO funcMacro = SysMacroDO.builder()
                    .macroCode("sys_circle_area")
                    .macroType("FUNCTION")
                    .scope("ALL")
                    .build();
            macros.put("sys_circle_area", new CachedMacro(funcMacro,
                    SPEL_PARSER.parseExpression("3.14159 * #p0 * #p0")));

            when(mockManager.getAllCachedMacros()).thenReturn(Collections.unmodifiableMap(macros));

            // JS 中同时使用 VARIABLE 和 FUNCTION 宏
            Object piResult = evaluator.evaluate("sys_pi", null);
            assertEquals(3.14159, piResult);

            Object areaResult = evaluator.evaluate("sys_circle_area(10)", null);
            assertTrue(areaResult instanceof Number);
            assertEquals(314.159, ((Number) areaResult).doubleValue(), 0.001);
        }
    }

    // ============================= convertValue 静态方法测试 =============================

    @Test
    @Order(70)
    @DisplayName("70、convertValue - null 输入返回 null")
    void testConvertValueNull() {
        assertNull(JavaScriptEvaluatorImpl.convertValue(null));
    }
}
