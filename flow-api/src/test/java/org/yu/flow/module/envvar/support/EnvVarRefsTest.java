package org.yu.flow.module.envvar.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager.CachedEnvVariable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnvVarRefsTest {

    private EnvVariableCacheManager cache;

    @BeforeEach
    void setUp() {
        cache = mock(EnvVariableCacheManager.class);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(cache.get("PAY_URL")).thenReturn(Optional.of(new CachedEnvVariable("PAY_URL", "https://pay.example.com", false)));
        when(cache.get("PAY_KEY")).thenReturn(Optional.of(new CachedEnvVariable("PAY_KEY", "k-9f8e7d", true)));
        ReflectionTestUtils.setField(EnvVarRefs.class, "cacheManager", cache);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(EnvVarRefs.class, "cacheManager", null);
    }

    @Test
    void validatesCode() {
        assertTrue(EnvVarRefs.isValidCode("PAY_BASE_URL"));
        assertTrue(EnvVarRefs.isValidCode("A1"));
        assertFalse(EnvVarRefs.isValidCode("pay_url"));
        assertFalse(EnvVarRefs.isValidCode("1ABC"));
        assertFalse(EnvVarRefs.isValidCode("A-B"));
        assertFalse(EnvVarRefs.isValidCode(null));
    }

    @Test
    void recognizesInputPathOnlyForWholeEnvPath() {
        assertEquals("PAY_URL", EnvVarRefs.inputPathCode("$.env.PAY_URL"));
        assertNull(EnvVarRefs.inputPathCode("$.env.PAY_URL.sub"));
        assertNull(EnvVarRefs.inputPathCode("$.start.env"));
        assertNull(EnvVarRefs.inputPathCode(null));
    }

    @Test
    void scansBothSyntaxes() {
        String dsl = "{\"url\":\"${env.PAY_URL}/order\",\"inputs\":{\"k\":\"$.env.PAY_KEY\"},\"x\":\"${ env.PAY_URL }\"}";
        assertEquals(Set.of("PAY_URL", "PAY_KEY"), EnvVarRefs.scan(dsl));
        assertTrue(EnvVarRefs.scan("{\"url\":\"$.start.args.env\"}").isEmpty());
    }

    @Test
    void resolveRegistersOnlySecretValues() {
        ExecutionContext ctx = new ExecutionContext();
        assertEquals("https://pay.example.com", EnvVarRefs.resolve("PAY_URL", ctx));
        assertTrue(ctx.getSecretValues().isEmpty());
        assertEquals("k-9f8e7d", EnvVarRefs.resolve("PAY_KEY", ctx));
        assertEquals(Set.of("k-9f8e7d"), ctx.getSecretValues());
    }

    @Test
    void missingVariableFailsFast() {
        FlowException ex = assertThrows(FlowException.class,
                () -> EnvVarRefs.resolve("NOT_SET", new ExecutionContext()));
        assertTrue(ex.getMessage().contains("NOT_SET"));
    }

    @Test
    void injectTemplateRefsKeepsNodeDefinedInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("env.PAY_URL", "node-override");
        ExecutionContext ctx = new ExecutionContext();
        EnvVarRefs.injectTemplateRefs(inputs, ctx, "${env.PAY_URL}/pay", null, "Bearer ${env.PAY_KEY}");
        assertEquals("node-override", inputs.get("env.PAY_URL"));
        assertEquals("k-9f8e7d", inputs.get("env.PAY_KEY"));
        assertTrue(ctx.getSecretValues().contains("k-9f8e7d"));
    }

    @Test
    void secretSetSharedAcrossBranchCopies() {
        ExecutionContext ctx = new ExecutionContext(null, false, true);
        ExecutionContext branch = ctx.copy(false);
        EnvVarRefs.resolve("PAY_KEY", branch);
        assertTrue(ctx.getSecretValues().contains("k-9f8e7d"));
        assertTrue(ctx.getFlowTrace().getSecretValues().contains("k-9f8e7d"));
    }
}
