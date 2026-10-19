package org.yu.flow.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.yu.flow.module.release.support.ReleaseAssetResolver;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 整个应用在真实 MySQL + Redis 上能启动：Flyway 建表、全部 Bean 装配（无循环依赖、无重复 Bean）、
 * 版本单每种资产类型都有实现。只连容器里的临时库。
 */
@Testcontainers
@SpringBootTest(classes = FlowApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class FlowApplicationStartupIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("flow")
            .withUsername("root")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> withParams(MYSQL.getJdbcUrl()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("yu.flow.password", () -> "Startup-IT-Admin-9x!");
        registry.add("yu.flow.security.jwt-secret-key", () -> "startup-it-jwt-secret-0123456789abcdef");
        registry.add("yu.flow.security.aes-secret-key", () -> "startupIT16bytes");
    }

    private static String withParams(String url) {
        String params = "serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";
        return url + (url.contains("?") ? "&" : "?") + params;
    }

    @Autowired
    private ReleaseAssetResolver assetResolver;

    @Test
    void contextStartsWithEveryReleaseAssetType() {
        for (String type : ReleaseAssetResolver.TYPES) {
            assertNotNull(assetResolver.typeOf(type), type);
        }
    }
}
