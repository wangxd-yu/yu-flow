package org.yu.flow.auto.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FieldNameUtils unit tests.
 */
class FieldNameUtilsTest {

    @Test
    void toCamelCase_normalUnderscore_shouldConvert() {
        assertEquals("userName", FieldNameUtils.toCamelCase("user_name"));
        assertEquals("orderItemId", FieldNameUtils.toCamelCase("order_item_id"));
    }

    @Test
    void toCamelCase_emptyOrNull_shouldReturnAsIs() {
        assertNull(FieldNameUtils.toCamelCase(null));
        assertEquals("", FieldNameUtils.toCamelCase(""));
    }

    @Test
    void toCamelCase_noUnderscore_shouldLowerCase() {
        assertEquals("username", FieldNameUtils.toCamelCase("USERNAME"));
    }
}
