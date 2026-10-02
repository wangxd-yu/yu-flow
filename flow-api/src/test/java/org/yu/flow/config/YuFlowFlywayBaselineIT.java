package org.yu.flow.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空库要能靠基线建出表和种子；已经有 flow_* 表的库只登记基线，不能把建表脚本再跑一遍。
 */
@Testcontainers
class YuFlowFlywayBaselineIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("flow")
            .withUsername("root")
            .withPassword("test");

    @Test
    void mysqlEmptyDatabaseThenExistingTables() throws Exception {
        assertBaseline(dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()),
                "classpath:db/flyway/mysql");
    }

    private static void assertBaseline(DataSource dataSource, String location) throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .table(YuFlowFlywayMigrator.HISTORY_TABLE)
                .baselineVersion(YuFlowFlywayMigrator.BASELINE_VERSION)
                .baselineOnMigrate(false)
                .placeholderReplacement(false)
                .load();

        assertEquals(YuFlowFlywayMigrator.Mode.MIGRATE,
                YuFlowFlywayMigrator.apply(flyway, dataSource, true));
        assertTrue(count(dataSource, "select count(*) from flow_sys_role") > 0);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table " + YuFlowFlywayMigrator.HISTORY_TABLE);
        }

        assertEquals(YuFlowFlywayMigrator.Mode.BASELINE_THEN_MIGRATE,
                YuFlowFlywayMigrator.apply(flyway, dataSource, true));
        assertTrue(count(dataSource, "select count(*) from flow_sys_role") > 0);
        assertEquals(1, count(dataSource,
                "select count(*) from " + YuFlowFlywayMigrator.HISTORY_TABLE
                        + " where version = '" + YuFlowFlywayMigrator.BASELINE_VERSION + "'"));
    }

    private static DataSource dataSource(String url, String user, String password) {
        return new DriverManagerDataSource(url, user, password);
    }

    private static int count(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
