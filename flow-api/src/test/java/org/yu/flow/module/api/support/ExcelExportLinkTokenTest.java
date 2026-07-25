package org.yu.flow.module.api.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 短期下载链（payload.hmac）安全语义锁定：
 * TTL 过期、签名篡改、弱默认密钥拒签、payload 往返、签名高熵。
 */
class ExcelExportLinkTokenTest {

    private YuFlowProperties props;

    @BeforeEach
    void setUp() {
        props = new YuFlowProperties();
        props.getSecurity().setJwtSecretKey("unit-test-jwt-secret-with-enough-length!!");
    }

    private static ApiDataExportRequestDTO sampleParams() {
        ApiDataExportRequestDTO params = new ApiDataExportRequestDTO();
        params.setQueryParams(Map.of("name", "yu"));
        params.setBodyParams(Map.of("size", 10));
        params.setPathParams(Map.of("id", "1"));
        return params;
    }

    @Test
    void issueAndVerify_roundTrip() {
        String token = ExcelExportLinkToken.issue(props, "api-1", "admin", sampleParams(), 300);
        ExcelExportLinkToken.Parsed parsed = ExcelExportLinkToken.verify(props, token);

        assertEquals("api-1", parsed.apiId());
        assertEquals("yu", parsed.request().getQueryParams().get("name"));
        assertEquals("1", parsed.request().getPathParams().get("id"));
        assertEquals(10, parsed.request().getBodyParams().get("size"));
        // 强制按已发布快照导出，忽略草稿
        assertFalse(Boolean.TRUE.equals(parsed.request().getUseDraft()));
    }

    @Test
    void verify_expiredToken_rejected() throws InterruptedException {
        // issue 对 ttl 下限收敛为 1 秒；epoch 秒级比较，需睡过 2 秒边界
        String token = ExcelExportLinkToken.issue(props, "api-1", "admin", null, 0);
        Thread.sleep(2100L);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> ExcelExportLinkToken.verify(props, token));
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    void verify_tamperedPayload_rejected() {
        String token = ExcelExportLinkToken.issue(props, "api-1", "admin", null, 300);
        String tamperedApi = ExcelExportLinkToken.issue(props, "api-2", "admin", null, 300);
        // 用 api-2 的 payload 拼 api-1 的签名 → 必须失败
        String forged = tamperedApi.substring(0, tamperedApi.indexOf('.'))
                + token.substring(token.indexOf('.'));
        assertThrows(ValidationException.class, () -> ExcelExportLinkToken.verify(props, forged));
    }

    @Test
    void verify_signatureBitFlip_rejected() {
        String token = ExcelExportLinkToken.issue(props, "api-1", "admin", null, 300);
        char last = token.charAt(token.length() - 1);
        String flipped = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThrows(ValidationException.class, () -> ExcelExportLinkToken.verify(props, flipped));
    }

    @Test
    void verify_wrongKey_rejected() {
        String token = ExcelExportLinkToken.issue(props, "api-1", "admin", null, 300);
        YuFlowProperties other = new YuFlowProperties();
        other.getSecurity().setJwtSecretKey("another-jwt-secret-with-enough-length!!!!");
        assertThrows(ValidationException.class, () -> ExcelExportLinkToken.verify(other, token));
    }

    @Test
    void issue_insecureDefaultKey_rejected() {
        YuFlowProperties insecure = new YuFlowProperties();
        insecure.getSecurity().setJwtSecretKey("ss-flow-699");
        assertThrows(ValidationException.class,
                () -> ExcelExportLinkToken.issue(insecure, "api-1", "admin", null, 300));

        YuFlowProperties blank = new YuFlowProperties();
        blank.getSecurity().setJwtSecretKey("");
        assertThrows(ValidationException.class,
                () -> ExcelExportLinkToken.issue(blank, "api-1", "admin", null, 300));
    }

    @Test
    void verify_malformedToken_rejected() {
        assertThrows(ValidationException.class, () -> ExcelExportLinkToken.verify(props, null));
        assertThrows(ValidationException.class, () -> ExcelExportLinkToken.verify(props, ""));
        assertThrows(ValidationException.class, () -> ExcelExportLinkToken.verify(props, "no-dot"));
        assertThrows(ValidationException.class, () -> ExcelExportLinkToken.verify(props, "aaa.bbb"));
    }

    @Test
    void signature_isHmacSha256Entropy() {
        String token = ExcelExportLinkToken.issue(props, "api-1", "admin", null, 300);
        String sig = token.substring(token.indexOf('.') + 1);
        // HMAC-SHA256 → 32 字节 → Base64URL 无 padding 恒为 43 字符
        assertEquals(43, sig.length());
        // 同 payload 不同 apiId 的签名不同（无碰撞退化）
        String other = ExcelExportLinkToken.issue(props, "api-9", "admin", null, 300);
        assertFalse(sig.equals(other.substring(other.indexOf('.') + 1)));
    }
}
