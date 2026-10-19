package org.yu.flow.engine.evaluator.spel;

import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.yu.flow.util.FlowSystemParamsUtil;
import org.yu.flow.util.SnowIdGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeTypeLocatorTest {

    private final SafeTypeLocator locator = new SafeTypeLocator();

    @Test
    void movedTypesResolveByOldAndNewName() {
        assertEquals(SnowIdGenerator.class, locator.findType("org.yu.flow.util.SnowIdGenerator"));
        assertEquals(SnowIdGenerator.class, locator.findType("org.yu.flow.auto.util.SnowIdGenerator"));
        assertEquals(FlowSystemParamsUtil.class, locator.findType("org.yu.flow.auto.util.FlowSystemParamsUtil"));
    }

    @Test
    void builtInSnowflakeMacroWithOldClassNameStillEvaluates() {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setTypeLocator(locator);
        Object id = new SpelExpressionParser()
                .parseExpression("T(org.yu.flow.auto.util.SnowIdGenerator).getId()")
                .getValue(ctx);
        assertFalse(String.valueOf(id).isBlank());
    }

    @Test
    void otherClassesUnderOldPackageStayBlocked() {
        assertThrows(EvaluationException.class, () -> locator.findType("org.yu.flow.auto.util.JwtTokenUtil"));
        assertThrows(EvaluationException.class, () -> locator.findType("org.yu.flow.util.JwtTokenUtil"));
        assertThrows(EvaluationException.class, () -> locator.findType("java.lang.Runtime"));
    }
}
