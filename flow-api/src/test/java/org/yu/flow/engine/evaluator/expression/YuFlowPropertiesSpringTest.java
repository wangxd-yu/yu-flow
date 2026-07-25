package org.yu.flow.engine.evaluator.expression;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.yu.flow.config.YuFlowProperties;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * YuFlowProperties 配置绑定 Spring 切片测试。
 */
@SpringBootTest(classes = YuFlowPropertiesSpringTest.Config.class)
class YuFlowPropertiesSpringTest {

    @SpringBootConfiguration
    @EnableConfigurationProperties(YuFlowProperties.class)
    static class Config {
        @Bean
        public ExpressionEngine expressionEngine(YuFlowProperties properties) {
            String type = properties.getEngine().getExpressionEngine();
            if ("spel".equalsIgnoreCase(type)) {
                return new SafeSpelExpressionEngine();
            }
            return new SimpleExpressionEngine();
        }
    }

    @Resource
    private ExpressionEngine expressionEngine;

    @Test
    void defaultEngine_isSimple() {
        assertEquals(SimpleExpressionEngine.class, expressionEngine.getClass());
    }
}

@SpringBootTest(classes = YuFlowPropertiesSpringTest.Config.class, properties = "yu.flow.engine.expression-engine=spel")
class SpelExpressionEngineSpringTest {

    @Resource
    private ExpressionEngine expressionEngine;

    @Test
    void spelEngine_isSafeSpel() {
        assertEquals(SafeSpelExpressionEngine.class, expressionEngine.getClass());
    }
}
