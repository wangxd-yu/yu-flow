package org.yu.flow.module.api.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.SchemaValidatorService;
import org.yu.flow.dto.PageBean;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowDbDebugRequestDTO;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.datasource.service.DynamicDataSourceService.DataSourceCallback;
import org.yu.flow.module.datasource.wall.DataSourceWallGuard;
import org.yu.flow.module.log.execution.domain.FlowExecutionLogDO;
import org.yu.flow.module.log.execution.service.FlowExecutionLogService;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口运行时（JSON / STRING / DB 三种实现、执行日志与计量、DB 调试运行）的现有行为。
 * DB 模式的 SQL 在 H2 内存库上真实执行。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowApiRuntimeCharacterizationTest {

    @Mock private DynamicDataSourceService dynamicDataSourceService;
    @Mock private DataSourceWallGuard dataSourceWallGuard;
    @Mock private DemoModeGuard demoModeGuard;
    @Mock private FlowExecutionLogService flowExecutionLogService;
    @Mock private SchemaValidatorService schemaValidatorService;
    @Mock private YuFlowRuntimeSettings yuFlowRuntimeSettings;
    @Mock private AssetMetricsRecorder assetMetricsRecorder;
    @Spy private ContractParamTypeConverter contractParamTypeConverter = new ContractParamTypeConverter();

    @InjectMocks
    private FlowApiServiceImpl service;

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:api_runtime_" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE t_order (id INT PRIMARY KEY, order_no VARCHAR(32), amount INT)");
        for (int i = 1; i <= 25; i++) {
            jdbc.update("INSERT INTO t_order VALUES (?, ?, ?)", i, "NO-" + i, i * 10);
        }
        when(dynamicDataSourceService.execute(anyString(), any())).thenAnswer(inv -> run(inv.getArgument(1)));
        when(dynamicDataSourceService.executeInTransaction(anyString(), any(DataSourceCallback.class)))
                .thenAnswer(inv -> tx.execute(status -> run(inv.getArgument(1))));
        when(dynamicDataSourceService.executeInTransactionThenRollback(anyString(), any())).thenAnswer(inv ->
                tx.execute(status -> {
                    Object result = run(inv.getArgument(1));
                    status.setRollbackOnly();
                    return result;
                }));
        when(yuFlowRuntimeSettings.getEngineDefaultLogMode()).thenReturn("ALL");
        service.initStrategies();
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("SHUTDOWN");
    }

    @SuppressWarnings("unchecked")
    private Object run(Object callback) {
        return ((DataSourceCallback<Object>) callback).doInDataSource(jdbc);
    }

    // ─── 静态内容 ─────────────────────────────────────────────────────────

    @Test
    void jsonApiReturnsParsedContentAndRecordsSuccess() throws Exception {
        FlowApiDO api = api("JSON").setJsonContent("{\"a\":1}");

        Object result = service.executeApi(api, Map.of("q", "1"), Map.of(), new HashMap<>(), PageRequest.of(0, 10), null);

        assertEquals(Map.of("a", 1), result);
        FlowExecutionLogDO log = savedLog();
        assertEquals("SUCCESS", log.getStatus());
        assertEquals("{\"a\":1}", log.getResponseBody());
        assertTrue(log.getRequestParams().contains("\"q\":\"1\""), log.getRequestParams());
        assertTrue(log.getTraceData().contains("json_node"), "非流程类型合成一步轨迹");
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api-1"), eq(MetricsOutcome.SUCCESS), anyLong());
    }

    @Test
    void stringApiParsesJsonTextOtherwiseReturnsRaw() throws Exception {
        assertEquals("hello", service.executeApi(api("STRING").setTextContent("hello"), Map.of(), PageRequest.of(0, 1), null));
        assertEquals(Map.of("x", 2),
                service.executeApi(api("STRING").setTextContent("{\"x\":2}"), Map.of(), PageRequest.of(0, 1), null));
    }

    @Test
    void publishedSnapshotContentWinsOverDraft() throws Exception {
        FlowApiDO api = api("JSON").setJsonContent("{\"v\":\"draft\"}")
                .setPublishStatus(1)
                .setPublishedSnapshot("{\"serviceType\":\"JSON\",\"jsonContent\":\"{\\\"v\\\":\\\"live\\\"}\"}");

        assertEquals(Map.of("v", "live"), service.executeApi(api, Map.of(), PageRequest.of(0, 1), null));
    }

    @Test
    void failureIsRecordedWithErrorTraceAndRethrown() {
        FlowApiDO api = api("JSON").setJsonContent("not json");

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.executeApi(api, Map.of(), Map.of(), new HashMap<>(), PageRequest.of(0, 1), null));

        assertEquals("JSON parsing error", e.getMessage());
        FlowExecutionLogDO log = savedLog();
        assertEquals("ERROR", log.getStatus());
        assertEquals("JSON parsing error", log.getErrorMsg());
        assertTrue(log.getTraceData().contains("\"status\":\"error\""), log.getTraceData());
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api-1"), eq(MetricsOutcome.FAIL), anyLong());
    }

    @Test
    void errorOnlyModeSkipsSuccessLog() throws Exception {
        FlowApiDO api = api("JSON").setJsonContent("{}").setLogMode("ERROR_ONLY");

        service.executeApi(api, Map.of(), PageRequest.of(0, 1), null);

        verify(flowExecutionLogService, never()).saveLogAsync(any());
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api-1"), eq(MetricsOutcome.SUCCESS), anyLong());
    }

    @Test
    void unknownServiceTypeIsRejected() {
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> service.executeApi(api("XML"), Map.of(), PageRequest.of(0, 1), null));
        assertEquals("接口配置错误，未知的 serviceType: XML", e.getMessage());
    }

    // ─── 流程编排 ─────────────────────────────────────────────────────────

    @Test
    void flowWithTraceUnwrapsResponseBodyAndPersistsTrace() throws Exception {
        FlowApiDO api = flow("'Hello ' + 'World'");

        Object result = service.executeApi(api, Map.of(), Map.of(), new HashMap<>(), PageRequest.of(0, 1), null);

        assertEquals("Hello World", result);
        FlowExecutionLogDO log = savedLog();
        assertEquals("SUCCESS", log.getStatus());
        assertEquals("\"Hello World\"", log.getResponseBody());
        assertTrue(log.getTraceData().contains("node_calc"), "流程真实轨迹落库");
    }

    @Test
    void flowWithoutTraceUnwrapsExecutionResult() throws Exception {
        FlowApiDO api = flow("'Hello ' + 'World'").setLogMode("ERROR_ONLY");

        assertEquals("Hello World", service.executeApi(api, Map.of(), PageRequest.of(0, 1), null));
        verify(flowExecutionLogService, never()).saveLogAsync(any());
    }

    @Test
    void flowFailureWithTraceThrows() {
        FlowApiDO api = flow("1/0");

        assertThrows(RuntimeException.class,
                () -> service.executeApi(api, Map.of(), Map.of(), new HashMap<>(), PageRequest.of(0, 1), null));

        FlowExecutionLogDO log = savedLog();
        assertEquals("ERROR", log.getStatus());
        assertTrue(log.getTraceData().contains("node_calc"), "失败时保留引擎轨迹而不是合成轨迹");
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api-1"), eq(MetricsOutcome.FAIL), anyLong());
    }

    @Test
    void flowFailureWithoutTraceReturnsFailedR() throws Exception {
        FlowApiDO api = flow("1/0").setLogMode("ERROR_ONLY");

        Object result = service.executeApi(api, Map.of(), PageRequest.of(0, 1), null);

        assertTrue(result instanceof org.yu.flow.dto.R<?> r && Boolean.FALSE.equals(r.getOk()), String.valueOf(result));
        FlowExecutionLogDO log = savedLog();
        assertEquals("ERROR", log.getStatus());
        verify(assetMetricsRecorder).record(eq(MetricsAssetType.API), eq("api-1"), eq(MetricsOutcome.FAIL), anyLong());
    }

    // ─── 数据库 ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void dbPageQueryCountsSortsAndPages() throws Exception {
        FlowApiDO api = db("SELECT id, order_no FROM t_order WHERE amount >= ${min}", "PAGE");

        Object result = service.executeApi(api, Map.of("min", "0"), Map.of(), new HashMap<>(),
                PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "id")), null);

        PageBean<Map<String, Object>> page = (PageBean<Map<String, Object>>) result;
        assertEquals(25L, page.getTotal());
        assertEquals(3, page.getPages());
        assertEquals(10, page.getItems().size());
        assertEquals("15", page.getItems().get(0).get("id"), "数字列按字符串返回");
        assertEquals("NO-15", page.getItems().get(0).get("orderNo"));
        String actualSql = savedLog().getTraceData();
        assertTrue(actualSql.contains("COUNT SQL") && actualSql.contains("LIMIT 10 OFFSET 10"), actualSql);
        verify(dataSourceWallGuard).assertSqlAllowed(eq("ds1"), anyString());
    }

    @Test
    void emptyPageShortCircuits() throws Exception {
        FlowApiDO api = db("SELECT id FROM t_order WHERE amount > ${min}", "PAGE");

        PageBean<?> page = (PageBean<?>) service.executeApi(api, Map.of("min", "100000"),
                PageRequest.of(0, 10), null);

        assertEquals(0L, page.getTotal());
        assertTrue(page.getItems().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dbListAndObjectQueries() throws Exception {
        List<Map<String, Object>> list = (List<Map<String, Object>>) service.executeApi(
                db("SELECT id FROM t_order ORDER BY id", "LIST"), Map.of(),
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id")), null);
        assertEquals(25, list.size(), "LIST 不分页");
        assertEquals("25", list.get(0).get("id"), "按 pageable 的排序替换原 ORDER BY");

        Map<String, Object> row = (Map<String, Object>) service.executeApi(
                db("SELECT order_no FROM t_order WHERE id = ${id}", "OBJECT"), Map.of("id", "7"),
                PageRequest.of(0, 1), null);
        assertEquals("NO-7", row.get("orderNo"));

        assertNull(service.executeApi(db("SELECT id FROM t_order WHERE id = ${id}", "OBJECT"),
                Map.of("id", "999"), PageRequest.of(0, 1), null));
    }

    @Test
    void dbWritesGoThroughDemoGuard() throws Exception {
        Object updated = service.executeApi(db("UPDATE t_order SET amount = 0 WHERE id <= ${max}", "UPDATE"),
                Map.of("max", "3"), PageRequest.of(0, 1), null);
        assertEquals(3, updated);
        verify(demoModeGuard).checkSqlWrite("UPDATE/DELETE");

        Object inserted = service.executeApi(db("INSERT INTO t_order VALUES (${id}, ${no}, 1)", "INSERT"),
                Map.of("id", "100", "no", "X"), PageRequest.of(0, 1), null);
        assertEquals(1, inserted);
        verify(demoModeGuard).checkSqlWrite("INSERT");
    }

    @Test
    void unknownResponseTypeIsRejected() {
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> service.executeApi(db("SELECT 1", "TREE"), Map.of(), PageRequest.of(0, 1), null));
        assertEquals("业务异常：未知的 responseType: TREE", e.getMessage());
    }

    @Test
    void batchInsertSplitsWhenDynamicSqlChangesShape() {
        int count = service.executeBatchInsert("ds1", "INSERT INTO t_order VALUES (${id}, ${no}, ${amount})",
                List.of(Map.of("id", 201, "no", "B1", "amount", 1), Map.of("id", 202, "no", "B2", "amount", 2)));

        assertEquals(2, count);
        assertEquals(27, jdbc.queryForObject("SELECT COUNT(*) FROM t_order", Integer.class));
        assertEquals(0, service.executeBatchInsert("ds1", "INSERT INTO t_order VALUES (${id})", List.of()));
    }

    // ─── DB 调试运行 ──────────────────────────────────────────────────────

    @Test
    void debugRunValidatesInput() {
        FlowDbDebugRequestDTO request = new FlowDbDebugRequestDTO();
        FlowTrace noSql = service.debugRunDb(request);
        assertEquals("error", noSql.getStatus());
        assertEquals("SQL 内容不能为空", noSql.getErrorMsg());

        request.setSqlContent("SELECT 1");
        assertEquals("请选择数据源", service.debugRunDb(request).getErrorMsg());
        request.setDatasource("ds1");
        assertEquals("请选择响应类型", service.debugRunDb(request).getErrorMsg());
    }

    @Test
    void debugRunRollsBackWritesByDefault() {
        FlowDbDebugRequestDTO request = new FlowDbDebugRequestDTO();
        request.setSqlContent("UPDATE t_order SET amount = -1 WHERE id = ${id}");
        request.setDatasource("ds1");
        request.setResponseType("UPDATE");
        request.setQueryParams(Map.of("id", "5"));

        FlowTrace trace = service.debugRunDb(request);

        assertEquals("success", trace.getStatus());
        assertEquals(1, trace.getGlobalOutputs());
        assertEquals("已开启事务回退：本次调试中的写操作不会提交到数据库",
                ((Map<?, ?>) trace.getStepLogs().get(0).getOutputs()).get("transactionNote"));
        assertEquals(50, jdbc.queryForObject("SELECT amount FROM t_order WHERE id = 5", Integer.class));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private static FlowApiDO api(String serviceType) {
        return new FlowApiDO().setId("api-1").setName("测试接口").setUrl("/t").setMethod("GET")
                .setServiceType(serviceType).setPublishStatus(0);
    }

    private static FlowApiDO db(String sql, String responseType) {
        return api("DB").setSqlContent(sql).setDatasource("ds1").setResponseType(responseType);
    }

    private static FlowApiDO flow(String expression) {
        String dsl = "{\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"request\",\"ports\":[{\"id\":\"params\"}]},"
                + "{\"id\":\"node_calc\",\"type\":\"evaluate\",\"ports\":[{\"id\":\"in\"},{\"id\":\"out\"}],"
                + "\"data\":{\"expression\":\"" + expression + "\"}},"
                + "{\"id\":\"end\",\"type\":\"response\",\"ports\":[{\"id\":\"in\"}],"
                + "\"data\":{\"inputs\":{\"node_calc\":\"$.node_calc\"},\"body\":\"${node_calc.out}\"}}],"
                + "\"edges\":["
                + "{\"source\":{\"cell\":\"start\",\"port\":\"params\"},\"target\":{\"cell\":\"node_calc\",\"port\":\"in\"}},"
                + "{\"source\":{\"cell\":\"node_calc\",\"port\":\"out\"},\"target\":{\"cell\":\"end\",\"port\":\"in\"}}]}";
        return api("FLOW").setDslContent(dsl);
    }

    private FlowExecutionLogDO savedLog() {
        ArgumentCaptor<FlowExecutionLogDO> captor = ArgumentCaptor.forClass(FlowExecutionLogDO.class);
        verify(flowExecutionLogService).saveLogAsync(captor.capture());
        return captor.getValue();
    }
}
