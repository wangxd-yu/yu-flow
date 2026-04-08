package org.yu.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.engine.evaluator.FlowEngine;

import java.util.*;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.yu.flow.auto.dto.SqlAndParams;
import org.yu.flow.engine.service.SqlExecutorService;
import org.springframework.data.domain.Pageable;


/**
 * FlowEngine V3 完整场景测试
 * 适配架构：Nodes + Edges + Ports + Data Inputs
 * 表达式引擎：AviatorScript (单引号字符串，无${}变量)
 */
@DisplayName("FlowEngine V3 (AviatorScript) 完整测试")
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class FlowEngineTest {

    private FlowEngine engine;
    private TestService testService;

    @BeforeEach
    void setUp() {
        engine = new FlowEngine();
        testService = new TestService();
        engine.registerService("testService", testService);
    }

    public static class TestService {
        public final List<String> logs = new ArrayList<>();
        public void log(String msg) { synchronized(logs) { logs.add(msg); } }
        public String greet(String name) { return "Hello, " + name; }
    }

    // ==========================================
    // 基础功能测试
    // ==========================================

    @Test
    @DisplayName("01、测试空流程")
    void testEmptyFlow() throws JsonProcessingException {
        String flowJson = "{\"id\":\"empty\",\"nodes\":[],\"edges\":[]}";
        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("02、测试 Evaluate 节点 - 纯表达式计算")
    void testEvaluateNode() throws JsonProcessingException {
        // Aviator: 字符串使用单引号
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"node_calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], \n" +
                "      \"data\": { \"expression\": \"'Hello ' + 'World'\" } },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \n" +
                "      \"data\": { \"responseBody\": \"${node_calc.result}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_calc\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertEquals("Hello World", result.getData());
    }

    // ==========================================
    // 条件分支测试 (Aviator 逻辑)
    // ==========================================

    @Test
    @DisplayName("03、测试 If 节点 - True 分支")
    void testIfStepTrueBranch() throws JsonProcessingException {
        String flowJson = getIfFlowJson();
        Map<String, Object> args = new HashMap<>();
        args.put("age", 20);

        ExecutionResult result = engine.execute(flowJson, args);
        assertEquals("adult", result.getData());
    }

    @Test
    @DisplayName("04、测试 If 节点 - False 分支")
    void testIfStepFalseBranch() throws JsonProcessingException {
        String flowJson = getIfFlowJson();
        Map<String, Object> args = new HashMap<>();
        args.put("age", 15);

        ExecutionResult result = engine.execute(flowJson, args);
        assertEquals("minor", result.getData());
    }

    private String getIfFlowJson() {
        // Aviator: 变量直接引用，无需 ${}
        return "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"if_node\", \"type\": \"if\", \n" +
                "      \"ports\": [{\"id\":\"in\"}, {\"id\":\"true\"}, {\"id\":\"false\"}],\n" +
                "      \"data\": { \n" +
                "        \"inputs\": { \"age\": {\"extractPath\": \"$.start.args.age\"} },\n" +
                "        \"condition\": \"age >= 18\" \n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end_adult\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"adult\"} },\n" +
                "    { \"id\": \"end_minor\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"minor\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"if_node\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"if_node\", \"port\": \"true\"}, \"target\": {\"cell\": \"end_adult\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"if_node\", \"port\": \"false\"}, \"target\": {\"cell\": \"end_minor\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";
    }

    // ==========================================
    // Switch 测试
    // ==========================================

    @Test
    @DisplayName("05、测试 Switch 节点 - 字符串匹配")
    void testSwitchStepAdminCase() throws JsonProcessingException {
        // Aviator: 字符串比较直接使用 == (Aviator 重载了操作符)
        String flowJson = getSwitchFlowJson();
        Map<String, Object> args = new HashMap<>();
        args.put("role", "ADMIN");
        assertEquals("Admin Access", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    @Test
    @DisplayName("06、测试 Switch 节点 - Default 分支")
    void testSwitchStepDefaultCase() throws JsonProcessingException {
        String flowJson = getSwitchFlowJson();
        Map<String, Object> args = new HashMap<>();
        args.put("role", "UNKNOWN");
        assertEquals("Unknown Role", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    private String getSwitchFlowJson() {
        return "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"sw_node\", \"type\": \"switch\", \n" +
                "      \"ports\": [{\"id\":\"in\"}, {\"id\":\"case_ADMIN\"}, {\"id\":\"case_USER\"}, {\"id\":\"default\"}],\n" +
                "      \"data\": { \n" +
                "        \"inputs\": { \"r\": {\"extractPath\": \"$.start.args.role\"} },\n" +
                "        \"expression\": \"r\" \n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end_1\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"Admin Access\"} },\n" +
                "    { \"id\": \"end_2\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"User Access\"} },\n" +
                "    { \"id\": \"end_3\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"Unknown Role\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"sw_node\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"sw_node\", \"port\": \"case_ADMIN\"}, \"target\": {\"cell\": \"end_1\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"sw_node\", \"port\": \"case_USER\"}, \"target\": {\"cell\": \"end_2\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"sw_node\", \"port\": \"default\"}, \"target\": {\"cell\": \"end_3\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";
    }

    @Test
    @DisplayName("07、测试 Switch 节点 - 数值匹配")
    void testSwitchWithNumbers() throws JsonProcessingException {
        // Aviator 对数字类型处理较好，Input 提取为 Number，Case 也是 Number
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"sw\", \"type\": \"switch\", \n" +
                "      \"ports\": [{\"id\":\"in\"}, {\"id\":\"case_200\"}, {\"id\":\"case_404\"}, {\"id\":\"default\"}],\n" +
                "      \"data\": { \n" +
                "        \"inputs\": { \"code\": {\"extractPath\": \"$.start.args.code\"} },\n" +
                "        \"expression\": \"code\" \n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end_ok\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"OK\"} },\n" +
                "    { \"id\": \"end_err\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"ERR\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"sw\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"sw\", \"port\": \"case_200\"}, \"target\": {\"cell\": \"end_ok\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"sw\", \"port\": \"default\"}, \"target\": {\"cell\": \"end_err\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("code", 200);
        assertEquals("OK", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    // ==========================================
    // 服务调用 (07)
    // ==========================================

    @Test
    @DisplayName("12、测试 ServiceCall 节点")
    void testServiceCallStep() throws JsonProcessingException {
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"call_svc\", \"type\": \"serviceCall\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"out\"}],\n" +
                "      \"data\": {\n" +
                "        \"service\": \"testService\", \"method\": \"greet\",\n" +
                "        \"inputs\": { \"n\": {\"extractPath\": \"$.start.args.name\"} },\n" +
                "        \"args\": [ \"n\" ]\n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}],\n" +
                "      \"data\": { \"responseBody\": \"${call_svc.result}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"call_svc\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"call_svc\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("name", "Aviator");
        assertEquals("Hello, Aviator", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    // ==========================================
    // 复杂逻辑组合 (08-09)
    // ==========================================

    @Test
    @DisplayName("09、测试复杂流程 - If + Switch 组合")
    void testComplexFlowWithIfAndSwitch() throws JsonProcessingException {
        // Start -> If(age>=18) -> Switch(role) -> End
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"if_check\", \"type\": \"if\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"true\"}, {\"id\":\"false\"}],\n" +
                "      \"data\": { \"inputs\":{\"a\":\"$.start.args.age\"}, \"condition\":\"a >= 18\" } },\n" +
                "    { \"id\": \"sw_role\", \"type\": \"switch\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"case_admin\"}, {\"id\":\"default\"}],\n" +
                "      \"data\": { \"inputs\":{\"r\":\"$.start.args.role\"}, \"expression\":\"r\" } },\n" +
                "    { \"id\": \"end_final\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\":{\"responseBody\":\"Adult Admin\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"if_check\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"if_check\", \"port\": \"true\"}, \"target\": {\"cell\": \"sw_role\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"sw_role\", \"port\": \"case_admin\"}, \"target\": {\"cell\": \"end_final\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("age", 25);
        args.put("role", "admin");
        assertEquals("Adult Admin", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    @Test
    @DisplayName("08、测试嵌套 If 节点")
    void testNestedIfSteps() throws JsonProcessingException {
        // Aviator condition: s >= 60
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"if_pass\", \"type\": \"if\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"true\"}],\n" +
                "      \"data\": { \"inputs\":{\"s\":\"$.start.args.score\"}, \"condition\":\"s >= 60\" } },\n" +
                "    { \"id\": \"if_good\", \"type\": \"if\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"true\"}],\n" +
                "      \"data\": { \"inputs\":{\"s\":\"$.start.args.score\"}, \"condition\":\"s >= 80\" } },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\":{\"responseBody\":\"Excellent\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"if_pass\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"if_pass\", \"port\": \"true\"}, \"target\": {\"cell\": \"if_good\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"if_good\", \"port\": \"true\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("score", 90);
        assertEquals("Excellent", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    // ==========================================
    // 参数与上下文 (10-11)
    // ==========================================

    @Test
    @DisplayName("10、测试输入参数传递 (Start Args)")
    void testInputParameters() throws JsonProcessingException {
        // 验证 End 节点通过模板直接引用 Start 参数
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}],\n" +
                "      \"data\": { \"responseBody\": \"${start.args.name}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("name", "DirectUser");
        assertEquals("DirectUser", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    @Test
    @DisplayName("11、测试表达式中的字符串拼接")
    void testFlowLevelArgs() throws JsonProcessingException {
        // Aviator: 使用 + 拼接字符串
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],\n" +
                "      \"data\": { \n" +
                "        \"inputs\": { \"g\":{\"extractPath\":\"$.start.args.greeting\"}, \"n\":{\"extractPath\":\"$.start.args.name\"} },\n" +
                "        \"expression\": \"g + ', ' + n\" \n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": { \"responseBody\": \"${calc.result}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("greeting", "Welcome");
        args.put("name", "User");
        assertEquals("Welcome, User", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    // ==========================================
    // 高级执行模式 (13-15)
    // ==========================================

    @Test
    @DisplayName("13、测试并行执行 (Implicit Parallel)")
    void testParallelNextWithService() throws JsonProcessingException {
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"branch_a\", \"type\": \"serviceCall\", \"ports\": [{\"id\":\"in\"}], \n" +
                "      \"data\": { \"service\":\"testService\", \"method\":\"log\", \"args\":[\"'A_DONE'\"] } },\n" +
                "    { \"id\": \"branch_b\", \"type\": \"serviceCall\", \"ports\": [{\"id\":\"in\"}], \n" +
                "      \"data\": { \"service\":\"testService\", \"method\":\"log\", \"args\":[\"'B_DONE'\"] } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"branch_a\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"branch_b\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        engine.execute(flowJson, new HashMap<>());
        assertTrue(testService.logs.contains("A_DONE"));
        assertTrue(testService.logs.contains("B_DONE"));
    }

    @Test
    @DisplayName("14、测试多父节点汇聚")
    void testMultiParentConvergence() throws JsonProcessingException {
        // Aviator expression: bVal + '_' + cVal
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"node_b\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], \"data\": {\"expression\":\"'B'\"} },\n" +
                "    { \"id\": \"node_c\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], \"data\": {\"expression\":\"'C'\"} },\n" +
                "    { \"id\": \"node_z\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],\n" +
                "      \"data\": {\n" +
                "        \"inputs\": { \n" +
                "          \"bVal\": {\"extractPath\": \"$.node_b.result\"},\n" +
                "          \"cVal\": {\"extractPath\": \"$.node_c.result\"}\n" +
                "        },\n" +
                "        \"expression\": \"bVal + '_' + cVal\"\n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\":\"${node_z.result}\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_b\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_c\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_b\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_z\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_c\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_z\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_z\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertEquals("B_C", result.getData());
    }

    @Test
    @DisplayName("15、测试复杂汇聚 (Diamond Problem)")
    void testComplexConvergence() throws JsonProcessingException {
        // D = b * 2, Z = d + c
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"node_b\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], \"data\": {\"expression\":\"2\"} },\n" +
                "    { \"id\": \"node_c\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], \"data\": {\"expression\":\"3\"} },\n" +
                "    { \"id\": \"node_d\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], \n" +
                "      \"data\": { \"inputs\":{\"b\":{\"extractPath\":\"$.node_b.result\"}}, \"expression\":\"b * 2\" } },\n" +
                "    { \"id\": \"node_z\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],\n" +
                "      \"data\": {\n" +
                "        \"inputs\": { \n" +
                "          \"dVal\": {\"extractPath\": \"$.node_d.result\"},\n" +
                "          \"cVal\": {\"extractPath\": \"$.node_c.result\"}\n" +
                "        },\n" +
                "        \"expression\": \"dVal + cVal\"\n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\":\"${node_z.result}\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_b\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_c\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_b\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_d\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_d\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_z\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_c\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_z\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_z\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertEquals(7L, result.getData()); // 3 + 4 = 7 (Aviator returns Long)
    }
// -----------------------------------------------------------------------
    // 以下为新增的 SpEL 专用测试用例
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("16、测试 SpEL 引擎 - 使用安全 Math 函数")
    void testSpELEngine() throws JsonProcessingException {
        // 场景：使用安全的 #Math.max() 语法 (不再支持 T() 类型引用，因为有安全风险)
        // 注意 data 中指定了 "language": "spel"
        // SpEL 变量使用 # 前缀
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"node_spel\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], \n" +
                "      \"data\": { \n" +
                "        \"language\": \"spel\", \n" +
                "        \"inputs\": { \"val1\": {\"extractPath\": \"$.start.args.v1\"}, \"val2\": {\"extractPath\": \"$.start.args.v2\"} },\n" +
                "        \"expression\": \"#Math.max(#val1, #val2)\" \n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \n" +
                "      \"data\": { \"responseBody\": \"${node_spel.result}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"node_spel\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"node_spel\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("v1", 10);
        args.put("v2", 99);

        ExecutionResult result = engine.execute(flowJson, args);
        // SpEL 计算结果应为 99.0 (MathHelper 返回 double)
        assertEquals(99.0, result.getData());
    }

    @Test
    @DisplayName("17、测试混合引擎 (Aviator + SpEL)")
    void testMixedEngines() throws JsonProcessingException {
        // 场景：Node A 用 Aviator 计算，Node B 用 SpEL 判断
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"calc_aviator\", \"type\": \"evaluate\", \n" +
                "      \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],\n" +
                "      \"data\": { \n" +
                "        \"language\": \"aviator\", \n" + // 显式声明 Aviator
                "        \"inputs\": { \"a\": {\"extractPath\": \"$.start.args.price\"} },\n" +
                "        \"expression\": \"a * 0.8\" \n" + // 打8折
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"check_spel\", \"type\": \"if\", \n" +
                "      \"ports\": [{\"id\":\"in\"}, {\"id\":\"true\"}, {\"id\":\"false\"}],\n" +
                "      \"data\": { \n" +
                "        \"language\": \"spel\", \n" + // 显式声明 SpEL
                "        \"inputs\": { \"discountPrice\": {\"extractPath\": \"$.calc_aviator.result\"} },\n" +
                "        \"condition\": \"#discountPrice < 100.0\" \n" + // SpEL 语法
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end_cheap\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"CHEAP\"} },\n" +
                "    { \"id\": \"end_expensive\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"EXPENSIVE\"} }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"calc_aviator\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"calc_aviator\", \"port\": \"out\"}, \"target\": {\"cell\": \"check_spel\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"check_spel\", \"port\": \"true\"}, \"target\": {\"cell\": \"end_cheap\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"check_spel\", \"port\": \"false\"}, \"target\": {\"cell\": \"end_expensive\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();

        // Case 1: 100 * 0.8 = 80 (<100) -> CHEAP
        args.put("price", 100);
        assertEquals("CHEAP", ((ExecutionResult) engine.execute(flowJson, args)).getData());

        // Case 2: 200 * 0.8 = 160 (>100) -> EXPENSIVE
        args.put("price", 200);
        assertEquals("EXPENSIVE", ((ExecutionResult) engine.execute(flowJson, args)).getData());
    }

    // =================================================================
    // SpEL 安全测试用例 - 验证危险操作被正确阻止
    // =================================================================

    @Test
    @DisplayName("18、SpEL安全 - 阻止 Runtime.exec() 系统命令执行")
    void testSpELSecurityBlockRuntimeExec() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"hack\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"expression\": \"T(java.lang.Runtime).getRuntime().exec('cmd')\", \"language\": \"spel\" } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"hacked\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"hack\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"hack\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertFalse(result.isSuccess(), "安全检查应阻止 Runtime.exec() 调用");
        assertTrue(result.getMessage().contains("安全限制") || result.getMessage().contains("T()"),
                "错误消息应包含安全限制提示: " + result.getMessage());
    }

    @Test
    @DisplayName("19、SpEL安全 - 阻止 ProcessBuilder 进程创建")
    void testSpELSecurityBlockProcessBuilder() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"hack\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"expression\": \"new java.lang.ProcessBuilder('cmd').start()\", \"language\": \"spel\" } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"hacked\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"hack\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"hack\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertFalse(result.isSuccess(), "安全检查应阻止 ProcessBuilder 调用");
        assertTrue(result.getMessage().contains("安全限制") || result.getMessage().contains("new"),
                "错误消息应包含安全限制提示: " + result.getMessage());
    }

    @Test
    @DisplayName("20、SpEL安全 - 阻止 Class.forName() 反射攻击")
    void testSpELSecurityBlockClassForName() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"hack\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"expression\": \"T(java.lang.Class).forName('java.lang.Runtime').getMethod('getRuntime').invoke(null)\", \"language\": \"spel\" } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"hacked\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"hack\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"hack\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertFalse(result.isSuccess(), "安全检查应阻止 Class.forName() 调用");
        assertTrue(result.getMessage().contains("安全限制") || result.getMessage().contains("T()"),
                "错误消息应包含安全限制提示: " + result.getMessage());
    }

    @Test
    @DisplayName("21、SpEL安全 - 允许安全的 Math 函数调用")
    void testSpELSecurityAllowSafeMathFunctions() throws JsonProcessingException {
        // 测试安全的 Math 函数: max, min, abs 通过 #Math 辅助对象调用
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"inputs\": { \"a\": {\"extractPath\": \"$.start.args.a\"}, \"b\": {\"extractPath\": \"$.start.args.b\"} }," +
                "      \"expression\": \"#Math.max(#a, #b)\", \"language\": \"spel\" } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"${calc.result}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("a", 10);
        args.put("b", 25);
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "安全的 Math 函数应该成功执行: " + result.getMessage());
        assertEquals(25.0, result.getData());
    }

    @Test
    @DisplayName("22、SpEL安全 - 允许基本算术和比较运算")
    void testSpELSecurityAllowBasicOperations() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"inputs\": { \"x\": {\"extractPath\": \"$.start.args.x\"} }," +
                "      \"expression\": \"#x * 2 + 10\", \"language\": \"spel\" } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"${calc.result}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("x", 5);
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "基本算术应该成功执行: " + result.getMessage());
        assertEquals(20, result.getData());
    }

    @Test
    @DisplayName("23、SpEL安全 - 阻止读取系统属性")
    void testSpELSecurityBlockSystemProperties() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"hack\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"expression\": \"T(java.lang.System).getProperty('user.home')\", \"language\": \"spel\" } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"${hack.result}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"hack\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"hack\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertFalse(result.isSuccess(), "安全检查应阻止 System.getProperty() 调用");
        assertTrue(result.getMessage().contains("安全限制") || result.getMessage().contains("System"),
                "错误消息应包含安全限制提示: " + result.getMessage());
    }

    // =================================================================
    // Start 节点参数校验测试用例
    // =================================================================

    @Test
    @DisplayName("24、参数校验 - 必填项检查")
    void testParamValidationRequired() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}], " +
                "    \"data\": { \"validations\": { \"username\": { \"required\": true, \"message\": \"用户名不能为空\" } } } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"ok\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        // Case 1: 缺少 username -> 失败
        ExecutionResult result1 = engine.execute(flowJson, new HashMap<>());
        assertFalse(result1.isSuccess(), "缺少必填参数应失败");
        assertTrue(result1.getMessage().contains("用户名不能为空"), "错误消息应包含验证信息");

        // Case 2: username 为空字符串 -> 失败
        Map<String, Object> args2 = new HashMap<>();
        args2.put("username", "  ");
        ExecutionResult result2 = engine.execute(flowJson, args2);
        assertFalse(result2.isSuccess(), "空字符串参数应失败");

        // Case 3: 提供 username -> 成功
        Map<String, Object> args3 = new HashMap<>();
        args3.put("username", "test_user");
        ExecutionResult result3 = engine.execute(flowJson, args3);
        assertTrue(result3.isSuccess(), "提供必填参数应成功");
    }

    @Test
    @DisplayName("25、参数校验 - 手机号和邮箱检查")
    void testParamValidationPhoneAndEmail() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}], " +
                "    \"data\": { \"validations\": { " +
                "      \"phone\": { \"type\": \"phone\", \"required\": true }, " +
                "      \"email\": { \"type\": \"email\" } " +
                "    } } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"ok\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        // Case 1: 手机号格式错误
        Map<String, Object> args1 = new HashMap<>();
        args1.put("phone", "123456");
        ExecutionResult result1 = engine.execute(flowJson, args1);
        assertFalse(result1.isSuccess());
        assertTrue(result1.getMessage().contains("手机号格式"), "错误消息应包含手机号错误提示");

        // Case 2: 邮箱格式错误
        Map<String, Object> args2 = new HashMap<>();
        args2.put("phone", "13800138000");
        args2.put("email", "invalid-email");
        ExecutionResult result2 = engine.execute(flowJson, args2);
        assertFalse(result2.isSuccess());
        assertTrue(result2.getMessage().contains("邮箱格式"), "错误消息应包含邮箱错误提示");

        // Case 3: 全部正确
        Map<String, Object> args3 = new HashMap<>();
        args3.put("phone", "13800138000");
        args3.put("email", "test@example.com");
        ExecutionResult result3 = engine.execute(flowJson, args3);
        assertTrue(result3.isSuccess());
    }

    @Test
    @DisplayName("26、参数校验 - 数值范围和正则检查")
    void testParamValidationRangeAndRegex() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}], " +
                "    \"data\": { \"validations\": { " +
                "      \"age\": { \"type\": \"range\", \"min\": 18, \"max\": 60, \"message\": \"年龄必须在18-60之间\" }, " +
                "      \"code\": { \"type\": \"regex\", \"pattern\": \"^[A-Z]{3}\\\\d{3}$\", \"message\": \"编码格式错误\" } " +
                "    } } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"ok\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        // Case 1: 年龄小于 18
        Map<String, Object> args1 = new HashMap<>();
        args1.put("age", 16);
        ExecutionResult result1 = engine.execute(flowJson, args1);
        assertFalse(result1.isSuccess());
        assertTrue(result1.getMessage().contains("年龄必须在18-60之间"));

        // Case 2: 编码格式错误
        Map<String, Object> args2 = new HashMap<>();
        args2.put("age", 25);
        args2.put("code", "abc123"); // 小写字母不支持
        ExecutionResult result2 = engine.execute(flowJson, args2);
        assertFalse(result2.isSuccess());
        assertTrue(result2.getMessage().contains("编码格式错误"));

        // Case 3: 全部正确
        Map<String, Object> args3 = new HashMap<>();
        args3.put("age", 30);
        args3.put("code", "ABC123");
        ExecutionResult result3 = engine.execute(flowJson, args3);
        assertTrue(result3.isSuccess());
    }

    // =================================================================
    // Request 节点测试用例
    // =================================================================

    @Test
    @DisplayName("27、Request节点 - 正确拆分 headers/params/body")
    void testRequestNodeSplitData() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"req\", \"type\": \"request\", \"ports\": [{\"id\":\"headers\"},{\"id\":\"params\"},{\"id\":\"body\"}] }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": {\"responseBody\": \"${req.body.username}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"req\", \"port\": \"body\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();

        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        headers.put("Content-Type", "application/json");
        args.put("headers", headers);

        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        args.put("params", params);

        Map<String, Object> body = new HashMap<>();
        body.put("username", "testUser");
        body.put("email", "test@example.com");
        args.put("body", body);

        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "Request 节点应成功执行");
    }

    @Test
    @DisplayName("28、Request节点 - 参数校验失败")
    void testRequestNodeValidationFailed() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"req\", \"type\": \"request\", \"ports\": [{\"id\":\"headers\"},{\"id\":\"params\"},{\"id\":\"body\"}], " +
                "    \"data\": { \"validations\": { " +
                "      \"userId\": { \"required\": true, \"message\": \"用户ID必填\" }, " +
                "      \"phone\": { \"type\": \"phone\", \"required\": true, \"message\": \"请输入正确的手机号\" } " +
                "    } } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"ok\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"req\", \"port\": \"body\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        // Case 1: 缺少必填参数
        Map<String, Object> args1 = new HashMap<>();
        args1.put("body", new HashMap<>());
        ExecutionResult result1 = engine.execute(flowJson, args1);
        assertFalse(result1.isSuccess(), "缺少必填参数应失败");
        assertTrue(result1.getMessage().contains("用户ID必填"), "错误消息: " + result1.getMessage());

        // Case 2: 手机号格式错误
        Map<String, Object> args2 = new HashMap<>();
        Map<String, Object> body2 = new HashMap<>();
        body2.put("userId", "U001");
        body2.put("phone", "123abc");
        args2.put("body", body2);
        ExecutionResult result2 = engine.execute(flowJson, args2);
        assertFalse(result2.isSuccess(), "手机号格式错误应失败");
        assertTrue(result2.getMessage().contains("手机号"), "错误消息: " + result2.getMessage());

        // Case 3: 全部正确
        Map<String, Object> args3 = new HashMap<>();
        Map<String, Object> body3 = new HashMap<>();
        body3.put("userId", "U001");
        body3.put("phone", "13800138000");
        args3.put("body", body3);
        ExecutionResult result3 = engine.execute(flowJson, args3);
        assertTrue(result3.isSuccess(), "参数校验通过应成功");
    }

    // =================================================================
    // HttpRequest 节点测试用例
    // =================================================================

    @Test
    @DisplayName("29、HttpRequest节点 - GET 请求")
    void testHttpRequestNode() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"http\", \"type\": \"httpRequest\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"url\": \"https://httpbin.org/get?name=${username}\", \"method\": \"GET\", \"timeout\": 10000 } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": {\"responseBody\": \"${http.status}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"http\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"http\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("username", "flowTest");
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "HttpRequest 应成功执行: " + result.getMessage());
    }

    // =================================================================
    // ForEach 循环节点测试用例
    // =================================================================

    @Test
    @DisplayName("32、For节点 - 并发遍历数组并调用ServiceCall")
    void testForEachNode() throws JsonProcessingException {
        // 流程: start -> for([1,2,3])
        //   item端口 -> serviceCall(testService.log) -> collect
        //   list端口 -> end
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"loop\", \"type\": \"for\", \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}], " +
                "    \"data\": { \"collectStepId\": \"collector\", \"inputs\": { \"collection\": {\"extractPath\": \"$.start.args.items\"} } } }," +
                "  { \"id\": \"logStep\", \"type\": \"call\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"service\": \"testService\", \"method\": \"log\", " +
                "      \"args\": [\"${loop.item}\"] } }," +
                "  { \"id\": \"collector\", \"type\": \"collect\", \"ports\": [{\"id\":\"item\"},{\"id\":\"list\"}], " +
                "    \"data\": { \"inputs\": { \"val\": {\"extractPath\": \"$.logStep.out\"} } } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": {\"responseBody\": \"${collector.count}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"loop\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"loop\", \"port\": \"item\"}, \"target\": {\"cell\": \"logStep\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"logStep\", \"port\": \"out\"}, \"target\": {\"cell\": \"collector\", \"port\": \"item\"} }," +
                "  { \"source\": {\"cell\": \"collector\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("items", Arrays.asList("apple", "banana", "cherry"));

        testService.logs.clear();
        ExecutionResult result = engine.execute(flowJson, args);

        assertTrue(result.isSuccess(), "For 应成功: " + result.getMessage());
        assertEquals(3, testService.logs.size(), "Service 应被调用 3 次, 实际: " + testService.logs);
    }

    // =================================================================
    // Template 模版节点测试用例
    // =================================================================

    @Test
    @DisplayName("33、Template节点 - 模版字符串替换")
    void testTemplateNode() throws JsonProcessingException {
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"tmpl\", \"type\": \"template\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"template\": \"User: {{name}}, Score: {{score}}\"," +
                "      \"inputs\": { \"name\": \"${userName}\", \"score\": \"${userScore}\" }" +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": {\"responseBody\": \"${tmpl.result}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"tmpl\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"tmpl\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("userName", "Alice");
        args.put("userScore", 98);
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "Template 应成功: " + result.getMessage());
    }

    // =================================================================
    // ForEach + Evaluate + Collect 综合测试
    // =================================================================

    @Test
    @DisplayName("38、ForEach + Evaluate + Collect - 数组映射聚合")
    void testLoopWithCollect() throws JsonProcessingException {
        // 流程: start -> for([1,2,3])
        //   item -> evaluate(item * 10) -> collect
        //   list -> end (验证 collect.list = [10, 20, 30])
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"loop\", \"type\": \"for\", \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}], " +
                "    \"data\": { \"collectStepId\": \"collector\", \"inputs\": { \"collection\": {\"extractPath\": \"$.start.args.numbers\"} } } }," +
                "  { \"id\": \"calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"expression\": \"n * 10\"," +
                "      \"inputs\": { \"n\": \"$['loop']['item']\" }" +
                "    } }," +
                "  { \"id\": \"collector\", \"type\": \"collect\", \"ports\": [{\"id\":\"item\"},{\"id\":\"list\"}], " +
                "    \"data\": { \"inputs\": { \"val\": {\"extractPath\": \"$.calc.result\"} } } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": {\"responseBody\": \"${collector.list}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"loop\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"loop\", \"port\": \"item\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"collector\", \"port\": \"item\"} }," +
                "  { \"source\": {\"cell\": \"collector\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("numbers", Arrays.asList(1, 2, 3));
        ExecutionResult result = engine.execute(flowJson, args);

        assertTrue(result.isSuccess(), "For+Collect 应成功: " + result.getMessage());
        assertTrue(result.getData() != null && result.getData().toString().contains("10"));
        assertTrue(result.getData().toString().contains("20"));
        assertTrue(result.getData().toString().contains("30"));
    }

    // =================================================================
    // Record 数据构造节点测试用例
    // =================================================================

    @Test
    @DisplayName("35、Record节点 - 从多节点构造对象")
    @SuppressWarnings("unchecked")
    void testRecordNode() throws JsonProcessingException {
        // 流程: start(name="Jack",age=18) -> evaluate(age>=18) -> record -> end
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"expression\": \"age >= 18\", " +
                "      \"inputs\": { \"age\": \"$['start']['args']['age']\" } } }," +
                "  { \"id\": \"rec\", \"type\": \"record\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"schema\": { " +
                "      \"uName\": \"$['start']['args']['name']\", " +
                "      \"isAdult\": \"${calc.result}\" " +
                "    } } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": {\"responseBody\": \"${rec.result}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"rec\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"rec\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("name", "Jack");
        args.put("age", 18);
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "Record 应成功: " + result.getMessage());

        // 验证输出是构造好的 Map
        assertNotNull(result.getData(), "应有输出数据");
        assertTrue(result.getData() instanceof Map, "输出应为 Map");
        Map<String, Object> outputMap = (Map<String, Object>) result.getData();
        assertEquals("Jack", outputMap.get("uName"), "uName 应为 Jack");
        assertNotNull(outputMap.get("isAdult"), "isAdult 不应为 null");
    }

    // =================================================================
    // Response HTTP 响应终态节点测试用例
    // =================================================================

    @Test
    @DisplayName("37、Response节点 - 404 + 自定义 Headers")
    void testResponseNode() throws JsonProcessingException {
        // 流程: start -> response(status=404, headers, body)
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"resp\", \"type\": \"response\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": { " +
                "      \"status\": 404, " +
                "      \"headers\": { \"X-Flow\": \"v1\", \"Content-Type\": \"application/json\" }, " +
                "      \"body\": { \"error\": \"Not Found\" } " +
                "    } }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"resp\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        org.springframework.http.ResponseEntity<?> response = engine.execute(flowJson, args);

        // 验证结构化响应
        assertNotNull(response, "应有输出数据");

        assertEquals(404, response.getStatusCodeValue(), "状态码应为 404");

        org.springframework.http.HttpHeaders headers = response.getHeaders();
        assertEquals("v1", headers.getFirst("X-Flow"), "X-Flow 头应为 v1");
        assertEquals("application/json", headers.getFirst("Content-Type"), "Content-Type 应正确");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("Not Found", body.get("error"), "Body error 应为 Not Found");
    }

    // ==========================================
    // HTTP Request 测试 (28)
    // ==========================================

    @Test
    @DisplayName("30、HttpRequest 节点 - 基本执行与变量替换")
    void testHttpRequestExecution() throws JsonProcessingException {
        // 由于无法连接真实服务，我们请求一个本地不存在端口，预期走 fail 分支
        // 重点验证：变量替换逻辑是否正常执行（不会报错说是无法解析）
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"http_req\", \"type\": \"httpRequest\", \n" +
                "      \"ports\": [{\"id\":\"in\"}, {\"id\":\"success\"}, {\"id\":\"fail\"}],\n" +
                "      \"data\": {\n" +
                "        \"url\": \"http://localhost:54321/api/${uid}\",\n" +
                "        \"method\": \"GET\",\n" +
                "        \"inputs\": { \"uid\": {\"extractPath\": \"$.start.args.userId\"} },\n" +
                "        \"params\": { \"type\": \"User\" },\n" +
                "        \"timeout\": 1000\n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end_success\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": { \"responseBody\": \"SUCCESS\" } },\n" +
                "    { \"id\": \"end_fail\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": { \"responseBody\": \"FAIL\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"http_req\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"http_req\", \"port\": \"success\"}, \"target\": {\"cell\": \"end_success\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"http_req\", \"port\": \"fail\"}, \"target\": {\"cell\": \"end_fail\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("userId", 123);

        // 预期因为连接不上而走 fail 分支，但不会抛出异常
        ExecutionResult result = engine.execute(flowJson, args);
        assertEquals("FAIL", result.getData());
    }

    // =================================================================
    // NEW: 真实 HTTP 请求集成测试 (使用 JDK 内置 HttpServer)
    // =================================================================

    @Test
    @DisplayName("31、集成测试 - HttpRequest 真实网络调用 (MockServer)")
    void testHttpRequestRealIntegration() throws Exception {
        // 1. 启动一个临时的本地 HTTP Server (随机端口)
        // 注意：需要引入 com.sun.net.httpserver 包，这是 JDK 自带的，无需 Maven 依赖
        // 如果你的 IDE 报错，请确保 JDK >= 8
        java.net.InetSocketAddress address = new java.net.InetSocketAddress(0);
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(address, 0);

        // 定义一个回显接口
        server.createContext("/api/echo", exchange -> {
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            // 读取 Request Body
            java.io.InputStream is = exchange.getRequestBody();
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String body = s.hasNext() ? s.next() : "";

            // 构造 JSON 响应
            String respJson = String.format("{\"echoMethod\": \"%s\", \"echoQuery\": \"%s\", \"echoBody\": \"%s\"}",
                    method, query, body);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, respJson.getBytes().length);
            java.io.OutputStream os = exchange.getResponseBody();
            os.write(respJson.getBytes());
            os.close();
        });

        server.start();
        int port = server.getAddress().getPort();
        System.out.println("Test Server started on port " + port);

        try {
            // 2. 构建流程
            String flowJson = "{\n" +
                    "  \"nodes\": [\n" +
                    "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                    "    { \"id\": \"http_req\", \"type\": \"httpRequest\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"success\"}, {\"id\":\"fail\"}],\n" +
                    "      \"data\": { \n" +
                    "        \"method\": \"POST\", \n" +
                    "        \"url\": \"http://localhost:" + port + "/api/echo\",\n" +
                    "        \"timeout\": 2000,\n" +
                    "        \"inputs\": { \"uid\": {\"extractPath\": \"$.start.args.userId\"} },\n" +
                    "        \"params\": { \"q\": \"${uid}\" }, \n" + // 测试 Query Param
                    "        \"body\": \"data_${uid}\" \n" + // 测试 Body
                    "      }\n" +
                    "    },\n" +
                    "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \n" +
                    "      \"data\": { \"responseBody\": \"${http_req.body}\" } }\n" + // 获取解析后的 JSON Body
                    "  ],\n" +
                    "  \"edges\": [\n" +
                    "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"http_req\", \"port\": \"in\"} },\n" +
                    "    { \"source\": {\"cell\": \"http_req\", \"port\": \"success\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                    "  ]\n" +
                    "}";

            Map<String, Object> args = new HashMap<>();
            args.put("userId", "999");

            ExecutionResult result = engine.execute(flowJson, args);

            // 3. 验证
            assertTrue(result.isSuccess(), "HTTP 流程应成功");

            // 验证结果是否为 Map (自动解析 JSON)
            Object data = result.getData();
            if (data instanceof Map) {
                Map<?,?> map = (Map<?,?>) data;
                assertEquals("POST", map.get("echoMethod"));
                assertTrue(map.get("echoQuery").toString().contains("q=999"));
                assertEquals("data_999", map.get("echoBody"));
            } else {
                // 如果未实现自动解析，验证字符串
                String str = data.toString();
                assertTrue(str.contains("data_999"));
            }

        } finally {
            server.stop(0);
        }
    }

    // =================================================================
    // NEW: Template, Record, Collect 综合测试
    // =================================================================

    @Test
    @DisplayName("34、Template节点 - 模版渲染")
    void testTemplateNode2() throws JsonProcessingException {
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"tpl\", \"type\": \"template\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"out\"}],\n" +
                "      \"data\": { \n" +
                "        \"template\": \"Hello {{name}}, code is {{code}}\",\n" +
                "        \"inputs\": { \n" +
                "           \"name\": {\"extractPath\": \"$.start.args.user\"}, \n" +
                "           \"code\": {\"extractPath\": \"$.start.args.id\"} \n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \n" +
                "      \"data\": { \"responseBody\": \"${tpl.result}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"tpl\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"tpl\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("user", "Alice");
        args.put("id", 123);

        ExecutionResult result = engine.execute(flowJson, args);
        assertEquals("Hello Alice, code is 123", result.getData());
    }

    @Test
    @DisplayName("36、Record节点 - 对象构造")
    void testRecordNode2() throws JsonProcessingException {
        // 构造一个 { "info": "Alice", "status": true } 对象
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"rec\", \"type\": \"record\", \"ports\": [{\"id\":\"in\"}, {\"id\":\"out\"}],\n" +
                "      \"data\": { \n" +
                "        \"schema\": { \n" +
                "           \"info\": {\"extractPath\": \"$.start.args.name\"}, \n" +
                "           \"status\": {\"extractPath\": \"$.start.args.active\"} \n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \n" +
                "      \"data\": { \"responseBody\": \"${rec.result}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"rec\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"rec\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("name", "Alice");
        args.put("active", true);

        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.getData() instanceof Map);
        Map<?,?> map = (Map<?,?>) result.getData();
        assertEquals("Alice", map.get("info"));
        assertEquals(true, map.get("status"));
    }

    @Test
    @DisplayName("39、Loop + Collect - 循环聚合测试")
    void testLoopCollect() throws JsonProcessingException {
        // Start([1,2]) -> Loop -> Evaluate(*10) -> Collect -> End(List)
        String flowJson = "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },\n" +
                "    { \"id\": \"loop\", \"type\": \"for\", \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}],\n" +
                "      \"data\": { \"collectStepId\": \"coll\", \"inputs\": { \"collection\": {\"extractPath\": \"$.start.args.list\"} } } },\n" +
                "    { \"id\": \"calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],\n" +
                "      \"data\": { \"expression\": \"item * 10\", \"inputs\": {\"item\": \"$.loop.item\"} } },\n" +
                "    { \"id\": \"coll\", \"type\": \"collect\", \"ports\": [{\"id\":\"item\"},{\"id\":\"list\"}],\n" +
                "      \"data\": { \"inputs\": { \"val\": {\"extractPath\": \"$.calc.result\"} } } },\n" +
                "    { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}],\n" +
                "      \"data\": { \"responseBody\": \"${coll.list}\" } }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"loop\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"loop\", \"port\": \"item\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} },\n" +
                "    { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"coll\", \"port\": \"item\"} },\n" +
                "    { \"source\": {\"cell\": \"coll\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("list", Arrays.asList(1, 2));

        ExecutionResult result = engine.execute(flowJson, args);

        assertTrue(result.isSuccess());
        // 验证结果为 [10, 20]
        assertTrue(result.getData() instanceof List);
        List<?> list = (List<?>) result.getData();
        assertEquals(2, list.size());
        // ForStep executor uses threadpool, so order is not strictly guaranteed right now!
        // but we'll check contains or sort it to assert correctly.
        List<Long> values = new ArrayList<>();
        values.add(Long.valueOf(list.get(0).toString()));
        values.add(Long.valueOf(list.get(1).toString()));
        Collections.sort(values);
        assertEquals(10L, values.get(0));
        assertEquals(20L, values.get(1));
    }

    // =================================================================
    // Database Node 测试用例 (Mock SqlExecutorService)
    // =================================================================

    @Test
    @DisplayName("40、Database 节点 - LIST 操作")
    void testDatabaseNodeListOperation() throws JsonProcessingException {
        // Mock SqlExecutorService
        SqlExecutorService sqlService = mock(SqlExecutorService.class);
        engine.setSqlExecutorService(sqlService);

        // 模拟返回数据
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>(); row1.put("id", 1); row1.put("name", "Alice");
        mockData.add(row1);

        when(sqlService.executeListQuery(eq("ds1"), any(SqlAndParams.class), any(Pageable.class)))
                .thenReturn(mockData);

        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"db_node\", \"type\": \"database\", \"ports\": [{\"id\":\"out\"},{\"id\":\"in\"}], " +
                "    \"data\": { " +
                "      \"datasourceId\": \"ds1\", " +
                "      \"sqlType\": \"SELECT\", " +
                "      \"returnType\": \"LIST\", " +
                "      \"sql\": \"SELECT * FROM users WHERE age > ${age}\", " +
                "      \"inputs\": { \"age\": {\"extractPath\": \"$.start.args.minAge\"} } " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"${db_node.result[0].name}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"db_node\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"db_node\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("minAge", 18);

        ExecutionResult result = engine.execute(flowJson, args);

        // 验证调用
        verify(sqlService, times(1)).executeListQuery(eq("ds1"), any(SqlAndParams.class), any(Pageable.class));

        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals("Alice", result.getData());
    }

    @Test
    @DisplayName("41、Database 节点 - UPDATE 操作")
    void testDatabaseNodeUpdateOperation() throws JsonProcessingException {
        // Mock SqlExecutorService
        SqlExecutorService sqlService = mock(SqlExecutorService.class);
        engine.setSqlExecutorService(sqlService);

        when(sqlService.executeUpdate(eq("ds1"), any(SqlAndParams.class)))
                .thenReturn(5); // 影响5行

        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"db_update\", \"type\": \"database\", \"ports\": [{\"id\":\"out\"},{\"id\":\"in\"}], " +
                "    \"data\": { " +
                "      \"datasourceId\": \"ds1\", " +
                "      \"sqlType\": \"UPDATE\", " +
                "      \"sql\": \"UPDATE users SET status = 1 WHERE id IN (${ids})\", " +
                "      \"inputs\": { \"ids\": {\"extractPath\": \"$.start.args.list\"} } " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"${db_update.result}\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"db_update\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"db_update\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("list", Arrays.asList(1, 2, 3));

        ExecutionResult result = engine.execute(flowJson, args);

        // 验证调用
        verify(sqlService, times(1)).executeUpdate(eq("ds1"), any(SqlAndParams.class));

        // 验证结果
        assertEquals(5, result.getData());
    }

    // =================================================================
    // Request → Response 节点联动测试
    // =================================================================

    @Test
    @DisplayName("42、Request→Response 节点联动 - 读取 params.name 参数")
    void testRequestToResponseWithParamName() throws Exception {
        // 模拟 GET /flow/test/flow01?name=ssss
        // request 节点把 params 存入 context.var["request_1"]["params"]["name"]
        // update 使得 response 节点必须声明 inputs: { "pName": "$.request_1.params.name" } 然后 template 用 ${pName}
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"request_1\", \"type\": \"request\","
                + "    \"ports\": [{\"id\":\"headers\"},{\"id\":\"params\"},{\"id\":\"body\"}],"
                + "    \"data\": { \"method\": \"GET\" } },"
                + "  { \"id\": \"response_1\", \"type\": \"response\","
                + "    \"ports\": [{\"id\":\"in:body\"}],"
                + "    \"data\": {"
                + "      \"inputs\": { \"pName\": \"$.request_1.params.name\" },"
                + "      \"status\": 200,"
                + "      \"headers\": {},"
                + "      \"body\": \"hello world ${pName}\""
                + "    } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"request_1\", \"port\": \"params\"},"
                + "    \"target\": {\"cell\": \"response_1\", \"port\": \"in:body\"} }"
                + "]"
                + "}";

        // 入参模拟：HTTP 框架会把 query params 放到 context 的 params 字段
        Map<String, Object> args = new HashMap<>();
        args.put("params", Collections.singletonMap("name", "ssss"));

        org.springframework.http.ResponseEntity<?> output = engine.execute(flowJson, args);
        assertNotNull(output, "输出不应为 null");
        assertEquals(200, output.getStatusCodeValue());
        assertEquals("hello world ssss", output.getBody(),
                "body 中应包含从 request params 解析出的 name 参数");
    }

    @Test
    @DisplayName("43、Request→Response 节点联动 - 直接返回 params 对象")
    void testRequestToResponseReturnParamsObject() throws Exception {
        // response.body = ${inputParams}
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"request_1\", \"type\": \"request\","
                + "    \"ports\": [{\"id\":\"params\"}],"
                + "    \"data\": { \"method\": \"GET\" } },"
                + "  { \"id\": \"response_1\", \"type\": \"response\","
                + "    \"ports\": [{\"id\":\"in:body\"}],"
                + "    \"data\": {"
                + "      \"inputs\": { \"inputParams\": \"$.request_1.params\" },"
                + "      \"status\": 200,"
                + "      \"headers\": {},"
                + "      \"body\": \"${inputParams}\""
                + "    } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"request_1\", \"port\": \"params\"},"
                + "    \"target\": {\"cell\": \"response_1\", \"port\": \"in:body\"} }"
                + "]"
                + "}";

        Map<String, Object> args = new HashMap<>();
        args.put("params", Collections.singletonMap("name", "ssss"));

        org.springframework.http.ResponseEntity<?> output = engine.execute(flowJson, args);
        assertNotNull(output);

        // body 应该是 params Map 本身
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) output.getBody();
        assertEquals("ssss", bodyMap.get("name"), "body 应为包含 name=ssss 的 params Map");
    }

    @Test
    @DisplayName("44、Response节点 - 复杂 ETL 与模板渲染")
    void testResponseWithComplexETL() throws Exception {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },"
                + "  { \"id\": \"resp\", \"type\": \"response\", \"ports\": [{\"id\":\"in\"}], "
                + "    \"data\": { "
                + "      \"inputs\": { "
                + "        \"userName\": { \"extractPath\": \"$.start.args.user.name\" },"
                + "        \"userAge\": \"$.start.args.user.age\","
                + "        \"statusCode\": \"$.start.args.status\""
                + "      },"
                + "      \"status\": \"${statusCode}\", "
                + "      \"headers\": { \"X-User-Name\": \"${userName}\", \"Content-Type\": \"application/json\" }, "
                + "      \"body\": { \"message\": \"User ${userName} is ${userAge} years old\" } "
                + "    } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"resp\", \"port\": \"in\"} }"
                + "]"
                + "}";

        Map<String, Object> args = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("name", "Bob");
        user.put("age", 25);
        args.put("user", user);
        args.put("status", 201);

        org.springframework.http.ResponseEntity<?> result = engine.execute(flowJson, args);

        assertEquals(201, result.getStatusCodeValue());
        assertEquals("Bob", result.getHeaders().getFirst("X-User-Name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertEquals("User Bob is 25 years old", body.get("message"));
    }

    @Test
    @DisplayName("45、Request节点 - 从接口请求中获取对应数据")
    void testRequestNode() throws Exception {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"req\", \"type\": \"request\", \"ports\": [{\"id\":\"headers\"}, {\"id\":\"params\"}, {\"id\":\"body\"}] },"
                + "  { \"id\": \"resp\", \"type\": \"response\", \"ports\": [{\"id\":\"in:var:v_hToken\"}, {\"id\":\"in:var:v_pKeyword\"}, {\"id\":\"in:var:v_bDataId\"}], "
                + "    \"data\": { "
                + "      \"inputs\": { "
                + "        \"hToken\": { \"id\": \"v_hToken\", \"extractPath\": \"$.token\" },"
                + "        \"pKeyword\": { \"id\": \"v_pKeyword\", \"extractPath\": \"$.keyword\" },"
                + "        \"bDataId\": { \"id\": \"v_bDataId\", \"extractPath\": \"$.dataId\" }"
                + "      },"
                + "      \"status\": 200, "
                + "      \"body\": { \"token\": \"${hToken}\", \"kw\": \"${pKeyword}\", \"id\": \"${bDataId}\" } "
                + "    } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"req\", \"port\": \"headers\"}, \"target\": {\"cell\": \"resp\", \"port\": \"in:var:v_hToken\"} },"
                + "  { \"source\": {\"cell\": \"req\", \"port\": \"params\"}, \"target\": {\"cell\": \"resp\", \"port\": \"in:var:v_pKeyword\"} },"
                + "  { \"source\": {\"cell\": \"req\", \"port\": \"body\"}, \"target\": {\"cell\": \"resp\", \"port\": \"in:var:v_bDataId\"} }"
                + "]"
                + "}";

        // ============================================
        // 使用 Mockito 模拟真实的 HttpServletRequest 提取
        // ============================================
        javax.servlet.http.HttpServletRequest request = mock(javax.servlet.http.HttpServletRequest.class);

        // 1. 模拟 Headers
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(Arrays.asList("token", "X-Custom")));
        when(request.getHeader("token")).thenReturn("myHeaderToken123");
        when(request.getHeader("X-Custom")).thenReturn("12345");

        // 2. 模拟 Params
        when(request.getParameterNames()).thenReturn(Collections.enumeration(Collections.singletonList("keyword")));
        when(request.getParameter("keyword")).thenReturn("searchParam");

        // 3. 模拟 Body (JSON 格式)
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(
                new java.io.BufferedReader(new java.io.StringReader("{\"dataId\":999, \"name\":\"testName\"}"))
        );

        // --- 以下模拟 FlowApiInterceptor 中的提取过程 ---
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }

        Map<String, String> params = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            params.put(name, request.getParameter(name));
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String bodyStr = request.getReader().lines().collect(java.util.stream.Collectors.joining());
        Map<String, Object> body = mapper.readValue(bodyStr, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

        // 最终组装给 FlowEngine 的 args
        Map<String, Object> args = new HashMap<>();
        args.put("headers", headers);
        args.put("params", params);
        args.put("body", body);

        org.springframework.http.ResponseEntity<?> result = engine.execute(flowJson, args);

        assertEquals(200, result.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = (Map<String, Object>) result.getBody();
        assertEquals("myHeaderToken123", respBody.get("token"));
        assertEquals("searchParam", respBody.get("kw"));
        assertEquals("999", String.valueOf(respBody.get("id")));
    }

    // =================================================================
    // ForStep + CollectStep - Scatter-Gather 线程接力并发测试
    // 关键架构：分支链路 scatter.item → calc → gather（图拓扑物理汇聚）
    //           无 done 端口，CollectStep 是真正的并发屏障
    // =================================================================

    @Test
    @DisplayName("51、ForStep + CollectStep - 标准并发 Scatter-Gather 数组映射（线程接力架构）")
    @SuppressWarnings("unchecked")
    void testForStepWithCollect() throws JsonProcessingException {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },"
                + "  { \"id\": \"scatter\", \"type\": \"for\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}],"
                + "    \"data\": {"
                + "      \"collectStepId\": \"gather\","
                + "      \"inputs\": { \"list\": { \"extractPath\": \"$.start.args.numbers\" } }"
                + "    } },"
                + "  { \"id\": \"calc\", \"type\": \"evaluate\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],"
                + "    \"data\": {"
                + "      \"expression\": \"n * 10\","
                + "      \"inputs\": { \"n\": \"$.scatter.item\" }"
                + "    } },"
                + "  { \"id\": \"gather\", \"type\": \"collect\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"list\"},{\"id\":\"finish\"}],"
                + "    \"data\": {"
                + "      \"inputs\": { \"val\": { \"extractPath\": \"$.calc.result\" } }"
                + "    } },"
                + "  { \"id\": \"end\", \"type\": \"end\","
                + "    \"ports\": [{\"id\":\"in\"}],"
                + "    \"data\": { \"responseBody\": \"${gather.list}\" } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"scatter\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"scatter\", \"port\": \"item\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"gather\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"gather\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }"
                + "]}";

        Map<String, Object> args = new HashMap<>();
        args.put("numbers", Arrays.asList(1, 2, 3));

        ExecutionResult result = engine.execute(flowJson, args);

        assertTrue(result.isSuccess(), "ForStep+CollectStep 应成功: " + result.getMessage());
        assertNotNull(result.getData(), "应有结果数据");
        assertTrue(result.getData() instanceof List, "结果应为 List，实际: " + result.getData());
        List<?> resultList = (List<?>) result.getData();
        assertEquals(3, resultList.size(), "应收集 3 个元素: " + resultList);

        Set<Long> expected = new HashSet<>(Arrays.asList(10L, 20L, 30L));
        Set<Long> actual = new HashSet<>();
        for (Object item : resultList) {
            actual.add(Long.valueOf(item.toString()));
        }
        assertEquals(expected, actual, "并发结果应包含 {10, 20, 30}: " + resultList);
    }

    @Test
    @DisplayName("52、ForStep - 空数组旁路（防死锁，totalCount=0 立即完成）")
    void testForStepWithEmptyArray() throws JsonProcessingException {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },"
                + "  { \"id\": \"scatter\", \"type\": \"for\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}],"
                + "    \"data\": {"
                + "      \"collectStepId\": \"gather\","
                + "      \"inputs\": { \"list\": { \"extractPath\": \"$.start.args.emptyList\" } }"
                + "    } },"
                + "  { \"id\": \"calc\", \"type\": \"evaluate\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],"
                + "    \"data\": { \"expression\": \"'should_not_execute'\" } },"
                + "  { \"id\": \"gather\", \"type\": \"collect\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"list\"},{\"id\":\"finish\"}],"
                + "    \"data\": {} },"
                + "  { \"id\": \"end\", \"type\": \"end\","
                + "    \"ports\": [{\"id\":\"in\"}],"
                + "    \"data\": { \"responseBody\": \"${gather.count}\" } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"scatter\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"scatter\", \"port\": \"item\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"gather\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"gather\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }"
                + "]}";

        Map<String, Object> args = new HashMap<>();
        args.put("emptyList", Collections.emptyList());

        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "空数组旁路应成功（不死锁）: " + result.getMessage());
        assertEquals(0, Integer.parseInt(String.valueOf(result.getData())),
                "空数组场景下 gather.count 应为 0");
    }

    @Test
    @DisplayName("53、ForStep - null 数组旁路（防死锁）")
    void testForStepWithNullArray() throws JsonProcessingException {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },"
                + "  { \"id\": \"scatter\", \"type\": \"for\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}],"
                + "    \"data\": {"
                + "      \"collectStepId\": \"gather\","
                + "      \"inputs\": { \"list\": { \"extractPath\": \"$.start.args.nullList\" } }"
                + "    } },"
                + "  { \"id\": \"gather\", \"type\": \"collect\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"list\"},{\"id\":\"finish\"}],"
                + "    \"data\": {} },"
                + "  { \"id\": \"end\", \"type\": \"end\","
                + "    \"ports\": [{\"id\":\"in\"}],"
                + "    \"data\": { \"responseBody\": \"${gather.count}\" } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"scatter\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"gather\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }"
                + "]}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertTrue(result.isSuccess(), "null 数组旁路应成功（不死锁）: " + result.getMessage());
    }

    @Test
    @DisplayName("54、ForStep + CollectStep - 单元素数组（totalCount=1 边界条件）")
    @SuppressWarnings("unchecked")
    void testForStepSingleElement() throws JsonProcessingException {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },"
                + "  { \"id\": \"scatter\", \"type\": \"for\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}],"
                + "    \"data\": {"
                + "      \"collectStepId\": \"gather\","
                + "      \"inputs\": { \"list\": { \"extractPath\": \"$.start.args.singleItem\" } }"
                + "    } },"
                + "  { \"id\": \"calc\", \"type\": \"evaluate\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],"
                + "    \"data\": { \"expression\": \"'processed_' + x\", \"inputs\": { \"x\": \"$.scatter.item\" } } },"
                + "  { \"id\": \"gather\", \"type\": \"collect\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"list\"},{\"id\":\"finish\"}],"
                + "    \"data\": { \"inputs\": { \"val\": { \"extractPath\": \"$.calc.result\" } } } },"
                + "  { \"id\": \"end\", \"type\": \"end\","
                + "    \"ports\": [{\"id\":\"in\"}],"
                + "    \"data\": { \"responseBody\": \"${gather.list}\" } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"scatter\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"scatter\", \"port\": \"item\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"gather\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"gather\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }"
                + "]}";

        Map<String, Object> args = new HashMap<>();
        args.put("singleItem", Collections.singletonList("hello"));

        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "单元素 ForStep 应成功: " + result.getMessage());
        assertTrue(result.getData() instanceof List, "结果应为 List");
        assertEquals(1, ((List<?>) result.getData()).size(), "单元素数组应收集 1 个结果");
    }

    @Test
    @DisplayName("55、ForStep - finish 端口触发验证（控制流线程接力）")
    void testForStepFinishPortTrigger() throws JsonProcessingException {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },"
                + "  { \"id\": \"scatter\", \"type\": \"for\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}],"
                + "    \"data\": {"
                + "      \"collectStepId\": \"gather\","
                + "      \"inputs\": { \"list\": { \"extractPath\": \"$.start.args.data\" } }"
                + "    } },"
                + "  { \"id\": \"process\", \"type\": \"evaluate\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],"
                + "    \"data\": { \"expression\": \"item + 1\", \"inputs\": { \"item\": \"$.scatter.item\" } } },"
                + "  { \"id\": \"gather\", \"type\": \"collect\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"list\"},{\"id\":\"finish\"}],"
                + "    \"data\": {} },"
                + "  { \"id\": \"end\", \"type\": \"end\","
                + "    \"ports\": [{\"id\":\"in\"}],"
                + "    \"data\": { \"responseBody\": \"collect_done\" } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"scatter\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"scatter\", \"port\": \"item\"}, \"target\": {\"cell\": \"process\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"process\", \"port\": \"out\"}, \"target\": {\"cell\": \"gather\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"gather\", \"port\": \"finish\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }"
                + "]}";

        Map<String, Object> args = new HashMap<>();
        args.put("data", Arrays.asList(10, 20));

        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "finish 端口控制流应成功: " + result.getMessage());
        assertEquals("collect_done", result.getData(), "应触发 finish 端口到 end 节点");
    }

    @Test
    @DisplayName("56、ForStep + Collect + 完整 Scatter-Gather 管道（5元素）")
    @SuppressWarnings("unchecked")
    void testForStepFullPipeline() throws JsonProcessingException {
        String flowJson = "{"
                + "\"nodes\": ["
                + "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] },"
                + "  { \"id\": \"scatter\", \"type\": \"for\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"item\"}],"
                + "    \"data\": {"
                + "      \"collectStepId\": \"gather\","
                + "      \"inputs\": { \"list\": { \"extractPath\": \"$.start.args.nums\" } }"
                + "    } },"
                + "  { \"id\": \"double\", \"type\": \"evaluate\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}],"
                + "    \"data\": { \"expression\": \"x * 2\", \"inputs\": { \"x\": \"$.scatter.item\" } } },"
                + "  { \"id\": \"gather\", \"type\": \"collect\","
                + "    \"ports\": [{\"id\":\"in\"},{\"id\":\"list\"},{\"id\":\"finish\"}],"
                + "    \"data\": { \"inputs\": { \"val\": { \"extractPath\": \"$.double.result\" } } } },"
                + "  { \"id\": \"end\", \"type\": \"end\","
                + "    \"ports\": [{\"id\":\"in\"}],"
                + "    \"data\": { \"responseBody\": \"${gather.count}\" } }"
                + "],"
                + "\"edges\": ["
                + "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"scatter\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"scatter\", \"port\": \"item\"}, \"target\": {\"cell\": \"double\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"double\", \"port\": \"out\"}, \"target\": {\"cell\": \"gather\", \"port\": \"in\"} },"
                + "  { \"source\": {\"cell\": \"gather\", \"port\": \"list\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }"
                + "]}";

        Map<String, Object> args = new HashMap<>();
        args.put("nums", Arrays.asList(1, 2, 3, 4, 5));

        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "完整管道应成功: " + result.getMessage());
        assertEquals(5, Integer.parseInt(result.getData().toString()), "完整管道应收集 5 个结果");
    }

    // =================================================================
    // JavaScript (GraalJS) 引擎测试用例
    // =================================================================

    @Test
    @DisplayName("57、JavaScript 引擎 - 基本表达式计算")
    void testJavaScriptBasicExpression() throws JsonProcessingException {
        // 场景：使用 JS 进行简单的数学计算
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"js_calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"inputs\": { \"a\": {\"extractPath\": \"$.start.args.a\"}, \"b\": {\"extractPath\": \"$.start.args.b\"} }," +
                "      \"expression\": \"a * b + 10\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": { \"responseBody\": \"${js_calc.result}\" } }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_calc\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("a", 5);
        args.put("b", 3);
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "JS 基本计算应成功: " + result.getMessage());
        // 5 * 3 + 10 = 25
        assertEquals(25L, result.getData());
    }

    @Test
    @DisplayName("58、JavaScript 引擎 - 数组 filter/map 操作")
    @SuppressWarnings("unchecked")
    void testJavaScriptArrayOperations() throws JsonProcessingException {
        // 场景：使用 JS 过滤和映射数组 - JS 最擅长的场景
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"js_filter\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"inputs\": { \"items\": {\"extractPath\": \"$.start.args.items\"} }," +
                "      \"expression\": \"items.filter(x => x > 50).map(x => x * 2)\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": { \"responseBody\": \"${js_filter.result}\" } }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_filter\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_filter\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("items", Arrays.asList(10, 60, 30, 80, 100));
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "JS 数组操作应成功: " + result.getMessage());

        // filter(>50): [60, 80, 100] -> map(*2): [120, 160, 200]
        Object data = result.getData();
        assertTrue(data instanceof List, "结果应为 List 类型");
        List<Object> list = (List<Object>) data;
        assertEquals(3, list.size(), "过滤后应有 3 个元素");
    }

    @Test
    @DisplayName("59、JavaScript 引擎 - 复杂对象变换")
    @SuppressWarnings("unchecked")
    void testJavaScriptObjectTransform() throws JsonProcessingException {
        // 场景：接收一个对象，进行字段组装和变换
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"js_transform\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"inputs\": { \"name\": {\"extractPath\": \"$.start.args.name\"}, \"age\": {\"extractPath\": \"$.start.args.age\"} }," +
                "      \"expression\": \"({fullName: 'User: ' + name, isAdult: age >= 18, doubleAge: age * 2})\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": { \"responseBody\": \"${js_transform.result}\" } }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_transform\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_transform\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("name", "Alice");
        args.put("age", 25);
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "JS 对象变换应成功: " + result.getMessage());

        Object data = result.getData();
        assertTrue(data instanceof Map, "结果应为 Map 类型");
        Map<String, Object> map = (Map<String, Object>) data;
        assertEquals("User: Alice", map.get("fullName"));
        assertEquals(true, map.get("isAdult"));
        assertEquals(50L, map.get("doubleAge"));
    }

    @Test
    @DisplayName("60、JavaScript 引擎 - ES6 模板字符串")
    void testJavaScriptStringConcat() throws JsonProcessingException {
        // 场景：使用 ES6 模板字符串 (GraalJS 21.3 完整支持)
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"js_str\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"inputs\": { \"greeting\": {\"extractPath\": \"$.start.args.greeting\"}, \"user\": {\"extractPath\": \"$.start.args.user\"} }," +
                "      \"expression\": \"`${greeting}, ${user}!`\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": { \"responseBody\": \"${js_str.result}\" } }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_str\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_str\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("greeting", "Hello");
        args.put("user", "World");
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "JS ES6 模板字符串应成功: " + result.getMessage());
        assertEquals("Hello, World!", result.getData());
    }

    @Test
    @DisplayName("61、JavaScript 安全 - 阻止 Java 类访问")
    void testJavaScriptSecurityBlockJavaAccess() throws JsonProcessingException {
        // 场景：尝试通过 JS 访问 Java 类（应被沙箱阻止）
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"js_hack\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"expression\": \"Java.type('java.lang.Runtime').getRuntime().exec('cmd')\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"hacked\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_hack\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_hack\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertFalse(result.isSuccess(), "安全检查应阻止 JS 访问 Java 类");
    }

    @Test
    @DisplayName("62、JavaScript 引擎 - 语法错误处理")
    void testJavaScriptSyntaxError() throws JsonProcessingException {
        // 场景：JS 脚本有语法错误，应返回友好的错误消息
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"js_bad\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"expression\": \"function( { broken syntax\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], \"data\": {\"responseBody\": \"ok\"} }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_bad\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_bad\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertFalse(result.isSuccess(), "JS 语法错误应导致执行失败");
    }

    // =================================================================
    // JavaScript 顶级变量 + $ 简写 extractPath 测试
    // =================================================================

    @Test
    @DisplayName("63、JS 顶级变量 + $ 简写 extractPath - 从上游 Evaluate 结果中提取")
    @SuppressWarnings("unchecked")
    void testJsTopLevelVarsWithDollarShorthand() throws JsonProcessingException {
        // 流程: start(items=[...]) -> js_step1(过滤>50) -> js_step2(用 $ 拿上游结果再 map)
        // js_step2 的 inputs 使用 "$" 简写，FlowParser 的 autoFillExtractPath 应自动补全
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"js_step1\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"inputs\": { \"items\": {\"extractPath\": \"$.start.args.items\"} }," +
                "      \"expression\": \"items.filter(x => x > 50)\" " +
                "    } }," +
                "  { \"id\": \"js_step2\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"inputs\": { \"arr\": {\"extractPath\": \"$\"} }," +
                "      \"expression\": \"arr.map(x => x * 10)\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": { \"responseBody\": \"${js_step2.result}\" } }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_step1\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_step1\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_step2\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_step2\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        Map<String, Object> args = new HashMap<>();
        args.put("items", Arrays.asList(10, 60, 30, 80, 100));
        ExecutionResult result = engine.execute(flowJson, args);
        assertTrue(result.isSuccess(), "JS $ 简写应成功: " + result.getMessage());

        // filter(>50): [60, 80, 100] -> map(*10): [600, 800, 1000]
        Object data = result.getData();
        assertTrue(data instanceof List, "结果应为 List 类型");
        List<Object> list = (List<Object>) data;
        assertEquals(3, list.size(), "应有 3 个元素");
        assertEquals(600L, list.get(0));
        assertEquals(800L, list.get(1));
        assertEquals(1000L, list.get(2));
    }

    @Test
    @DisplayName("64、JS 顶级变量 + $.field 相对路径提取")
    @SuppressWarnings("unchecked")
    void testJsTopLevelVarsWithDollarDotShorthand() throws JsonProcessingException {
        // 流程: start -> evaluate(返回对象 {result: ...}) -> js_step(用 $.result 提取子字段)
        String flowJson = "{" +
                "\"nodes\": [" +
                "  { \"id\": \"start\", \"type\": \"start\", \"ports\": [{\"id\":\"out\"}] }," +
                "  { \"id\": \"calc\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { \"language\": \"js\", \"expression\": \"({ result: 10 + 20 })\" } }," +
                "  { \"id\": \"js_step\", \"type\": \"evaluate\", \"ports\": [{\"id\":\"in\"},{\"id\":\"out\"}], " +
                "    \"data\": { " +
                "      \"language\": \"js\", " +
                "      \"inputs\": { \"val\": {\"extractPath\": \"$.result\"} }," +
                "      \"expression\": \"val * 3\" " +
                "    } }," +
                "  { \"id\": \"end\", \"type\": \"end\", \"ports\": [{\"id\":\"in\"}], " +
                "    \"data\": { \"responseBody\": \"${js_step.result}\" } }" +
                "]," +
                "\"edges\": [" +
                "  { \"source\": {\"cell\": \"start\", \"port\": \"out\"}, \"target\": {\"cell\": \"calc\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"calc\", \"port\": \"out\"}, \"target\": {\"cell\": \"js_step\", \"port\": \"in\"} }," +
                "  { \"source\": {\"cell\": \"js_step\", \"port\": \"out\"}, \"target\": {\"cell\": \"end\", \"port\": \"in\"} }" +
                "]" +
                "}";

        ExecutionResult result = engine.execute(flowJson, new HashMap<>());
        assertTrue(result.isSuccess(), "JS $.result 简写应成功: " + result.getMessage());
        // (10 + 20) * 3 = 90
        assertEquals(90L, result.getData());
    }
}
