package org.yu.flow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 把 Flyway 指到当前方言的脚本目录，并按库里有没有 {@code flow_*} 表决定是建表还是只登记基线。
 *
 * <p>{@code db/migration} 与 {@code db/migration-pg} 是历史上手工执行的增量，不在这次的扫描路径里，
 * 避免空库从中间态 ALTER 跑起。新的增量放到 {@code db/flyway/mysql} 与 {@code db/flyway/postgresql}。</p>
 */
@Configuration
public class YuFlowFlywayConfig {

    @Bean
    FlywayConfigurationCustomizer yuFlowFlywayCustomizer(@Value("${spring.datasource.url:}") String jdbcUrl) {
        return configuration -> {
            String location = YuFlowFlywayMigrator.isPostgres(jdbcUrl)
                    ? "classpath:db/flyway/postgresql"
                    : "classpath:db/flyway/mysql";
            configuration.locations(location);
            configuration.table(YuFlowFlywayMigrator.HISTORY_TABLE);
            configuration.baselineVersion(YuFlowFlywayMigrator.BASELINE_VERSION);
            configuration.baselineOnMigrate(false);
            configuration.placeholderReplacement(false);
        };
    }

    @Bean
    FlywayMigrationStrategy yuFlowFlywayStrategy(DataSource dataSource, YuFlowProperties properties) {
        return flyway -> {
            boolean autoMigrate = properties.getDb() == null || properties.getDb().isAutoMigrate();
            try {
                YuFlowFlywayMigrator.apply(flyway, dataSource, autoMigrate);
            } catch (SQLException e) {
                throw new IllegalStateException("元数据库迁移失败", e);
            }
        };
    }
}
