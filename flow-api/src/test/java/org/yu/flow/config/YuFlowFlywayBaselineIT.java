package org.yu.flow.config;

import jakarta.persistence.Entity;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空库要能靠基线建出表和种子；已经有 flow_* 表的库只登记基线，不能把建表脚本再跑一遍，
 * 但基线之后的增量要补上老库缺的列。迁移完成后实体映射的表和列必须都在库里。
 */
@Testcontainers
class YuFlowFlywayBaselineIT {

    /** 2026-10-19 增量加列的表；模拟升级前的老库时先删掉这些列 */
    private static final List<String> REQUEST_ID_TABLES = List.of(
            "flow_log_execution", "flow_log_third", "flow_log_task", "flow_log_service", "flow_log_mq_task");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("flow")
            .withUsername("root")
            .withPassword("test");

    /** 默认 scram-sha-256 认证；能连上说明瀚高驱动没有再遮住官方驱动的 SCRAM 实现 */
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("flow")
            .withUsername("flow")
            .withPassword("test");

    @Test
    void mysqlEmptyDatabaseThenExistingTables() throws Exception {
        DataSource dataSource = dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        assertBaseline(dataSource, "classpath:db/flyway/mysql");
        assertEntitiesMatchSchema(dataSource);
    }

    @Test
    void postgresEmptyDatabaseThenExistingTables() throws Exception {
        DataSource dataSource = dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        assertBaseline(dataSource, "classpath:db/flyway/postgresql");
        assertEntitiesMatchSchema(dataSource);
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

        // 空库：基线建表后增量在已有列上再跑一遍，必须幂等
        assertEquals(YuFlowFlywayMigrator.Mode.MIGRATE,
                YuFlowFlywayMigrator.apply(flyway, dataSource, true));
        assertTrue(count(dataSource, "select count(*) from flow_sys_role") > 0);
        assertRequestIdColumns(dataSource);
        assertEquals("aviator,spel,javascript", scriptLanguages(dataSource));

        // 老库：有 flow_* 表、没有迁移记录、也还没有 request_id 列；白名单是旧默认值且已有接口在用 Groovy
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table " + YuFlowFlywayMigrator.HISTORY_TABLE);
            for (String table : REQUEST_ID_TABLES) {
                statement.execute("alter table " + table + " drop column request_id");
            }
            statement.execute("update flow_sys_config set config_value = 'aviator,spel,javascript,groovy'"
                    + " where config_key = 'SCRIPT_ALLOWED_LANGUAGES'");
            statement.execute("insert into flow_api_info (id, dsl_content)"
                    + " values ('it-groovy', '{\"data\":{\"language\":\"Groovy\"}}')");
        }
        for (String table : REQUEST_ID_TABLES) {
            assertFalse(hasColumn(dataSource, table, "request_id"));
        }
        AssertionError drift = assertThrows(AssertionError.class, () -> assertEntitiesMatchSchema(dataSource));
        assertTrue(drift.getMessage().contains("flow_log_execution.request_id"), drift.getMessage());

        assertEquals(YuFlowFlywayMigrator.Mode.BASELINE_THEN_MIGRATE,
                YuFlowFlywayMigrator.apply(flyway, dataSource, true));
        assertTrue(count(dataSource, "select count(*) from flow_sys_role") > 0);
        assertEquals(1, count(dataSource,
                "select count(*) from " + YuFlowFlywayMigrator.HISTORY_TABLE
                        + " where version = '" + YuFlowFlywayMigrator.BASELINE_VERSION + "'"));
        assertRequestIdColumns(dataSource);
        assertEquals("aviator,spel,javascript,groovy", scriptLanguages(dataSource), "在用 Groovy 的库不能被收紧");
        assertEquals(0, count(dataSource,
                "select count(*) from " + YuFlowFlywayMigrator.HISTORY_TABLE + " where success = false"));
    }

    private static String scriptLanguages(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select config_value from flow_sys_config where config_key = 'SCRIPT_ALLOWED_LANGUAGES'")) {
            assertTrue(result.next(), "缺少 SCRIPT_ALLOWED_LANGUAGES 种子");
            return result.getString(1);
        }
    }

    private static void assertRequestIdColumns(DataSource dataSource) throws Exception {
        for (String table : REQUEST_ID_TABLES) {
            assertTrue(hasColumn(dataSource, table, "request_id"), table + ".request_id");
        }
    }

    /**
     * 每个 {@code @Entity} 的表和列在库里都要存在。不用 Hibernate 的 {@code validate}：
     * 状态类 {@code Integer} 字段按 DDL 规范落在 tinyint / smallint 上，它会逐个报类型不符。
     */
    private static void assertEntitiesMatchSchema(DataSource dataSource) throws Exception {
        List<Class<?>> entities = scanEntities();
        assertTrue(entities.size() > 40, "实体扫描数量异常：" + entities.size());

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            entities.forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.getMetadataBuilder()
                    .applyPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                    .applyImplicitNamingStrategy(new SpringImplicitNamingStrategy())
                    .build();

            Map<String, Set<String>> actual = actualColumns(dataSource);
            List<String> missing = new ArrayList<>();
            for (Table table : metadata.collectTableMappings()) {
                String tableName = table.getName().toLowerCase(Locale.ROOT);
                Set<String> columns = actual.get(tableName);
                if (columns == null) {
                    missing.add(tableName + "（整张表）");
                    continue;
                }
                for (Column column : table.getColumns()) {
                    String columnName = column.getName().toLowerCase(Locale.ROOT);
                    if (!columns.contains(columnName)) {
                        missing.add(tableName + "." + columnName);
                    }
                }
            }
            assertTrue(missing.isEmpty(), "实体映射的表或列在库里不存在：" + missing);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static List<Class<?>> scanEntities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> entities = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("org.yu.flow")) {
            entities.add(Class.forName(candidate.getBeanClassName()));
        }
        return entities;
    }

    private static Map<String, Set<String>> actualColumns(DataSource dataSource) throws Exception {
        Map<String, Set<String>> result = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(connection.getCatalog(), connection.getSchema(), "%", "%")) {
                while (rs.next()) {
                    result.computeIfAbsent(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT), k -> new HashSet<>())
                            .add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private static boolean hasColumn(DataSource dataSource, String table, String column) throws Exception {
        Set<String> columns = actualColumns(dataSource).get(table);
        return columns != null && columns.contains(column);
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
