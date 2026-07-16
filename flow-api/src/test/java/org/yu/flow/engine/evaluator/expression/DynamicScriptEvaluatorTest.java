package org.yu.flow.engine.evaluator.expression;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicScriptEvaluatorTest {

    @AfterEach
    void clearGroovyCache() {
        GroovyEvaluatorImpl.clearCompiledCache();
    }

    @Test
    void parsesNewLanguagesWithoutChangingUnknownFallback() {
        assertEquals(ExpressionLanguage.PYTHON, ExpressionLanguage.fromString("PyThOn"));
        assertEquals(ExpressionLanguage.PYTHON, ExpressionLanguage.fromString("py"));
        assertEquals(ExpressionLanguage.GROOVY, ExpressionLanguage.fromString("Groovy"));
        assertEquals(ExpressionLanguage.AVIATOR, ExpressionLanguage.fromString("unknown"));
        assertEquals(ExpressionLanguage.AVIATOR, ExpressionLanguage.fromString(null));
    }

    @Test
    void keepsExistingEvaluatorBehavior() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", 2);
        input.put("b", 3);

        assertEquals("legacy",
                ExpressionEvaluatorFactory.getEvaluator((String) null).evaluate("'legacy'", input));
        assertEquals(5,
                ExpressionEvaluatorFactory.getEvaluator("spel").evaluate("#a + #b", input));
        assertEquals(5L,
                ExpressionEvaluatorFactory.getEvaluator("javascript").evaluate("a + b", input));
    }

    @Test
    void executesAndCachesGroovyScriptOnceAcrossThreads() throws Exception {
        GroovyEvaluatorImpl evaluator = new GroovyEvaluatorImpl();
        String script = "return input.values.collect { it * 2 }.sum()";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("values", Arrays.asList(1, 2, 3));

        ExecutorService executor = Executors.newFixedThreadPool(6);
        for (int i = 0; i < 30; i++) {
            executor.submit(() -> assertEquals(12, evaluator.evaluate(script, input)));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        assertEquals(1, GroovyEvaluatorImpl.compiledCacheSize());
    }

    @Test
    void blocksDangerousGroovyAndNonWhitelistedBean() {
        GroovyEvaluatorImpl evaluator = new GroovyEvaluatorImpl();
        FlowException securityError = assertThrows(FlowException.class,
                () -> evaluator.evaluate(
                        "java.lang.Runtime.getRuntime().exec('cmd')", Collections.emptyMap()));
        assertEquals("GROOVY_SECURITY_ERROR", securityError.getErrorCode());

        FlowException beanError = assertThrows(FlowException.class,
                () -> new GroovyEvaluatorImpl.RestrictedSpringAccess().getBean("secretBean"));
        assertEquals("GROOVY_BEAN_ACCESS_DENIED", beanError.getErrorCode());
    }

    @Test
    void supportsWhitelistedGroovySignatureClasses() {
        GroovyEvaluatorImpl evaluator = new GroovyEvaluatorImpl();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", "yu-flow");
        Object result = evaluator.evaluate(
                "import java.security.MessageDigest\n"
                        + "def digest = MessageDigest.getInstance('SHA-256').digest(input.text.bytes)\n"
                        + "return digest.encodeHex().toString()",
                input);
        assertEquals("8c71e1094928371afa16f982c17080a5b6407b2ab3885fd196fb25f42ce6778b", result);
    }

    @Test
    void convertsNestedPolyglotValues() {
        try (Context context = Context.newBuilder("js").build()) {
            Object converted = PythonEvaluatorImpl.convertValue(
                    context.eval("js", "({name: 'yu-flow', values: [1, true, null]})"));
            assertTrue(converted instanceof Map);
            assertEquals("yu-flow", ((Map<?, ?>) converted).get("name"));
            assertEquals(Arrays.asList(1L, true, null), ((Map<?, ?>) converted).get("values"));
        }
    }

    @Test
    void executesPythonWhenInstalledOrSilentlySkipsWhenUnavailable() {
        PythonEvaluatorImpl evaluator = new PythonEvaluatorImpl();
        if (!PythonEvaluatorImpl.isRuntimeAvailable()) {
            assertEquals(null, evaluator.evaluate("1 + 2", Collections.emptyMap()));
            return;
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", Arrays.asList(2, 3, 4));
        Object result = evaluator.evaluate(
                "total = sum(input['items'])\nreturn {'total': total, 'values': [x * 2 for x in items]}",
                input);
        assertTrue(result instanceof Map);
        assertEquals(9L, ((Map<?, ?>) result).get("total"));
        assertEquals(Arrays.asList(4L, 6L, 8L), ((Map<?, ?>) result).get("values"));
    }
}
