package org.yu.flow.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 流程引擎自研应用启动类
 *
 * 注意：为了避免自动配置类（AutoConfiguration）与应用自身的包扫描路径冲突，
 * 开发联调用的启动类应置于独立的子包中（如 org.yu.flow.app）。
 * 这样它既能通过自动配置加载库功能，又不会因为重叠扫描导致 Bean 重复注册。
 *
 * MQ（RabbitMQ / Kafka）客户端由 MqProviderRegistry 按「MQ 连接配置」动态构建，
 * 排除全局自动装配以避免无 Broker 环境下产生指向 localhost 的默认连接工厂。
 */
@EnableAsync
@SpringBootApplication(exclude = {RabbitAutoConfiguration.class, KafkaAutoConfiguration.class})
public class FlowApp {
    public static void main(String[] args) {
        SpringApplication.run(FlowApp.class, args);
    }
}
