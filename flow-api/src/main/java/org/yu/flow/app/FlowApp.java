package org.yu.flow.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 流程引擎自研应用启动类
 *
 * 注意：为了避免自动配置类（AutoConfiguration）与应用自身的包扫描路径冲突，
 * 开发联调用的启动类应置于独立的子包中（如 org.yu.flow.app）。
 * 这样它既能通过自动配置加载库功能，又不会因为重叠扫描导致 Bean 重复注册。
 */
@SpringBootApplication
public class FlowApp {
    public static void main(String[] args) {
        SpringApplication.run(FlowApp.class, args);
    }
}
