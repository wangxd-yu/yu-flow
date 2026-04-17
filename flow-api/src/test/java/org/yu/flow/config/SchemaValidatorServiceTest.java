package org.yu.flow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yu.flow.exception.SchemaValidationException;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * SchemaValidatorService 单元测试
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>空/null 契约 JSON → 跳过校验（无异常）</li>
 *   <li>Body 校验：required、type、pattern、minLength/maxLength、number 约束、嵌套 object/array</li>
 *   <li>Query 校验：required、pattern、minLength/maxLength</li>
 *   <li>混合 Body + Query 同时校验</li>
 *   <li>中文错误消息覆盖</li>
 *   <li>异常契约 JSON 格式处理</li>
 * </ul>
 *
 * @author yu-flow
 */
class SchemaValidatorServiceTest {

    private static SchemaValidatorService service;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        service = new SchemaValidatorService();
    }

    // ============================= 1. 空/null 契约跳过校验 =============================

    @ParameterizedTest(name = "空契约 JSON=\"{0}\" 应跳过校验")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldSkipWhenContractIsBlank(String contractJson) {
        assertDoesNotThrow(() ->
                service.validateFromContract(contractJson,
                        mapOf("key", "value"),
                        singletonStringMap("q", "v"))
        );
    }

    @Test
    @DisplayName("契约 JSON 缺少 request 节点 → 跳过校验")
    void shouldSkipWhenRequestNodeMissing() {
        String contract = "{\"responses\":{}}";
        assertDoesNotThrow(() ->
                service.validateFromContract(contract, mapOf("k", "v"), singletonStringMap("q", "v"))
        );
    }

    // ============================= 2. Body 校验 - required =============================

    @Test
    @DisplayName("Body: 必填字段缺失 → 抛出 SchemaValidationException")
    void bodyRequiredFieldMissing() {
        String contract = buildContract("json",
                Collections.singletonList(schemaNode("username", "string", "用户名", true)),
                null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("otherField", "value"),
                        null)
        );
        // required 错误消息可能包含字段名("username")或回退为通用提示
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                        e.contains("不能为空") || e.contains("缺少必填字段")),
                "实际: " + ex.getErrors());
    }

    @Test
    @DisplayName("Body: 必填字段存在 → 校验通过")
    void bodyRequiredFieldPresent() {
        String contract = buildContract("json",
                Collections.singletonList(schemaNode("username", "string", "用户名", true)),
                null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("username", "admin"),
                        null)
        );
    }

    // ============================= 3. Body 校验 - type =============================

    @Test
    @DisplayName("Body: 字段类型不匹配 → 抛出类型不匹配错误")
    void bodyTypeMismatch() {
        String contract = buildContract("json",
                Collections.singletonList(schemaNode("age", "integer", "年龄", false)),
                null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("age", "not_a_number"),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("类型不匹配")));
    }

    @Test
    @DisplayName("Body: integer 类型接受整数值 → 校验通过")
    void bodyTypeMatchInteger() {
        String contract = buildContract("json",
                Collections.singletonList(schemaNode("age", "integer", "年龄", false)),
                null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("age", 25),
                        null)
        );
    }

    // ============================= 4. Body 校验 - string 规则 =============================

    @Test
    @DisplayName("Body: pattern 正则校验失败")
    void bodyPatternMismatch() {
        Map<String, Object> node = schemaNode("email", "string", "邮箱", false);
        node.put("pattern", "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$");

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("email", "invalid-email"),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("格式不匹配")));
    }

    @Test
    @DisplayName("Body: pattern 正则校验通过")
    void bodyPatternMatch() {
        Map<String, Object> node = schemaNode("email", "string", "邮箱", false);
        node.put("pattern", "^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$");

        String contract = buildContract("json", Collections.singletonList(node), null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("email", "test@example.com"),
                        null)
        );
    }

    @Test
    @DisplayName("Body: minLength 校验失败")
    void bodyMinLengthFail() {
        Map<String, Object> node = schemaNode("name", "string", "名称", false);
        node.put("minLength", 3);

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("name", "ab"),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("长度不足")));
    }

    @Test
    @DisplayName("Body: maxLength 校验失败")
    void bodyMaxLengthFail() {
        Map<String, Object> node = schemaNode("name", "string", "名称", false);
        node.put("maxLength", 5);

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("name", "toolongname"),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("最大长度限制")));
    }

    // ============================= 5. Body 校验 - number 约束 =============================

    @Test
    @DisplayName("Body: minimum 校验失败")
    void bodyMinimumFail() {
        Map<String, Object> node = schemaNode("score", "number", "分数", false);
        node.put("minimum", 0);

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("score", -1),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("小于最小值")));
    }

    @Test
    @DisplayName("Body: maximum 校验失败")
    void bodyMaximumFail() {
        Map<String, Object> node = schemaNode("score", "number", "分数", false);
        node.put("maximum", 100);

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("score", 150),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("最大值限制")));
    }

    @Test
    @DisplayName("Body: multipleOf 校验失败")
    void bodyMultipleOfFail() {
        Map<String, Object> node = schemaNode("quantity", "integer", "数量", false);
        node.put("multipleOf", 5);

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("quantity", 7),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("倍数约束")));
    }

    // ============================= 6. Body 校验 - 嵌套 object =============================

    @Test
    @DisplayName("Body: 嵌套 object 必填字段缺失")
    void bodyNestedObjectRequiredMissing() {
        Map<String, Object> cityNode = schemaNode("city", "string", "城市", true);
        Map<String, Object> streetNode = schemaNode("street", "string", "街道", false);

        Map<String, Object> addressNode = schemaNode("address", "object", "地址", true);
        addressNode.put("children", Arrays.asList(cityNode, streetNode));

        String contract = buildContract("json", Collections.singletonList(addressNode), null);

        Map<String, Object> body = new HashMap<>();
        body.put("address", mapOf("street", "中山路"));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, body, null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                        e.contains("city") || e.contains("不能为空") || e.contains("缺少必填字段")),
                "实际: " + ex.getErrors());
    }

    @Test
    @DisplayName("Body: 嵌套 object 全部字段正确 → 通过")
    void bodyNestedObjectValid() {
        Map<String, Object> cityNode = schemaNode("city", "string", "城市", true);
        Map<String, Object> streetNode = schemaNode("street", "string", "街道", false);

        Map<String, Object> addressNode = schemaNode("address", "object", "地址", true);
        addressNode.put("children", Arrays.asList(cityNode, streetNode));

        String contract = buildContract("json", Collections.singletonList(addressNode), null);

        Map<String, Object> addressData = new HashMap<>();
        addressData.put("city", "上海");
        addressData.put("street", "中山路");
        Map<String, Object> body = new HashMap<>();
        body.put("address", addressData);

        assertDoesNotThrow(() -> service.validateFromContract(contract, body, null));
    }

    // ============================= 7. Body 校验 - 嵌套 array =============================

    @Test
    @DisplayName("Body: array minItems 校验失败")
    void bodyArrayMinItemsFail() {
        Map<String, Object> itemNode = schemaNode("name", "string", "品名", true);

        Map<String, Object> arrayNode = schemaNode("items", "array", "列表", false);
        arrayNode.put("minItems", 2);
        arrayNode.put("children", Collections.singletonList(itemNode));

        String contract = buildContract("json", Collections.singletonList(arrayNode), null);

        Map<String, Object> body = new HashMap<>();
        body.put("items", Collections.singletonList(mapOf("name", "苹果")));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, body, null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("元素数不足")));
    }

    @Test
    @DisplayName("Body: array maxItems 校验失败")
    void bodyArrayMaxItemsFail() {
        Map<String, Object> itemNode = schemaNode("name", "string", "品名", false);

        Map<String, Object> arrayNode = schemaNode("items", "array", "列表", false);
        arrayNode.put("maxItems", 1);
        arrayNode.put("children", Collections.singletonList(itemNode));

        String contract = buildContract("json", Collections.singletonList(arrayNode), null);

        Map<String, Object> body = new HashMap<>();
        body.put("items", Arrays.asList(mapOf("name", "苹果"), mapOf("name", "香蕉")));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, body, null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("超出限制")));
    }

    @Test
    @DisplayName("Body: array 子元素的 required 校验")
    void bodyArrayChildRequiredMissing() {
        Map<String, Object> nameNode = schemaNode("name", "string", "品名", true);
        Map<String, Object> priceNode = schemaNode("price", "number", "价格", true);

        Map<String, Object> arrayNode = schemaNode("items", "array", "列表", false);
        arrayNode.put("children", Arrays.asList(nameNode, priceNode));

        String contract = buildContract("json", Collections.singletonList(arrayNode), null);

        // 第二个元素缺少 price
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "苹果");
        item1.put("price", 5.5);
        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "香蕉");  // 缺 price

        Map<String, Object> body = new HashMap<>();
        body.put("items", Arrays.asList(item1, item2));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, body, null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                        e.contains("price") || e.contains("不能为空") || e.contains("缺少必填字段")),
                "实际: " + ex.getErrors());
    }

    // ============================= 8. Query 校验 =============================

    @Test
    @DisplayName("Query: 必填参数缺失")
    void queryRequiredMissing() {
        String contract = buildContract(null, null,
                Collections.singletonList(schemaNode("page", "string", "页码", true)));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        null,
                        singletonStringMap("size", "10"))
        );
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                        e.contains("page") || e.contains("不能为空") || e.contains("缺少必填字段")),
                "实际: " + ex.getErrors());
    }

    @Test
    @DisplayName("Query: 必填参数存在 → 通过")
    void queryRequiredPresent() {
        String contract = buildContract(null, null,
                Collections.singletonList(schemaNode("page", "string", "页码", true)));

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        null,
                        singletonStringMap("page", "1"))
        );
    }

    @Test
    @DisplayName("Query: pattern 校验失败")
    void queryPatternFail() {
        Map<String, Object> node = schemaNode("code", "string", "编码", false);
        node.put("pattern", "^[A-Z]{3}$");

        String contract = buildContract(null, null, Collections.singletonList(node));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, null, singletonStringMap("code", "ab1"))
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("格式不匹配")));
    }

    @Test
    @DisplayName("Query: minLength 校验失败")
    void queryMinLengthFail() {
        Map<String, Object> node = schemaNode("keyword", "string", "关键词", false);
        node.put("minLength", 2);

        String contract = buildContract(null, null, Collections.singletonList(node));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, null, singletonStringMap("keyword", "a"))
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("长度不足")));
    }

    // ============================= 9. Body + Query 混合校验 =============================

    @Test
    @DisplayName("Body + Query 同时校验失败 → 错误合并")
    void bodyAndQueryBothFail() {
        Map<String, Object> bodyNode = schemaNode("name", "string", "名称", true);
        Map<String, Object> queryNode = schemaNode("page", "string", "页码", true);

        String contract = buildContract("json",
                Collections.singletonList(bodyNode),
                Collections.singletonList(queryNode));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("other", "val"),
                        singletonStringMap("size", "10"))
        );
        // body 和 query 各有一个 required 缺失，至少产生 2 条错误
        assertTrue(ex.getErrors().size() >= 2,
                "应包含至少2个错误: " + ex.getErrors());
        // 每条错误要么包含字段名，要么包含通用 required 提示
        assertTrue(ex.getErrors().stream().allMatch(e ->
                        e.contains("不能为空") || e.contains("缺少必填字段")),
                "实际: " + ex.getErrors());
    }

    @Test
    @DisplayName("Body + Query 全部通过 → 无异常")
    void bodyAndQueryBothPass() {
        Map<String, Object> bodyNode = schemaNode("name", "string", "名称", true);
        Map<String, Object> queryNode = schemaNode("page", "string", "页码", true);

        String contract = buildContract("json",
                Collections.singletonList(bodyNode),
                Collections.singletonList(queryNode));

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("name", "测试"),
                        singletonStringMap("page", "1"))
        );
    }

    // ============================= 10. bodyType 非 json → 跳过 body 校验 =============================

    @ParameterizedTest(name = "bodyType=\"{0}\" 应跳过 Body 校验")
    @ValueSource(strings = {"none", "form", "xml", ""})
    void shouldSkipBodyValidationForNonJsonType(String bodyType) {
        Map<String, Object> bodyNode = schemaNode("name", "string", "名称", true);
        String contract = buildContractWithBodyType(bodyType,
                Collections.singletonList(bodyNode), null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("other", "val"),
                        null)
        );
    }

    // ============================= 11. body / query 均为 null/empty → 空对象校验 =============================

    @Test
    @DisplayName("bodyParams 和 queryParams 均为 null → 仍需校验必填约束")
    void shouldFailWhenBothParamsNullButRequired() {
        String contract = buildContract("json",
                Collections.singletonList(schemaNode("name", "string", "名称", true)),
                Collections.singletonList(schemaNode("page", "string", "页码", true)));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, null, null)
        );
        assertTrue(ex.getErrors().size() >= 2, "应包含 body 和 query 的必填错误");
    }

    @Test
    @DisplayName("bodyParams 为空 Map → 仍需校验必填约束")
    void shouldFailBodyWhenParamsEmptyButRequired() {
        String contract = buildContract("json",
                Collections.singletonList(schemaNode("name", "string", "名称", true)),
                null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, Collections.emptyMap(), null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                e.contains("name") || e.contains("不能为空") || e.contains("缺少必填字段")));
    }

    // ============================= 12. 异常/畸形契约 JSON =============================

    @Test
    @DisplayName("非法 JSON 字符串 → 抛出 SchemaValidationException")
    void invalidJsonContract() {
        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract("{invalid json!!!",
                        mapOf("k", "v"),
                        singletonStringMap("q", "v"))
        );
        assertTrue(ex.getMessage().contains("请求参数格式不正确"));
    }

    // ============================= 13. 多字段组合校验 =============================

    @Test
    @DisplayName("多字段: 部分必填 + 部分可选 + 类型校验")
    void multiFieldMixed() {
        List<Map<String, Object>> bodyNodes = Arrays.asList(
                schemaNode("username", "string", "用户名", true),
                schemaNode("age", "integer", "年龄", false),
                schemaNode("email", "string", "邮箱", false)
        );

        String contract = buildContract("json", bodyNodes, null);

        Map<String, Object> body = new HashMap<>();
        body.put("username", "admin");
        body.put("age", 30);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract, body, null)
        );
    }

    @Test
    @DisplayName("多字段: required 缺失 + type 错误 → 合并多条错误")
    void multiFieldMultipleErrors() {
        List<Map<String, Object>> bodyNodes = Arrays.asList(
                schemaNode("username", "string", "用户名", true),
                schemaNode("age", "integer", "年龄", false)
        );

        String contract = buildContract("json", bodyNodes, null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("age", "not_number"),
                        null)
        );
        assertTrue(ex.getErrors().size() >= 2,
                "应包含至少2个错误: " + ex.getErrors());
    }

    // ============================= 14. 深层嵌套: object → array → object =============================

    @Test
    @DisplayName("深层嵌套: object → array → object 子元素校验")
    void deepNestedValidation() {
        Map<String, Object> skuNode = schemaNode("sku", "string", "SKU", true);
        Map<String, Object> qtyNode = schemaNode("qty", "integer", "数量", false);

        Map<String, Object> itemsNode = schemaNode("items", "array", "商品列表", true);
        itemsNode.put("children", Arrays.asList(skuNode, qtyNode));
        itemsNode.put("minItems", 1);

        Map<String, Object> orderNode = schemaNode("order", "object", "订单", true);
        orderNode.put("children", Collections.singletonList(itemsNode));

        String contract = buildContract("json", Collections.singletonList(orderNode), null);

        Map<String, Object> item1 = new HashMap<>();
        item1.put("sku", "PROD-001");
        item1.put("qty", 2);
        Map<String, Object> item2 = new HashMap<>();
        item2.put("sku", "PROD-002");
        item2.put("qty", 1);

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("items", Arrays.asList(item1, item2));

        Map<String, Object> body = new HashMap<>();
        body.put("order", orderData);

        assertDoesNotThrow(() -> service.validateFromContract(contract, body, null));
    }

    @Test
    @DisplayName("深层嵌套: array 子元素缺少必填字段 → 校验失败")
    void deepNestedValidationFail() {
        Map<String, Object> skuNode = schemaNode("sku", "string", "SKU", true);
        Map<String, Object> qtyNode = schemaNode("qty", "integer", "数量", false);

        Map<String, Object> itemsNode = schemaNode("items", "array", "商品列表", true);
        itemsNode.put("children", Arrays.asList(skuNode, qtyNode));

        Map<String, Object> orderNode = schemaNode("order", "object", "订单", true);
        orderNode.put("children", Collections.singletonList(itemsNode));

        String contract = buildContract("json", Collections.singletonList(orderNode), null);

        Map<String, Object> item1 = new HashMap<>();
        item1.put("qty", 2);  // 缺少 sku

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("items", Collections.singletonList(item1));

        Map<String, Object> body = new HashMap<>();
        body.put("order", orderData);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, body, null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                        e.contains("sku") || e.contains("不能为空") || e.contains("缺少必填字段")),
                "实际: " + ex.getErrors());
    }

    // ============================= 15. SchemaNode 字段名为空 → 跳过 =============================

    @Test
    @DisplayName("SchemaNode fieldName 为空 → 该节点被忽略")
    void emptyFieldNameSkipped() {
        List<Map<String, Object>> bodyNodes = Arrays.asList(
                schemaNode("", "string", "空名称", true),
                schemaNode("name", "string", "名称", false)
        );

        String contract = buildContract("json", bodyNodes, null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("name", "test"),
                        null)
        );
    }

    // ============================= 16. exclusiveMinimum / exclusiveMaximum =============================

    @Test
    @DisplayName("Body: exclusiveMinimum 校验 (值等于下限 → 失败)")
    void bodyExclusiveMinimumFail() {
        Map<String, Object> node = schemaNode("score", "number", "分数", false);
        node.put("minimum", 0);
        node.put("exclusiveMinimum", true);

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("score", 0),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                        e.contains("小于最小值") || e.contains("exclusiveMinimum")),
                "错误列表: " + ex.getErrors());
    }

    @Test
    @DisplayName("Body: 纯数字 exclusiveMinimum 校验 (值等于下限 → 失败)")
    void bodyNumericExclusiveMinimumFail() {
        Map<String, Object> node = schemaNode("score", "number", "分数", false);
        node.put("exclusiveMinimum", 0);

        String contract = buildContract("json", Collections.singletonList(node), null);

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract,
                        mapOf("score", 0),
                        null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e ->
                        e.contains("小于最小值")),
                "错误列表: " + ex.getErrors());
    }

    // ============================= 17. uniqueItems =============================

    @Test
    @DisplayName("Body: array uniqueItems 校验失败（有重复元素）")
    void bodyArrayUniqueItemsFail() {
        Map<String, Object> arrayNode = schemaNode("tags", "array", "标签", false);
        arrayNode.put("uniqueItems", true);

        String contract = buildContract("json", Collections.singletonList(arrayNode), null);

        Map<String, Object> body = new HashMap<>();
        body.put("tags", Arrays.asList("java", "python", "java"));

        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contract, body, null)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("重复元素")));
    }

    // ============================= 18. 中文错误消息覆盖率 =============================

    @ParameterizedTest(name = "中文消息: {0}")
    @MethodSource("chineseMessageProvider")
    void chineseErrorMessages(String scenario, String contractJson,
                              Map<String, Object> body, Map<String, String> query,
                              String expectedSubstring) {
        SchemaValidationException ex = assertThrows(SchemaValidationException.class, () ->
                service.validateFromContract(contractJson, body, query)
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains(expectedSubstring)),
                "期望包含「" + expectedSubstring + "」, 实际: " + ex.getErrors());
    }

    static Stream<Arguments> chineseMessageProvider() {
        return Stream.of(
                arguments("required → 字段不能为空",
                        buildContractStatic("json",
                                Collections.singletonList(schemaNodeStatic("name", "string", "名称", true)),
                                null),
                        mapOfStatic("other", "v"), null, "不能为空"),

                arguments("type → 类型不匹配",
                        buildContractStatic("json",
                                Collections.singletonList(schemaNodeStatic("count", "integer", "数量", false)),
                                null),
                        mapOfStatic("count", "abc"), null, "类型不匹配"),

                arguments("minLength → 长度不足",
                        buildContractStatic("json",
                                Collections.singletonList(withConstraint(
                                        schemaNodeStatic("code", "string", "编码", false),
                                        "minLength", 5)),
                                null),
                        mapOfStatic("code", "ab"), null, "长度不足"),

                arguments("maxLength → 超出最大长度",
                        buildContractStatic("json",
                                Collections.singletonList(withConstraint(
                                        schemaNodeStatic("code", "string", "编码", false),
                                        "maxLength", 3)),
                                null),
                        mapOfStatic("code", "abcdef"), null, "最大长度限制"),

                arguments("minimum → 小于最小值",
                        buildContractStatic("json",
                                Collections.singletonList(withConstraint(
                                        schemaNodeStatic("val", "number", "数值", false),
                                        "minimum", 10)),
                                null),
                        mapOfStatic("val", 5), null, "小于最小值"),

                arguments("maximum → 超出最大值",
                        buildContractStatic("json",
                                Collections.singletonList(withConstraint(
                                        schemaNodeStatic("val", "number", "数值", false),
                                        "maximum", 100)),
                                null),
                        mapOfStatic("val", 150), null, "最大值限制")
        );
    }

    // ============================= 19. body 节点为空数组 → 跳过 =============================

    @Test
    @DisplayName("body 节点为空数组 → 跳过 body 校验")
    void emptyBodyNodesArraySkipped() {
        String contract = buildContract("json", Collections.emptyList(), null);
        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("name", "test"),
                        null)
        );
    }

    // ============================= 20. number 和 string 类型的边界值 =============================

    @Test
    @DisplayName("number 在 minimum 边界值上 → 通过")
    void numberExactlyAtMinimum() {
        Map<String, Object> node = schemaNode("val", "number", "数值", false);
        node.put("minimum", 0);

        String contract = buildContract("json", Collections.singletonList(node), null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("val", 0),
                        null)
        );
    }

    @Test
    @DisplayName("string 长度恰好等于 minLength → 通过")
    void stringExactlyAtMinLength() {
        Map<String, Object> node = schemaNode("code", "string", "编码", false);
        node.put("minLength", 3);

        String contract = buildContract("json", Collections.singletonList(node), null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("code", "abc"),
                        null)
        );
    }

    @Test
    @DisplayName("string 长度恰好等于 maxLength → 通过")
    void stringExactlyAtMaxLength() {
        Map<String, Object> node = schemaNode("code", "string", "编码", false);
        node.put("maxLength", 3);

        String contract = buildContract("json", Collections.singletonList(node), null);

        assertDoesNotThrow(() ->
                service.validateFromContract(contract,
                        mapOf("code", "abc"),
                        null)
        );
    }

    // ============================= 辅助方法 =============================

    /** 构造 SchemaNode Map（模拟前端传入的节点，字段名与前端 TS 类型一致） */
    private static Map<String, Object> schemaNode(String name, String type,
                                                   String description, boolean required) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", UUID.randomUUID().toString());
        node.put("name", name);
        node.put("type", type);
        node.put("description", description);
        node.put("required", required);
        return node;
    }

    /** 静态版本，用于 @MethodSource */
    private static Map<String, Object> schemaNodeStatic(String name, String type,
                                                         String description, boolean required) {
        return schemaNode(name, type, description, required);
    }

    /** 为 schemaNode 添加约束字段 */
    private static Map<String, Object> withConstraint(Map<String, Object> node,
                                                       String key, Object value) {
        node.put(key, value);
        return node;
    }

    /** Java 8 兼容的 Map 构造 */
    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> mapOf(String k, V v) {
        Map<String, V> map = new HashMap<>();
        map.put(k, v);
        return map;
    }

    /** 用于 static @MethodSource */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOfStatic(String k, Object v) {
        Map<String, Object> map = new HashMap<>();
        map.put(k, v);
        return map;
    }

    /** Java 8 兼容的 Map<String,String> 构造 */
    private static Map<String, String> singletonStringMap(String k, String v) {
        Map<String, String> map = new HashMap<>();
        map.put(k, v);
        return map;
    }

    /** 构建完整的契约 JSON 字符串 */
    private static String buildContract(String bodyType,
                                         List<Map<String, Object>> bodyNodes,
                                         List<Map<String, Object>> queryNodes) {
        return buildContractStatic(bodyType, bodyNodes, queryNodes);
    }

    private static String buildContractStatic(String bodyType,
                                               List<Map<String, Object>> bodyNodes,
                                               List<Map<String, Object>> queryNodes) {
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> request = new LinkedHashMap<>();

            if (bodyType != null) {
                request.put("bodyType", bodyType);
            }
            if (bodyNodes != null) {
                request.put("body", bodyNodes);
            }
            if (queryNodes != null) {
                request.put("query", queryNodes);
            }

            Map<String, Object> contract = new LinkedHashMap<>();
            contract.put("request", request);
            return om.writeValueAsString(contract);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build contract JSON", e);
        }
    }

    private static String buildContractWithBodyType(String bodyType,
                                                     List<Map<String, Object>> bodyNodes,
                                                     List<Map<String, Object>> queryNodes) {
        return buildContractStatic(bodyType, bodyNodes, queryNodes);
    }
}
