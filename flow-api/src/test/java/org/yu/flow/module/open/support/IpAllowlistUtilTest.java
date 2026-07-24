package org.yu.flow.module.open.support;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;

import static org.junit.jupiter.api.Assertions.*;

class IpAllowlistUtilTest {

    @Test
    void normalize_jsonArray() {
        String out = IpAllowlistUtil.normalizeAndValidate("[\"1.2.3.4\",\"10.0.0.0/8\"]");
        assertTrue(out.contains("1.2.3.4"));
        assertTrue(out.contains("10.0.0.0/8"));
    }

    @Test
    void normalize_blank() {
        assertNull(IpAllowlistUtil.normalizeAndValidate("  "));
        assertNull(IpAllowlistUtil.normalizeAndValidate("[]"));
    }

    @Test
    void normalize_invalid() {
        assertThrows(FlowException.class, () -> IpAllowlistUtil.normalizeAndValidate("[\"999.1.1.1\"]"));
        assertThrows(FlowException.class, () -> IpAllowlistUtil.normalizeAndValidate("[\"10.0.0.0/99\"]"));
    }
}
