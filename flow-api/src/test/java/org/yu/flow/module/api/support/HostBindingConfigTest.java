package org.yu.flow.module.api.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostBindingConfigTest {

    @Test
    void parseDefaults() {
        HostBindingConfig cfg = HostBindingConfig.parse(null);
        assertEquals("LOCAL", cfg.getForward());
        assertEquals(HostBindingConfig.LOG_ALL, cfg.getLogMode());
        assertFalse(cfg.isProbeEnabled());
        assertTrue(cfg.shouldLog(true));
        assertTrue(cfg.shouldLog(false));
    }

    @Test
    void errorOnlyLogsFailures() {
        HostBindingConfig cfg = HostBindingConfig.parse("{\"logMode\":\"ERROR_ONLY\"}");
        assertFalse(cfg.shouldLog(true));
        assertTrue(cfg.shouldLog(false));
    }

    @Test
    void sampleAlwaysLogsFailures() {
        HostBindingConfig cfg = HostBindingConfig.parse(
                "{\"logMode\":\"SAMPLE\",\"logSamplePermille\":0}");
        assertFalse(cfg.shouldLog(true));
        assertTrue(cfg.shouldLog(false));
    }

    @Test
    void resolveProbePathFallback() {
        HostBindingConfig cfg = HostBindingConfig.parse("{\"targetPath\":\"/a\"}");
        assertEquals("/a", cfg.resolveProbePath("/biz"));
        cfg.setProbePath("/p");
        assertEquals("/p", cfg.resolveProbePath("/biz"));
    }
}
