package org.yu.flow.module.release.support;

import org.junit.jupiter.api.Test;
import org.yu.flow.module.model.domain.FlowModelInfoDO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTableInspectorTest {

    private static FlowModelInfoDO model() {
        return FlowModelInfoDO.builder()
                .name("订单")
                .tableName("t_order")
                .fieldsSchema("[{\"fieldId\":\"order_no\",\"fieldName\":\"订单号\",\"dbType\":\"VARCHAR\",\"length\":32,\"isRequired\":true},"
                        + "{\"fieldId\":\"created_at\",\"fieldName\":\"创建时间\",\"dbType\":\"DATETIME\"},"
                        + "{\"fieldId\":\"memo\",\"fieldName\":\"备注'引号\",\"dbType\":\"TEXT\"}]")
                .build();
    }

    @Test
    void mysqlDdl() {
        String ddl = ModelTableInspector.generateDdl(model(), "mysql");
        assertTrue(ddl.contains("CREATE TABLE `t_order`"));
        assertTrue(ddl.contains("`order_no` VARCHAR(32) NOT NULL COMMENT '订单号'"));
        assertTrue(ddl.contains("`created_at` DATETIME"));
        assertTrue(ddl.contains("COMMENT '备注''引号'"));
        assertTrue(ddl.contains("ENGINE=InnoDB"));
    }

    @Test
    void postgresDdlMapsTypesAndUsesCommentOn() {
        String ddl = ModelTableInspector.generateDdl(model(), "postgresql");
        assertTrue(ddl.contains("CREATE TABLE \"t_order\""));
        assertTrue(ddl.contains("\"created_at\" TIMESTAMP"));
        assertTrue(ddl.contains("COMMENT ON COLUMN \"t_order\".\"order_no\" IS '订单号';"));
    }

    @Test
    void untrustedModelCannotSmuggleStatements() {
        FlowModelInfoDO evil = FlowModelInfoDO.builder()
                .name("订单\nDROP TABLE users;")
                .tableName("t_order")
                .fieldsSchema("[{\"fieldId\":\"a\",\"fieldName\":\"x\\\\' , evil\",\"dbType\":\"VARCHAR(10)); DROP TABLE users; --\"},"
                        + "{\"fieldId\":\"amount\",\"dbType\":\"DECIMAL(10, 2)\"}]")
                .build();

        String ddl = ModelTableInspector.generateDdl(evil, "mysql");

        assertFalse(ddl.lines().anyMatch(l -> l.startsWith("DROP")));
        assertFalse(ddl.contains("DROP TABLE users; --"));
        assertTrue(ddl.contains("`a` VARCHAR(255)"));
        assertTrue(ddl.contains("类型无法识别"));
        assertTrue(ddl.contains("COMMENT 'x\\\\'' , evil'"));
        assertTrue(ddl.contains("`amount` DECIMAL(10, 2)"));
    }

    @Test
    void tableNameWildcardsAreEscaped() {
        assertEquals("t\\_order\\%", ModelTableInspector.likeLiteral("t_order%", "\\"));
        assertEquals("t_order", ModelTableInspector.likeLiteral("t_order", ""));
    }
}
