package org.yu.flow.auto.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlIdentifierSanitizer 单测（Phase 2.3 安全加固验证）。
 *
 * <p>覆盖场景：合法标识符放行、注入 payload 拒绝、允许列表双重校验、日志脱敏截断。</p>
 */
class SqlIdentifierSanitizerTest {

    // ========== requireSafeIdentifier 合法情况 ==========

    @ParameterizedTest(name = "合法标识符应通过: [{0}]")
    @ValueSource(strings = {"id", "user_name", "create_time", "_internal", "col123", "A", "a_b_c_1"})
    void safeIdentifier_passes(String identifier) {
        assertEquals(identifier, SqlIdentifierSanitizer.requireSafeIdentifier(identifier));
    }

    // ========== requireSafeIdentifier 注入 payload 拒绝 ==========

    @ParameterizedTest(name = "注入 payload 应被拒绝: [{0}]")
    @ValueSource(strings = {
            "1col",                              // 数字开头
            "col name",                          // 含空格
            "col-name",                          // 含连字符
            "col;DROP TABLE users",              // SQL 注入
            "col' OR '1'='1",                   // 引号注入
            "col`; DELETE FROM flow_api_info",   // 反引号注入
            "col\nUNION SELECT * FROM sysuser",  // 换行注入
            ""                                   // 空字符串
    })
    void injectionPayload_rejected(String identifier) {
        assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifierSanitizer.requireSafeIdentifier(identifier));
    }

    @Test
    void null_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifierSanitizer.requireSafeIdentifier(null));
    }

    @Test
    void tooLong_rejected() {
        String tooLong = "a".repeat(129);
        assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifierSanitizer.requireSafeIdentifier(tooLong));
    }

    @Test
    void exactly128Chars_passes() {
        String just128 = "a".repeat(128);
        assertEquals(just128, SqlIdentifierSanitizer.requireSafeIdentifier(just128));
    }

    // ========== 允许列表双重校验 ==========

    @Test
    void allowlist_passes_whenInSet() {
        String col = SqlIdentifierSanitizer.requireSafeIdentifier(
                "create_time", Set.of("name", "create_time", "status"));
        assertEquals("create_time", col);
    }

    @Test
    void allowlist_rejected_whenNotInSet() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifierSanitizer.requireSafeIdentifier(
                        "admin_role", Set.of("name", "create_time", "status")));
    }

    @Test
    void allowlist_rejected_whenAllowlistEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifierSanitizer.requireSafeIdentifier("name", Set.of()));
    }

    @Test
    void allowlist_rejected_whenAllowlistNull() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifierSanitizer.requireSafeIdentifier("name", null));
    }

    // ========== isSafeIdentifier 无异常版本 ==========

    @Test
    void isSafeIdentifier_trueForValid() {
        assertTrue(SqlIdentifierSanitizer.isSafeIdentifier("order_no"));
    }

    @Test
    void isSafeIdentifier_falseForInjection() {
        assertFalse(SqlIdentifierSanitizer.isSafeIdentifier("order; DROP TABLE x"));
        assertFalse(SqlIdentifierSanitizer.isSafeIdentifier(null));
        assertFalse(SqlIdentifierSanitizer.isSafeIdentifier(""));
    }

    // ========== 日志脱敏：错误消息不暴露完整注入 payload ==========

    @Test
    void errorMessage_truncatesLongPayload() {
        String longInjection = "a".repeat(200) + "; DROP TABLE users";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifierSanitizer.requireSafeIdentifier(longInjection));
        // 错误消息因为标识符过长触发长度校验，不应包含完整 payload
        assertFalse(ex.getMessage().contains("DROP TABLE"));
    }
}
