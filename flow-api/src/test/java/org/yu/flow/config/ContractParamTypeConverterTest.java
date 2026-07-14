package org.yu.flow.config;

import org.junit.jupiter.api.Test;
import org.yu.flow.auto.druid.DynamicSqlParser;
import org.yu.flow.auto.dto.SqlAndParams;
import org.yu.flow.exception.SchemaValidationException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractParamTypeConverterTest {

    private final ContractParamTypeConverter converter = new ContractParamTypeConverter();

    @Test
    void shouldConvertCommonJsonSchemaTypesAndFormats() {
        String contract = contract("""
                [
                  {"name":"size","type":"integer","description":"分页大小"},
                  {"name":"price","type":"number"},
                  {"name":"enabled","type":"boolean"},
                  {"name":"day","type":"string","format":"date"},
                  {"name":"at","type":"string","format":"date-time"},
                  {"name":"clock","type":"string","format":"time"},
                  {"name":"requestId","type":"string","format":"uuid"},
                  {"name":"empty","type":"null"}
                ]
                """);
        UUID uuid = UUID.randomUUID();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("size", "20");
        source.put("price", "12.50");
        source.put("enabled", "true");
        source.put("day", "2026-07-14");
        source.put("at", "2026-07-14T10:30:00");
        source.put("clock", "10:30:00");
        source.put("requestId", uuid.toString());
        source.put("empty", "null");

        Map<String, Object> result = converter.convertSection(contract, "query", source);

        assertEquals(20L, result.get("size"));
        assertEquals(new BigDecimal("12.50"), result.get("price"));
        assertEquals(Boolean.TRUE, result.get("enabled"));
        assertInstanceOf(Date.class, result.get("day"));
        assertInstanceOf(Timestamp.class, result.get("at"));
        assertInstanceOf(Time.class, result.get("clock"));
        assertEquals(uuid, result.get("requestId"));
        assertNull(result.get("empty"));
    }

    @Test
    void shouldConvertArrayItemsAndNestedBody() {
        String contract = """
                {
                  "request": {
                    "body": [
                      {
                        "name": "filter",
                        "type": "object",
                        "children": [
                          {"name":"ids","type":"array","children":[{"name":"items","type":"integer"}]},
                          {"name":"active","type":"boolean"}
                        ]
                      }
                    ]
                  }
                }
                """;
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("ids", List.of("1", "2"));
        filter.put("active", "false");

        Map<String, Object> result = converter.convertSection(
                contract, "body", Map.of("filter", filter));

        Map<?, ?> convertedFilter = (Map<?, ?>) result.get("filter");
        assertEquals(List.of(1L, 2L), convertedFilter.get("ids"));
        assertEquals(Boolean.FALSE, convertedFilter.get("active"));
    }

    @Test
    void shouldBindIntegerAsNumericLimitParameter() {
        String contract = contract("[{\"name\":\"size\",\"type\":\"integer\"}]");
        Map<String, Object> params = converter.convertSection(
                contract, "query", Map.of("size", "10"));

        assertDoesNotThrow(() ->
                new SchemaValidatorService().validateFromContract(contract, Map.of(), params));

        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "SELECT * FROM water_record ORDER BY water_depth DESC, seq_no ASC LIMIT ${size}",
                params);

        assertEquals(List.of(10L), result.getParams());
    }

    @Test
    void shouldConvertAndBindQueryIntegerArray() {
        String contract = contract("""
                [
                  {
                    "name":"ids",
                    "type":"array",
                    "children":[{"name":"items","type":"integer"}]
                  }
                ]
                """);
        Map<String, Object> params = converter.convertSection(
                contract, "query", Map.of("ids", "1,2,3"));

        assertDoesNotThrow(() ->
                new SchemaValidatorService().validateFromContract(contract, Map.of(), params));
        SqlAndParams result = DynamicSqlParser.parseDynamicSqlToPrepared(
                "SELECT * FROM flood_camera WHERE id IN (${ids})", params);

        assertEquals(List.of(1L, 2L, 3L), result.getParams());
    }

    @Test
    void shouldRejectInvalidTypedValue() {
        String contract = contract("[{\"name\":\"size\",\"type\":\"integer\",\"description\":\"分页大小\"}]");

        SchemaValidationException error = assertThrows(SchemaValidationException.class,
                () -> converter.convertSection(contract, "query", Map.of("size", "abc")));

        assertEquals("参数 分页大小（query.size） 无法转换为 integer: abc", error.getMessage());
    }

    private String contract(String queryNodes) {
        return "{\"request\":{\"query\":" + queryNodes + "}}";
    }
}
