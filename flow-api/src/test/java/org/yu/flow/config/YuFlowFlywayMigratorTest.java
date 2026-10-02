package org.yu.flow.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YuFlowFlywayMigratorTest {

    @Test
    void highgoAndDisabledSwitchSkip() {
        assertEquals(YuFlowFlywayMigrator.Mode.SKIP,
                YuFlowFlywayMigrator.mode(true, "jdbc:highgo://db/flow", false, false));
        assertEquals(YuFlowFlywayMigrator.Mode.SKIP,
                YuFlowFlywayMigrator.mode(false, "jdbc:mysql://db/flow", false, false));
    }

    @Test
    void existingFlowTablesBaselineOnce() {
        assertEquals(YuFlowFlywayMigrator.Mode.BASELINE_THEN_MIGRATE,
                YuFlowFlywayMigrator.mode(true, "jdbc:mysql://db/flow", true, false));
        assertEquals(YuFlowFlywayMigrator.Mode.MIGRATE,
                YuFlowFlywayMigrator.mode(true, "jdbc:postgresql://db/flow", true, true));
    }

    @Test
    void emptySchemaMigratesEvenWhenTheHostDatabaseHasOtherTables() {
        assertEquals(YuFlowFlywayMigrator.Mode.MIGRATE,
                YuFlowFlywayMigrator.mode(true, "jdbc:mysql://db/flow", false, false));
    }

    @Test
    void postgresDoesNotIncludeHighgo() {
        assertTrue(YuFlowFlywayMigrator.isPostgres("jdbc:postgresql://db/flow"));
        assertFalse(YuFlowFlywayMigrator.isPostgres("jdbc:highgo://db/flow"));
        assertFalse(YuFlowFlywayMigrator.isPostgres("jdbc:mysql://db/flow"));
        assertTrue(YuFlowFlywayMigrator.isHighgo("jdbc:highgo://db/flow"));
    }
}
