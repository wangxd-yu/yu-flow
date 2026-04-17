package org.yu.flow.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;
import org.yu.flow.config.FlowApiCacheManager;
import org.yu.flow.config.FlowApiMessageListener;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.cache.SysConfigMessageListener;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.yu.flow.module.sysmacro.cache.SysMacroMessageListener;

import javax.annotation.Resource;

/**
 * Yu Flow Redis 消息监听统一管理器（编程式纳管，零 Bean 泄漏）
 *
 * <h3>SDK 防护设计</h3>
 * <p>作为 SDK 嵌入宿主系统时，如果我们通过 {@code @Bean} 暴露 {@link RedisMessageListenerContainer}，
 * 宿主系统自身也可能声明同类型 Bean，导致：</p>
 * <ul>
 *   <li>{@code NoUniqueBeanDefinitionException}（Spring 无法区分同类型的多个容器 Bean）</li>
 *   <li>生命周期崩溃（宿主的 destroy 回调可能误杀我们的容器，或反过来）</li>
 * </ul>
 *
 * <h3>解决方案</h3>
 * <p>本类通过 {@link InitializingBean} 和 {@link DisposableBean} 接口，
 * 在内部以 {@code new} 的方式编程式创建 {@link RedisMessageListenerContainer}，
 * <b>绝不通过 {@code @Bean} 对外暴露</b>。容器的整个生命周期完全由本管理器掌控：</p>
 * <ol>
 *   <li>{@link #afterPropertiesSet()}：创建容器 → 注册所有监听器 → 手动初始化并启动</li>
 *   <li>{@link #destroy()}：优雅停止容器 → 释放线程池和连接资源</li>
 * </ol>
 *
 * <h3>连接工厂策略</h3>
 * <p>通过 {@code @Resource} 直接注入 Spring Boot 自动配置提供的 {@link RedisConnectionFactory}，
 * <b>绝不自己创建物理连接</b>，完全复用宿主环境的 Redis 连接池。</p>
 *
 * <h3>当前纳管的监听器</h3>
 * <table>
 *   <tr><th>监听器</th><th>频道 (Topic)</th><th>职责</th></tr>
 *   <tr><td>{@link FlowApiMessageListener}</td><td>{@code flow:api:cache:refresh:topic}</td><td>API 路由缓存刷新</td></tr>
 *   <tr><td>{@link SysConfigMessageListener}</td><td>{@code flow:sys:config:refresh:topic}</td><td>系统配置缓存刷新</td></tr>
 *   <tr><td>{@link SysMacroMessageListener}</td><td>{@code flow:sys:macro:refresh:topic}</td><td>系统宏定义缓存刷新</td></tr>
 * </table>
 *
 * yu-flow
 */
@Slf4j
@Component
public class YuFlowRedisListenerManager implements InitializingBean, DisposableBean {

    // ===================== SDK 核心：复用宿主连接，绝不自建 =====================

    /**
     * 注入 Spring Boot 自动配置（或宿主系统手动配置）的 RedisConnectionFactory。
     * <p>SDK 绝不能 new LettuceConnectionFactory()，否则会产生独立于宿主的连接池，
     * 导致连接泄漏和生命周期紊乱。</p>
     */
    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    // ===================== 所有 Yu Flow 内部监听器 =====================

    @Resource
    private FlowApiMessageListener flowApiMessageListener;

    @Resource
    private SysConfigMessageListener sysConfigMessageListener;

    @Resource
    private SysMacroMessageListener sysMacroMessageListener;

    // ===================== 内部持有，绝不暴露为 Bean =====================

    /**
     * 编程式创建的 Redis 消息监听容器。
     * <p><b>注意：此字段不是 @Bean，宿主系统完全看不到它。</b></p>
     */
    private RedisMessageListenerContainer container;

    // ===================== 生命周期管理 =====================

    /**
     * 容器初始化完成后，编程式创建并启动 Redis 监听容器。
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>new 一个 RedisMessageListenerContainer（不通过 @Bean 注册）</li>
     *   <li>设置宿主提供的 RedisConnectionFactory</li>
     *   <li>逐个添加所有 Yu Flow 内部的 MessageListener 及其对应 Topic</li>
     *   <li>手动调用 afterPropertiesSet() 完成容器内部初始化</li>
     *   <li>手动调用 start() 启动订阅线程</li>
     * </ol>
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("[YuFlowRedisListenerManager] 开始编程式初始化 Redis 消息监听容器...");

        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);

        // ---- 注册所有 Yu Flow 内部监听器 ----

        // 1. API 路由缓存刷新监听
        container.addMessageListener(
                new MessageListenerAdapter(flowApiMessageListener, "onMessage"),
                new ChannelTopic(FlowApiCacheManager.REFRESH_TOPIC)
        );

        // 2. 系统配置缓存刷新监听
        container.addMessageListener(
                new MessageListenerAdapter(sysConfigMessageListener, "onMessage"),
                new ChannelTopic(SysConfigCacheManager.REFRESH_TOPIC)
        );

        // 3. 系统宏定义缓存刷新监听
        container.addMessageListener(
                new MessageListenerAdapter(sysMacroMessageListener, "onMessage"),
                new ChannelTopic(SysMacroCacheManager.REFRESH_TOPIC)
        );

        // ---- 手动驱动 Spring Lifecycle ----
        container.afterPropertiesSet();
        container.start();

        log.info("[YuFlowRedisListenerManager] Redis 消息监听容器启动完成。已注册 3 个监听器，" +
                "订阅频道: [{}, {}, {}]",
                FlowApiCacheManager.REFRESH_TOPIC,
                SysConfigCacheManager.REFRESH_TOPIC,
                SysMacroCacheManager.REFRESH_TOPIC);
    }

    /**
     * Spring 容器销毁时，优雅关闭 Redis 监听容器。
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>调用 stop() 停止订阅线程</li>
     *   <li>调用 destroy() 释放内部资源（线程池、连接等）</li>
     * </ol>
     *
     * <p>如果关闭过程中发生异常，仅打印 WARN 日志，不影响宿主系统的正常关闭流程。</p>
     */
    @Override
    public void destroy() {
        if (container != null) {
            log.info("[YuFlowRedisListenerManager] 正在优雅关闭 Redis 消息监听容器...");
            try {
                container.stop();
                container.destroy();
                log.info("[YuFlowRedisListenerManager] Redis 消息监听容器已安全关闭。");
            } catch (Exception e) {
                log.warn("[YuFlowRedisListenerManager] 关闭 Redis 消息监听容器时发生异常（不影响宿主系统）。", e);
            }
        }
    }
}
