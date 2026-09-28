package org.yu.flow.module.envvar.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvVarExtractionServiceTest {

    @Test
    void suggestsCodeFromHost() {
        assertEquals("PAY_EXAMPLE_COM_URL", EnvVarExtractionService.suggestCode("https://pay.example.com"));
        assertEquals("API_PARTNER_CN_8443_URL", EnvVarExtractionService.suggestCode("https://api.partner.cn:8443"));
        assertEquals("HOST_10_0_0_8_URL", EnvVarExtractionService.suggestCode("http://10.0.0.8"));
    }

    @Test
    void rewritesCanvasAndEngineFormats() {
        String dsl = "{\"nodes\":[{\"id\":\"n1\",\"type\":\"httpRequest\",\"data\":{\"url\":\"https://pay.example.com/v1/order\"}},"
                + "{\"id\":\"n2\",\"type\":\"httpRequest\",\"url\":\"HTTPS://PAY.EXAMPLE.COM/v1/refund\"},"
                + "{\"id\":\"n3\",\"type\":\"httpRequest\",\"data\":{\"url\":\"https://other.com/x\"}},"
                + "{\"id\":\"n4\",\"type\":\"database\",\"data\":{\"url\":\"https://pay.example.com/not-http-node\"}}]}";
        int[] counts = new int[2];

        String out = EnvVarExtractionService.rewrite(dsl, "https://pay.example.com", "${env.PAY_URL}", counts);

        assertNotNull(out);
        assertTrue(out.contains("\"url\":\"${env.PAY_URL}/v1/order\""));
        assertTrue(out.contains("\"url\":\"${env.PAY_URL}/v1/refund\""));
        assertTrue(out.contains("https://other.com/x"));
        assertTrue(out.contains("https://pay.example.com/not-http-node"), "非 httpRequest 节点不改");
        assertArrayEquals(new int[]{1, 2}, counts);
    }

    @Test
    void noMatchReturnsNull() {
        int[] counts = new int[2];
        assertNull(EnvVarExtractionService.rewrite("{\"nodes\":[]}", "https://pay.example.com", "${env.X}", counts));
        assertNull(EnvVarExtractionService.rewrite(null, "https://pay.example.com", "${env.X}", counts));
        assertArrayEquals(new int[]{0, 0}, counts);
    }
}
