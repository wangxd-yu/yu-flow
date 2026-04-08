package org.yu.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yu.flow.auto.util.FlowSystemParamsUtil;
import org.yu.flow.exception.FlowException;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowSystemParamsUtilTest {

    @Mock
    private Environment environment;

    @InjectMocks
    private FlowSystemParamsUtil flowSystemParamsUtil;

    @Test
    void getParams_shouldReturnEnvProperty_whenTypeIsENV() {
        // 准备
        String input = "ENV.spring.datasource.username";
        String expectedValue = "root";
        when(environment.getProperty("spring.datasource.username")).thenReturn(expectedValue);

        // 执行
        Object result = FlowSystemParamsUtil.getParams(input);

        // 验证
        assertEquals(expectedValue, result);
        verify(environment).getProperty("spring.datasource.username");
    }

    @Test
    void getParams_shouldReturnServeParam_whenTypeIsSERVE() {
        // 准备
        String input = "SERVE.mapKey.property";
        String mapKey = "mapKey";
        String expectedValue = "testValue";

        TestObject testObject = new TestObject(expectedValue);

        // 执行
        Object result = FlowSystemParamsUtil.getParams(input);

        // 验证
        assertEquals(expectedValue, result);
    }

    @Test
    void getParams_shouldThrowFlowException_whenTypeIsInvalid() {
        // 准备
        String input = "INVALID.test.key";

        // 执行 & 验证
        FlowException exception = assertThrows(FlowException.class, () -> FlowSystemParamsUtil.getParams(input));
        assertEquals("[PARAM_NOT_FOUND] 参数异常，找不到系统参数：" + input, exception.getMessage());
    }

    @Test
    void getParams_shouldThrowFlowException_whenServeParamNotFound() {
        // 准备
        String input = "SERVE.nonExistingKey.property";

        // 执行 & 验证
        FlowException exception = assertThrows(FlowException.class, () -> FlowSystemParamsUtil.getParams(input));

        assertTrue(exception.getMessage().contains("参数异常，找不到系统参数："));
    }



   /* @Test
    void getProperty_shouldReturnNestedProperty() {
        // 准备
        TestObject testObject = new TestObject("testValue");
        String path = "property";

        // 执行
        Object result = SystemParamsUtil.getProperty(testObject, path);

        // 验证
        assertEquals("testValue", result);
    }*/

    // 测试用辅助类
    static class TestObject {
        private final String property;

        public TestObject(String property) {
            this.property = property;
        }

        public String getProperty() {
            return property;
        }
    }
}
