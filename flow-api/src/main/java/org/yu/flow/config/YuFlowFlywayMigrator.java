package org.yu.flow.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * MySQL / PostgreSQL 的启动迁移策略。
 *
 * <p>空库（或宿主库里还没有 {@code flow_*} 表）执行 {@code db/flyway} 下的基线脚本。
 * 已经有 {@code flow_*} 表但没有 {@code flow_schema_history} 的库只登记基线，不重放历史脚本。
 * 瀚高不走 Flyway，继续手工执行 {@code sql-pg}。</p>
 */
public final class YuFlowFlywayMigrator {

    private static final Logger log = LoggerFactory.getLogger(YuFlowFlywayMigrator.class);

    public static final String HISTORY_TABLE = "flow_schema_history";

    /** 与 V2026_10_02_00__baseline.sql 的版本一致；小于等于该版本的脚本在 baseline 之后不再执行 */
    public static final String BASELINE_VERSION = "2026.10.02.00";

    public enum Mode {
        SKIP,
        MIGRATE,
        BASELINE_THEN_MIGRATE
    }

    private YuFlowFlywayMigrator() {
    }

    public static Mode mode(boolean autoMigrate, String jdbcUrl, boolean flowTables, boolean history) {
        if (!autoMigrate || isHighgo(jdbcUrl)) {
            return Mode.SKIP;
        }
        if (flowTables && !history) {
            return Mode.BASELINE_THEN_MIGRATE;
        }
        return Mode.MIGRATE;
    }

    public static boolean isHighgo(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).contains("highgo");
    }

    public static boolean isPostgres(String jdbcUrl) {
        if (jdbcUrl == null || isHighgo(jdbcUrl)) {
            return false;
        }
        return jdbcUrl.toLowerCase(Locale.ROOT).contains("postgresql");
    }

    public static Mode apply(Flyway flyway, DataSource dataSource, boolean autoMigrate) throws SQLException {
        String url;
        boolean flowTables;
        boolean history;
        try (Connection connection = dataSource.getConnection()) {
            url = connection.getMetaData().getURL();
            flowTables = hasFlowTables(connection);
            history = hasHistory(connection);
        }
        Mode decided = mode(autoMigrate, url, flowTables, history);
        switch (decided) {
            case SKIP -> log.info("[Flyway] 跳过自动迁移（autoMigrate={}，瀚高={}）。瀚高请手工执行 sql-pg",
                    autoMigrate, isHighgo(url));
            case BASELINE_THEN_MIGRATE -> {
                log.info("[Flyway] 库里已有 flow_* 表，登记基线 {} 后只跑更新的脚本", BASELINE_VERSION);
                flyway.baseline();
                flyway.migrate();
            }
            case MIGRATE -> {
                log.info("[Flyway] 执行迁移");
                flyway.migrate();
            }
        }
        return decided;
    }

    static boolean hasFlowTables(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String product = meta.getDatabaseProductName() == null
                ? "" : meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
        String catalog = product.contains("mysql") ? connection.getCatalog() : null;
        String schema = product.contains("mysql") ? null : schemaOrPublic(connection);
        try (ResultSet tables = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (name == null) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith("flow_") && !HISTORY_TABLE.equals(lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasHistory(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String product = meta.getDatabaseProductName() == null
                ? "" : meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
        String catalog = product.contains("mysql") ? connection.getCatalog() : null;
        String schema = product.contains("mysql") ? null : schemaOrPublic(connection);
        try (ResultSet tables = meta.getTables(catalog, schema, HISTORY_TABLE, new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (name != null && HISTORY_TABLE.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String schemaOrPublic(Connection connection) throws SQLException {
        String schema = connection.getSchema();
        return schema == null || schema.isBlank() ? "public" : schema;
    }
}
