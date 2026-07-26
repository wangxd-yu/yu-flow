package org.yu.flow.module.api.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yu.flow.module.api.domain.FlowApiDO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenApiSpecBuilder 单测（Phase 3.3）。
 * 覆盖：空列表、GET/POST API 规范生成、契约解析、OPEN 安全模式。
 */
@DisplayName("Phase 3.3 OpenApiSpecBuilder 单测")
class OpenApiSpecBuilderTest {

    private OpenApiSpecBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new OpenApiSpecBuilder();
    }

    @Test
    @DisplayName("空 API 列表 → 生成合法 OpenAPI 骨架")
    void emptyApiList_producesValidSkeleton() {
        ObjectNode spec = builder.build(List.of(), "http://localhost:11281/flow", "My API", "2.0.0");

        assertEquals("3.0.3", spec.get("openapi").asText());
        assertEquals("My API", spec.at("/info/title").asText());
        assertEquals("2.0.0", spec.at("/info/version").asText());
        assertTrue(spec.has("paths"));
        assertTrue(spec.has("components"));
        assertTrue(spec.at("/components/securitySchemes/bearerAuth").has("type"));
    }

    @Test
    @DisplayName("GET API → 无 requestBody，生成 parameters 区块")
    void getApi_generatesParameters() {
        FlowApiDO api = FlowApiDO.builder()
                .id("api-001")
                .name("用户查询")
                .url("/api/user/list")
                .method("GET")
                .publishStatus(1)
                .responseType("LIST")
                .contract("[{\"name\":\"keyword\",\"label\":\"关键词\",\"type\":\"string\"}]")
                .build();

        ObjectNode spec = builder.build(List.of(api), "http://localhost/flow", "Test", "1.0");

        ObjectNode paths = (ObjectNode) spec.get("paths");
        assertTrue(paths.has("/api/user/list"), "路径应以 / 开头");
        var params = paths.get("/api/user/list").get("get").get("parameters");
        assertTrue(params.isArray());
        assertEquals("keyword", params.get(0).get("name").asText());
        assertEquals("query", params.get(0).get("in").asText());
    }

    @Test
    @DisplayName("POST API → 生成 requestBody + JSON schema")
    void postApi_generatesRequestBody() {
        FlowApiDO api = FlowApiDO.builder()
                .id("api-002")
                .name("创建订单")
                .url("/api/order/create")
                .method("POST")
                .publishStatus(1)
                .responseType("OBJECT")
                .contract("[{\"name\":\"orderId\",\"label\":\"订单ID\",\"type\":\"string\"}," +
                          "{\"name\":\"amount\",\"label\":\"金额\",\"type\":\"number\"}]")
                .build();

        ObjectNode spec = builder.build(List.of(api), "http://localhost/flow", "Test", "1.0");

        ObjectNode paths = (ObjectNode) spec.get("paths");
        assertTrue(paths.has("/api/order/create"));
        var postNode = paths.get("/api/order/create").get("post");
        assertTrue(postNode.has("requestBody"));
        var props = postNode.get("requestBody").get("content").get("application/json").get("schema").get("properties");
        assertTrue(props.has("orderId"));
        assertTrue(props.has("amount"));
    }

    @Test
    @DisplayName("OPEN 鉴权模式 → 不加 security 字段")
    void openAuthApi_hasNoSecurityRequirement() {
        FlowApiDO api = FlowApiDO.builder()
                .id("api-003")
                .name("公开接口")
                .url("/api/public/info")
                .method("GET")
                .publishStatus(1)
                .securityConfig("{\"authMode\":\"OPEN\"}")
                .build();

        ObjectNode spec = builder.build(List.of(api), "http://localhost/flow", "Test", "1.0");
        ObjectNode paths = (ObjectNode) spec.get("paths");
        assertFalse(paths.get("/api/public/info").get("get").has("security"),
                "OPEN 鉴权接口不应生成 security 要求");
    }

    @Test
    @DisplayName("多个 API → 所有路径存在于 paths 中")
    void multipleApis_allPathsPresent() {
        FlowApiDO api1 = FlowApiDO.builder()
                .id("a1").name("A1").url("/a1").method("GET").publishStatus(1).build();
        FlowApiDO api2 = FlowApiDO.builder()
                .id("a2").name("A2").url("/a2").method("POST").publishStatus(1).build();
        FlowApiDO api3 = FlowApiDO.builder()
                .id("a3").name("A3").url("/a3").method("DELETE").publishStatus(1).build();

        ObjectNode spec = builder.build(List.of(api1, api2, api3), "http://localhost", "T", "1");

        ObjectNode paths = (ObjectNode) spec.get("paths");
        assertTrue(paths.has("/a1"));
        assertTrue(paths.has("/a2"));
        assertTrue(paths.has("/a3"));
    }

    @Test
    @DisplayName("url 不以 / 开头 → 自动补全为 /url")
    void urlWithoutLeadingSlash_getsFixed() {
        FlowApiDO api = FlowApiDO.builder()
                .id("a1").name("A").url("no-slash").method("GET").publishStatus(1).build();

        ObjectNode spec = builder.build(List.of(api), "http://localhost", "T", "1");
        ObjectNode paths = (ObjectNode) spec.get("paths");
        assertTrue(paths.has("/no-slash"), "应自动添加前导斜线");
        assertFalse(paths.has("no-slash"), "原始无斜线 key 不应存在");
    }

    @Test
    @DisplayName("NULL api列表 → 不抛出异常，生成空 paths")
    void nullApiList_doesNotThrow() {
        assertDoesNotThrow(() -> {
            ObjectNode spec = builder.build(null, "http://localhost", "T", "1");
            assertTrue(spec.at("/paths").isObject());
        });
    }

    @Test
    @DisplayName("tags 多值（逗号分隔）→ 解析为数组")
    void commaSeparatedTags_parsedAsArray() {
        FlowApiDO api = FlowApiDO.builder()
                .id("a1").name("A").url("/a").method("GET")
                .publishStatus(1).tags("用户管理,权限")
                .build();

        ObjectNode spec = builder.build(List.of(api), "http://localhost", "T", "1");
        ObjectNode paths = (ObjectNode) spec.get("paths");
        var tags = paths.get("/a").get("get").get("tags");
        assertTrue(tags.isArray());
        assertEquals(2, tags.size());
    }
}
