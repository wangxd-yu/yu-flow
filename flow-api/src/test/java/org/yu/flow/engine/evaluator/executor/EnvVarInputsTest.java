package org.yu.flow.engine.evaluator.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.TracePersistUtil;
import org.yu.flow.engine.model.step.HttpRequestStep;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager.CachedEnvVariable;
import org.yu.flow.module.envvar.support.EnvVarRefs;
import org.yu.flow.util.SecretMasker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 节点输入参数里的 {@code $.env.CODE}：取值、轨迹掩码、落库脱敏。
 */
class EnvVarInputsTest {

    private static final String SECRET = "sk-live-123456";

    private final AbstractStepExecutor<HttpRequestStep> executor = new AbstractStepExecutor<>() {
        @Override
        public String execute(HttpRequestStep step, ExecutionContext context, FlowDefinition flow) {
            return null;
        }
    };

    @BeforeEach
    void setUp() {
        EnvVariableCacheManager cache = mock(EnvVariableCacheManager.class);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(cache.get("PAY_URL")).thenReturn(Optional.of(new CachedEnvVariable("PAY_URL", "https://pay.example.com", false)));
        when(cache.get("PAY_KEY")).thenReturn(Optional.of(new CachedEnvVariable("PAY_KEY", SECRET, true)));
        ReflectionTestUtils.setField(EnvVarRefs.class, "cacheManager", cache);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(EnvVarRefs.class, "cacheManager", null);
    }

    private HttpRequestStep step(Map<String, Object> inputs) {
        HttpRequestStep step = new HttpRequestStep();
        step.setId("http_1");
        step.setInputs(inputs);
        return step;
    }

    @Test
    void envPathResolvedAndSecretMaskedInTraceInputs() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("baseUrl", "$.env.PAY_URL");
        cfg.put("apiKey", "$.env.PAY_KEY");
        cfg.put("orderId", "$.start.orderId");
        ExecutionContext ctx = new ExecutionContext(Map.of("start", Map.of("orderId", "A1")), false, true);

        Map<String, Object> inputs = executor.prepareInputs(step(cfg), ctx, new FlowDefinition());

        assertEquals("https://pay.example.com", inputs.get("baseUrl"));
        assertEquals(SECRET, inputs.get("apiKey"));
        assertEquals("A1", inputs.get("orderId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> traced = (Map<String, Object>) ctx.getCache("TRACE_INPUTS_http_1");
        assertEquals(SecretMasker.MASK, traced.get("apiKey"));
        assertEquals("https://pay.example.com", traced.get("baseUrl"));
        assertFalse(ctx.getVar().containsKey("env"), "环境变量不应写入上下文变量表");
    }

    @Test
    void nodeNamedEnvTakesPrecedence() {
        FlowDefinition flow = new FlowDefinition();
        HttpRequestStep envNode = new HttpRequestStep();
        envNode.setId("env");
        flow.addStep(envNode);
        ExecutionContext ctx = new ExecutionContext(Map.of("env", Map.of("PAY_URL", "from-node")), false, false);

        Map<String, Object> inputs = executor.prepareInputs(step(Map.of("u", "$.env.PAY_URL")), ctx, flow);

        assertEquals("from-node", inputs.get("u"));
    }

    @Test
    void persistedTraceHasSecretMasked() throws Exception {
        ExecutionContext ctx = new ExecutionContext(null, false, true);
        executor.prepareInputs(step(Map.of("apiKey", "$.env.PAY_KEY")), ctx, new FlowDefinition());
        ExecutionLog stepLog = new ExecutionLog();
        stepLog.setStatus("error");
        stepLog.setOutputs(Map.of("echo", "Authorization: Bearer " + SECRET));
        ctx.getFlowTrace().setStepLogs(List.of(stepLog));
        ctx.getFlowTrace().setErrorMsg("call failed with key=" + SECRET);

        String json = TracePersistUtil.serializeForPersist(ctx.getFlowTrace(), null, new ObjectMapper(), null);

        assertFalse(json.contains(SECRET));
        assertTrue(json.contains(SecretMasker.MASK));
    }
}
