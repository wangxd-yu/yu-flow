package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.dto.R;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyFieldInterceptorTest {

    private PrivacyFieldInterceptor interceptor;
    private PrivacyCryptoService crypto;
    private EffectivePrivacy cfg;

    @BeforeEach
    void setUp() throws Exception {
        YuFlowProperties props = new YuFlowProperties();
        props.getPrivacy().setAtRestSm4Key("0123456789abcdef");
        crypto = new PrivacyCryptoService();
        setField(crypto, "yuFlowProperties", props);

        interceptor = new PrivacyFieldInterceptor();
        setField(interceptor, "privacyCryptoService", crypto);

        cfg = new EffectivePrivacy(true, "_encrypt", Set.of("id_no"), true,
                "SM4", "default", PrivacyConfigMerge.defaultMask(), ApiPrivacyConfig.FAIL_MASK);
    }

    @Test
    void mask_stripsSuffix_andNeverLeaksCipher() {
        String phoneCipher = crypto.encryptAtRest("13812341234");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone_encrypt", phoneCipher);
        row.put("name_encrypt", "not-a-cipher");
        row.put("title", "ok");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) interceptor.apply(
                row, cfg, PrivacyClass.MASK, null, true);

        assertEquals("138****1234", out.get("phone"));
        assertEquals("****", out.get("name"));
        assertEquals("ok", out.get("title"));
        assertFalse(out.containsKey("phone_encrypt"));
        assertFalse(String.valueOf(out.get("name")).contains("not-a-cipher"));
    }

    @Test
    void reveal_withoutTransportKey_downgradesToMask() {
        Map<String, Object> row = Map.of("phone_encrypt", crypto.encryptAtRest("13812341234"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) interceptor.apply(
                row, cfg, PrivacyClass.REVEAL, null, true);
        assertEquals("138****1234", out.get("phone"));
    }

    @Test
    void reveal_wrapsSm4Envelope() {
        byte[] key = "1234567890abcdef".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> row = Map.of("phone_encrypt", crypto.encryptAtRest("13812341234"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) interceptor.apply(
                row, cfg, PrivacyClass.REVEAL, key, true);
        Object phone = out.get("phone");
        assertInstanceOf(Map.class, phone);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) phone;
        assertEquals(1, envelope.get(PrivacyCryptoService.ENC_FLAG));
        String hex = String.valueOf(envelope.get(PrivacyCryptoService.ENC_VAL));
        assertEquals("13812341234", PrivacyCryptoService.sm4CbcDecrypt(key, hex));
    }

    @Test
    void excelPath_revealIsPlaintext() {
        Map<String, Object> row = Map.of("phone_encrypt", crypto.encryptAtRest("13812341234"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) interceptor.apply(
                row, cfg, PrivacyClass.REVEAL, null, false);
        assertEquals("13812341234", out.get("phone"));
    }

    @Test
    void walksNestedAndRWrapper() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("id_no", crypto.encryptAtRest("31010119900101123X"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", List.of(inner));
        R<Map<String, Object>> wrapped = R.ok(data);

        @SuppressWarnings("unchecked")
        R<Map<String, Object>> out = (R<Map<String, Object>>) interceptor.apply(
                wrapped, cfg, PrivacyClass.MASK, null, false);
        assertTrue(out.getOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) out.getData().get("records");
        assertEquals("3****************X", records.get(0).get("id_no"));
    }

    @Test
    void usesProfileKeyAndCustomMaskRule() {
        String profileKey = "abcdefghijklmnop";
        PrivacyMaskRule phone = new PrivacyMaskRule();
        phone.setAliases(List.of("phone"));
        phone.setMethod(PrivacyMaskRule.KEEP_HEAD_TAIL);
        phone.setKeepHead(3);
        phone.setKeepTail(4);
        phone.setMaskLen(4);
        EffectivePrivacy profileCfg = new EffectivePrivacy(
                true, "_encrypt", Set.of(), true,
                "SM4", "default", Map.of(), ApiPrivacyConfig.FAIL_MASK,
                "staff", PrivacyDecryptAlg.SM4_CBC, profileKey, List.of(phone));

        Map<String, Object> row = Map.of("phone_encrypt",
                crypto.encryptAtRest("13812341234", PrivacyDecryptAlg.SM4_CBC, profileKey));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) interceptor.apply(
                row, profileCfg, PrivacyClass.MASK, null, false);
        assertEquals("138****1234", out.get("phone"));

        Map<String, Object> yamlRow = Map.of("phone_encrypt", crypto.encryptAtRest("13812341234"));
        @SuppressWarnings("unchecked")
        Map<String, Object> yamlOut = (Map<String, Object>) interceptor.apply(
                yamlRow, profileCfg, PrivacyClass.MASK, null, false);
        assertEquals("****", yamlOut.get("phone"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
