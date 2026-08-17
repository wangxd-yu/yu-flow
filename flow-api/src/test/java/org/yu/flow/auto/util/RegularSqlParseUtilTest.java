package org.yu.flow.auto.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.yu.flow.exception.FlowException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ORDER BY 拼接须统一走 SqlIdentifierSanitizer。
 */
class RegularSqlParseUtilTest {

    @Test
    void buildOrderByClause_quotesSafeColumns() {
        Sort sort = Sort.by(Sort.Order.asc("create_time"), Sort.Order.desc("u.name"));
        String clause = RegularSqlParseUtil.buildOrderByClause(sort);
        assertEquals("`create_time` ASC, `u`.`name` DESC", clause);
    }

    @Test
    void buildOrderByClause_rejectsInjectionPayload() {
        Sort sort = Sort.by(Sort.Order.asc("id; DROP TABLE users"));
        FlowException ex = assertThrows(FlowException.class,
                () -> RegularSqlParseUtil.buildOrderByClause(sort));
        assertEquals("SQL_INJECTION_BLOCK", ex.getErrorCode());
    }

    @Test
    void buildOrderByClause_rejectsQuoteInjection() {
        Sort sort = Sort.by(Sort.Order.desc("name` DESC, evil ASC --"));
        assertThrows(FlowException.class, () -> RegularSqlParseUtil.buildOrderByClause(sort));
    }
}
