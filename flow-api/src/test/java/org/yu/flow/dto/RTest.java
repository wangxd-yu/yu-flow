package org.yu.flow.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R — 通用响应包装类单元测试。
 */
class RTest {

    @Test
    void ok_noData_shouldBeSuccess() {
        R<Void> result = R.ok();
        assertTrue(result.getOk());
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
    }

    @Test
    void ok_withData_shouldContainData() {
        R<String> result = R.ok("hello");
        assertTrue(result.getOk());
        assertEquals("hello", result.getData());
    }

    @Test
    void ok_withDataAndMsg_shouldSetBoth() {
        R<String> result = R.ok("data", "custom msg");
        assertTrue(result.getOk());
        assertEquals("data", result.getData());
        assertEquals("custom msg", result.getMsg());
        assertEquals(200, result.getCode());
    }

    @Test
    void fail_withMsg_shouldNotBeSuccess() {
        R<Void> result = R.fail("something wrong");
        assertFalse(result.getOk());
        assertEquals(ResultCode.FAILED.getCode(), result.getCode());
        assertEquals("something wrong", result.getMsg());
    }

    @Test
    void fail_withResultCode_shouldUseCode() {
        R<Void> result = R.fail(ResultCode.FORBIDDEN);
        assertFalse(result.getOk());
        assertEquals(ResultCode.FORBIDDEN.getCode(), result.getCode());
    }

    @Test
    void ok_withCode_shouldSetOkDynamically() {
        R<String> result = R.ok(500, "server error", "payload");
        assertFalse(result.getOk());
        assertEquals(500, result.getCode());
        assertEquals("payload", result.getData());
    }

    @Test
    void fail_withData_shouldContainData() {
        R<String> result = R.fail(400, "bad request", "details");
        assertFalse(result.getOk());
        assertEquals(400, result.getCode());
        assertEquals("details", result.getData());
    }

    @Test
    void failWithErrorCode_shouldSetErrorCode() {
        R<Void> result = R.failWithErrorCode(401, "UNAUTHORIZED", "please login");
        assertFalse(result.getOk());
        assertEquals(401, result.getCode());
        assertEquals("UNAUTHORIZED", result.getErrorCode());
        assertEquals("please login", result.getMsg());
    }

    @Test
    void timestamp_shouldBeGenerated() {
        R<Void> result = R.ok();
        assertTrue(result.getTimestamp() > 0);
    }
}
