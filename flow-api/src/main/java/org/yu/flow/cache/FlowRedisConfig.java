package org.yu.flow.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Yu Flow SDK 专属 Redis 配置（安全隔离，零侵入宿主）
 *
 * <h3>SDK 零侵入设计原则</h3>
 * <ol>
 *   <li><b>绝不创建物理连接</b>：
 *       不声明 {@code LettuceConnectionFactory} / {@code JedisConnectionFactory} 等连接工厂 Bean。
 *       SDK 强依赖宿主环境（Spring Boot 自动配置或宿主手动配置）提供的 {@link RedisConnectionFactory}，
 *       通过方法参数注入即可拿到宿主的连接工厂，彻底避免连接池重复创建和生命周期冲突。</li>
 *
 *   <li><b>Bean 名称显式隔离</b>：
 *       所有 {@code @Bean} 均通过 {@code name} 属性指定唯一标识（如 {@code yuFlowRedisTemplate}），
 *       与宿主系统可能存在的同类型 Bean（如 {@code redisTemplate}）完全隔离：
 *       <ul>
 *         <li>不会触发 {@code NoUniqueBeanDefinitionException}</li>
 *         <li>SDK 内部通过 {@code @Resource(name = "yuFlowRedisTemplate")} 精确引用</li>
 *         <li>宿主系统的 {@code @Autowired RedisTemplate} 不受影响</li>
 *       </ul></li>
 *
 *   <li><b>不使用条件装配</b>：
 *       已移除 {@code @Conditional(NoExistingRedisConfigCondition.class)}。
 *       这类「靠猜」的机制在复杂宿主环境下不可靠（Bean 注册顺序不确定、ConfigurationClassPostProcessor 阶段差异等）。
 *       改为通过唯一 Bean 名称实现安全共存，无论宿主是否已有 Redis 配置均不冲突。</li>
 * </ol>
 *
 * <h3>产出的 Bean</h3>
 * <table>
 *   <tr><th>Bean 名称</th><th>类型</th><th>职责</th></tr>
 *   <tr><td>{@code yuFlowRedisTemplate}</td><td>{@link RedisTemplate}</td><td>SDK 专属 RedisTemplate（Jackson 序列化 + Java8 时间支持）</td></tr>
 * </table>
 *
 * yu-flow
 */
@Configuration
public class FlowRedisConfig {

    /**
     * Yu Flow SDK 专属 RedisTemplate（自定义 Jackson 序列化）
     *
     * <p>使用显式 Bean 名称 {@code yuFlowRedisTemplate}，与宿主的 {@code redisTemplate} 完全隔离。
     * SDK 内部所有需要操作 Redis 的组件均通过 {@code @Resource(name = "yuFlowRedisTemplate")} 引用，
     * 绝不干扰宿主的默认 RedisTemplate。</p>
     *
     * <h4>序列化配置</h4>
     * <ul>
     *   <li>Key / HashKey：{@link StringRedisSerializer}（人类可读，便于 Redis CLI 排查）</li>
     *   <li>Value / HashValue：{@link GenericJackson2JsonRedisSerializer}（JSON 格式，已注册 Java8 时间模块）</li>
     * </ul>
     *
     * @param factory 宿主环境提供的 RedisConnectionFactory（SDK 不创建、不管理）
     */
    @Bean(name = "yuFlowRedisTemplate")
    public RedisTemplate<String, Object> yuFlowRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // ★ 核心：消费宿主的连接工厂，绝不自建
        template.setConnectionFactory(factory);

        // 1. 初始化 JSON 序列化器，并注册 Java8 时间模块
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(
                new ObjectMapper()
                        .registerModule(new JavaTimeModule()) // 关键：注册 Java8 时间模块
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // 禁用时间戳序列化（改为字符串）
        );

        // 2. Key 序列化（String 类型）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 3. Value 序列化（JSON 格式，已支持 LocalDateTime）
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
