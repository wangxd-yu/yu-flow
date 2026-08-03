package org.yu.flow.engine.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志策略：摘要 / 报文 / Trace 门控语义。
 */
class LogModeTest {

    @Test
    void errorOnly_recordsPayloadOnFailure_butNotTrace() {
        assertTrue(LogMode.shouldRecord(LogMode.ERROR_ONLY, false));
        assertTrue(LogMode.shouldRecordPayload(LogMode.ERROR_ONLY, false));
        assertFalse(LogMode.shouldRecordTrace(LogMode.ERROR_ONLY, false));

        assertFalse(LogMode.shouldRecord(LogMode.ERROR_ONLY, true));
        assertFalse(LogMode.shouldRecordPayload(LogMode.ERROR_ONLY, true));
    }

    @Test
    void all_recordsPayloadAndTrace() {
        assertTrue(LogMode.shouldRecord(LogMode.ALL, true));
        assertTrue(LogMode.shouldRecordPayload(LogMode.ALL, true));
        assertTrue(LogMode.shouldRecordTrace(LogMode.ALL, true));
    }

    @Test
    void off_recordsNothing() {
        assertFalse(LogMode.shouldRecord(LogMode.OFF, false));
        assertFalse(LogMode.shouldRecordPayload(LogMode.OFF, false));
        assertFalse(LogMode.shouldRecordTrace(LogMode.OFF, false));
    }
}
