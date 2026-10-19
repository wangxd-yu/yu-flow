package org.yu.flow.module.api.service.impl;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.dataview.ApiDataPreviewer;
import org.yu.flow.module.api.dataview.ApiDataViewSupport;
import org.yu.flow.module.api.dataview.ApiExcelExportLinks;
import org.yu.flow.module.api.dataview.ApiExcelExporter;
import org.yu.flow.module.api.dataview.ApiExcelTemplateManager;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;
import org.yu.flow.module.api.dto.ApiDataPreviewRequestDTO;
import org.yu.flow.module.api.dto.ApiDataPreviewResultDTO;
import org.yu.flow.module.api.dto.ApiExcelExportLinkDTO;
import org.yu.flow.module.api.dto.ApiExcelExportLinkRequestDTO;
import org.yu.flow.module.api.dto.ApiExcelTemplateMetaDTO;
import org.yu.flow.module.api.dto.ViewExportColumnDTO;
import org.yu.flow.module.api.privacy.EffectivePrivacy;
import org.yu.flow.module.api.privacy.PrivacyDecision;
import org.yu.flow.module.api.privacy.PrivacyFieldInterceptor;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.runtime.ApiSqlExecutor;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.datasource.service.DynamicDataSourceService.DataSourceCallback;
import org.yu.flow.module.datasource.wall.DataSourceWallGuard;
import org.yu.flow.module.host.FlowHostPrincipalProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口数据查看（预览）、Excel 导出（动态表头 / 模板 / 对外下载 / 短期下载链）与模板管理的现有行为。SQL 在 H2 上真实执行。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiDataViewCharacterizationTest {

    private static final String SQL = "SELECT id, order_no, amount FROM t_order WHERE amount >= ${min} ORDER BY id";
    private static final String CONTRACT = "{\"responses\":{\"200\":{\"body\":[{\"name\":\"items\",\"type\":\"array\","
            + "\"children\":[{\"name\":\"orderNo\",\"type\":\"string\",\"title\":\"订单号\"},"
            + "{\"name\":\"amount\",\"type\":\"integer\",\"description\":\"金额\"}]}]}}}";

    @Mock private FlowApiRepository flowApiRepository;
    @Mock private FlowApiExcelTemplateRepository excelTemplateRepository;
    @Mock private DynamicDataSourceService dynamicDataSourceService;
    @Mock private DemoModeGuard demoModeGuard;
    @Mock private DataSourceWallGuard dataSourceWallGuard;
    @Mock private PrivacyFieldInterceptor privacyFieldInterceptor;
    @Mock private FlowHostPrincipalProvider flowHostPrincipalProvider;

    private ApiDataViewServiceImpl service;
    private JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:data_view_" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE t_order (id INT PRIMARY KEY, order_no VARCHAR(32), amount INT)");
        for (int i = 1; i <= 25; i++) {
            jdbc.update("INSERT INTO t_order VALUES (?, ?, ?)", i, "NO-" + i, i * 10);
        }
        when(dynamicDataSourceService.execute(anyString(), any())).thenAnswer(inv -> run(inv.getArgument(1)));
        when(dynamicDataSourceService.executeInTransaction(anyString(), any(DataSourceCallback.class)))
                .thenAnswer(inv -> tx.execute(status -> run(inv.getArgument(1))));
        when(privacyFieldInterceptor.resolveConfig(any(), anyBoolean())).thenReturn(EffectivePrivacy.disabled());
        when(privacyFieldInterceptor.resolveDecision(any(), any())).thenReturn(PrivacyDecision.mask());
        when(excelTemplateRepository.findByApiId(anyString())).thenReturn(Optional.empty());
        when(excelTemplateRepository.save(any())).thenAnswer(inv -> {
            FlowApiExcelTemplateDO d = inv.getArgument(0);
            d.setId("tpl-1");
            return d;
        });

        YuFlowProperties props = new YuFlowProperties();
        props.getSecurity().setJwtSecretKey("characterization-test-signing-key-0123456789");

        ApiSqlExecutor sqlExecutor = new ApiSqlExecutor(dynamicDataSourceService, dataSourceWallGuard, demoModeGuard);
        ApiDataViewSupport support = new ApiDataViewSupport(flowApiRepository, new ContractParamTypeConverter(),
                privacyFieldInterceptor, flowHostPrincipalProvider);
        ApiExcelExporter exporter = new ApiExcelExporter(support, sqlExecutor, dynamicDataSourceService,
                dataSourceWallGuard, excelTemplateRepository, demoModeGuard);
        service = new ApiDataViewServiceImpl(
                new ApiDataPreviewer(support, sqlExecutor),
                exporter,
                new ApiExcelExportLinks(support, exporter, demoModeGuard, props),
                new ApiExcelTemplateManager(flowApiRepository, excelTemplateRepository, demoModeGuard));
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("SHUTDOWN");
    }

    @SuppressWarnings("unchecked")
    private Object run(Object callback) {
        return ((DataSourceCallback<Object>) callback).doInDataSource(jdbc);
    }

    // ─── 预览 ─────────────────────────────────────────────────────────────

    @Test
    void previewListSlicesInMemoryAndTakesHeadersFromContract() {
        stubApi(draft("LIST", null));

        ApiDataPreviewResultDTO result = service.preview("api-1", previewDraft(1, 10));

        assertEquals("LIST", result.getResponseType());
        assertEquals(25L, result.getTotal());
        assertEquals(3, result.getPages());
        assertEquals(10, result.getRows().size());
        assertEquals("11", result.getRows().get(0).get("id"));
        assertEquals(List.of("id:id", "orderNo:订单号", "amount:金额"), headers(result.getColumns()));
        assertEquals(Boolean.FALSE, result.getPrivacyEnabled());
        assertNull(result.getPrivacyClass());
    }

    @Test
    void previewPageUsesSqlPagingAndClampsSize() {
        stubApi(draft("PAGE", null));

        ApiDataPreviewResultDTO result = service.preview("api-1", previewDraft(0, 1000));

        assertEquals(200, result.getSize(), "预览页大小上限 200");
        assertEquals(25L, result.getTotal());
        assertEquals(1, result.getPages());
        assertEquals(25, result.getRows().size());
    }

    @Test
    void previewObjectReturnsSingleRow() {
        stubApi(draft("OBJECT", null).setSqlContent("SELECT order_no FROM t_order WHERE id = ${id}"));
        ApiDataPreviewRequestDTO request = previewDraft(0, 10);
        request.setQueryParams(Map.of("id", "7"));

        ApiDataPreviewResultDTO result = service.preview("api-1", request);

        assertEquals("NO-7", result.getObject().get("orderNo"));
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRows().size());
    }

    @Test
    void previewPublishedReadsSnapshotNotDraft() throws Exception {
        FlowApiDO api = draft("LIST", null).setSqlContent("SELECT id FROM t_order");
        publish(api, SQL.replace("amount >= ${min}", "id <= 3"), null);
        stubApi(api);
        ApiDataPreviewRequestDTO request = new ApiDataPreviewRequestDTO();

        assertEquals(3L, service.preview("api-1", request).getTotal());
    }

    @Test
    void previewRejectsUnpublishedWithoutDraftAndNonDbApis() {
        stubApi(draft("LIST", null));
        assertEquals("接口未发布，请勾选「使用草稿」或先发布",
                rejected(() -> service.preview("api-1", null)));

        stubApi(draft("LIST", null).setServiceType("JSON"));
        assertTrue(rejected(() -> service.preview("api-1", previewDraft(0, 10))).startsWith("P0 仅支持 DB 模式接口"));

        stubApi(draft("UPDATE", null));
        assertEquals("仅支持 PAGE / LIST / OBJECT 响应类型",
                rejected(() -> service.preview("api-1", previewDraft(0, 10))));
    }

    // ─── 导出 ─────────────────────────────────────────────────────────────

    @Test
    void exportDynamicUsesConfiguredColumnsAndRowCap() throws Exception {
        stubApi(draft("LIST", "{\"columns\":[{\"field\":\"orderNo\",\"header\":\"单号\"},{\"field\":\"amount\"}],"
                + "\"sheetName\":\"订单\",\"maxExportRows\":5}"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportExcel("api-1", exportDraft(), response);

        verify(demoModeGuard).checkModifyOrDelete("api-1", "API 数据导出");
        assertEquals("DYNAMIC", response.getHeader("X-Export-Mode"));
        assertEquals("5", response.getHeader("X-Export-Rows"));
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;filename*=UTF-8''"));
        List<Map<Integer, String>> sheet = readSheet(response.getContentAsByteArray(), 0);
        assertEquals(Map.of(0, "单号", 1, "金额"), sheet.get(0));
        assertEquals(6, sheet.size());
        assertEquals(Map.of(0, "NO-1", 1, "10"), sheet.get(1));
    }

    @Test
    void exportRejectsWhenDisabled() {
        stubApi(draft("LIST", "{\"enabled\":false}"));
        assertEquals("该接口已关闭数据导出", rejected(
                () -> service.exportExcel("api-1", exportDraft(), new MockHttpServletResponse())));
    }

    @Test
    void templateModeWithoutTemplateFallsBackToDynamic() {
        stubApi(draft("LIST", "{\"exportMode\":\"TEMPLATE\"}"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportExcel("api-1", exportDraft(), response);

        assertEquals("DYNAMIC", response.getHeader("X-Export-Mode"));
        assertEquals("TEMPLATE_MISSING", response.getHeader("X-Export-Fallback"));
        assertEquals("25", response.getHeader("X-Export-Rows"));
    }

    @Test
    void templateModeFillsListPlaceholders() throws Exception {
        stubApi(draft("LIST", "{\"exportMode\":\"TEMPLATE\"}"));
        when(excelTemplateRepository.findByApiId("api-1")).thenReturn(Optional.of(
                FlowApiExcelTemplateDO.builder().id("tpl-1").apiId("api-1").content(templateXlsx()).build()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportExcel("api-1", exportDraft(), response);

        assertEquals("TEMPLATE", response.getHeader("X-Export-Mode"), response.getHeader("X-Export-Fallback") + " " + response.getHeader("X-Export-Fallback-Message"));
        assertNull(response.getHeader("X-Export-Fallback"));
        List<Map<Integer, String>> sheet = readSheet(response.getContentAsByteArray(), 0);
        assertEquals("订单 导出", sheet.get(0).get(0));
        assertEquals("NO-1", sheet.get(1).get(0));
        assertEquals("NO-25", sheet.get(25).get(0));
    }

    @Test
    void openExportRequiresPublishedOpenFlag() throws Exception {
        FlowApiDO api = draft("LIST", null);
        publish(api, SQL, "{}");
        stubApi(api);

        assertEquals("未启用对外 Excel 下载", rejected(
                () -> service.exportPublishedOpenExcel(api, null, new MockHttpServletResponse())));
    }

    @Test
    void signedLinkRoundTripExportsPublishedSnapshot() throws Exception {
        FlowApiDO api = draft("LIST", null);
        publish(api, SQL, "{\"openExportEnabled\":true,\"signedLinkTtlSeconds\":10}");
        stubApi(api);

        ApiExcelExportLinkDTO link = service.createExportLink("api-1", new ApiExcelExportLinkRequestDTO());

        assertEquals(30, link.getTtlSeconds(), "有效期下限 30 秒");
        assertTrue(link.getUrl().startsWith("/flow-api/download/excel/"), link.getUrl());
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportBySignedToken(link.getUrl().substring("/flow-api/download/excel/".length()), response);
        assertEquals("DYNAMIC", response.getHeader("X-Export-Mode"));
        assertEquals("25", response.getHeader("X-Export-Rows"));
    }

    @Test
    void signedLinkRequiresPublishAndOpenFlag() throws Exception {
        stubApi(draft("LIST", null));
        assertTrue(rejected(
                () -> service.createExportLink("api-1", null)).startsWith("请先发布接口后再签发下载链"));

        FlowApiDO api = draft("LIST", null);
        publish(api, SQL, "{\"openExportEnabled\":true,\"signedLinkEnabled\":false}");
        stubApi(api);
        assertEquals("该接口已关闭短期下载链",
                rejected(() -> service.createExportLink("api-1", null)));
    }

    // ─── 模板管理 ─────────────────────────────────────────────────────────

    @Test
    void uploadTemplateValidatesFileAndSwitchesExportMode() throws Exception {
        FlowApiDO api = draft("LIST", null);
        stubApi(api);

        assertEquals("仅支持 .xlsx（禁止宏包 .xlsm / 旧版 .xls）", rejected(
                () -> service.uploadExcelTemplate("api-1", xlsx("a.xls", templateXlsx()))));
        assertEquals("仅支持 .xlsx 文件", rejected(
                () -> service.uploadExcelTemplate("api-1", xlsx("a.csv", templateXlsx()))));

        ApiExcelTemplateMetaDTO meta = service.uploadExcelTemplate("api-1", xlsx("..\\evil/订单.xlsx", templateXlsx()));

        assertEquals("订单.xlsx", meta.getFileName(), "去掉路径");
        assertEquals(Boolean.TRUE, meta.getHasListPlaceholder());
        assertNull(meta.getWarning());
        Map<?, ?> cfg = mapper.readValue(api.getViewExportConfig(), Map.class);
        assertEquals("TEMPLATE", cfg.get("exportMode"));
        assertEquals("tpl-1", cfg.get("templateFileId"));
        assertEquals(0, cfg.get("templateSheetNo"));
        verify(demoModeGuard, times(3)).checkModifyOrDelete("api-1", "上传 Excel 导出模板");
    }

    @Test
    void deleteTemplateResetsExportMode() throws Exception {
        FlowApiDO api = draft("LIST", "{\"exportMode\":\"TEMPLATE\",\"templateFileId\":\"tpl-1\"}");
        stubApi(api);

        service.deleteExcelTemplate("api-1");

        verify(excelTemplateRepository).deleteByApiId("api-1");
        Map<?, ?> cfg = mapper.readValue(api.getViewExportConfig(), Map.class);
        assertEquals("DYNAMIC", cfg.get("exportMode"));
        assertNull(cfg.get("templateFileId"));
    }

    @Test
    void sampleTemplateListsPlaceholdersAndHelpSheet() throws Exception {
        stubApi(draft("LIST", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.downloadSampleExcelTemplate("api-1", response);

        List<Map<Integer, String>> data = readSheet(response.getContentAsByteArray(), 0);
        assertEquals(Map.of(0, "订单号", 1, "金额"), data.get(1));
        assertEquals(Map.of(0, "{.orderNo}", 1, "{.amount}"), data.get(2));
        assertEquals("使用说明", readSheet(response.getContentAsByteArray(), 1).get(0).get(0));
    }

    @Test
    void templateMetaAndDownload() {
        stubApi(draft("LIST", null));
        assertTrue(!service.getExcelTemplateMeta("api-1").isPresent());
        assertEquals("尚未上传导出模板", rejected(
                () -> service.downloadExcelTemplate("api-1", new MockHttpServletResponse())));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private void stubApi(FlowApiDO api) {
        when(flowApiRepository.findById("api-1")).thenReturn(Optional.of(api));
    }

    private static FlowApiDO draft(String responseType, String viewExportConfig) {
        return new FlowApiDO().setId("api-1").setName("订单").setServiceType("DB").setSqlContent(SQL)
                .setDatasource("ds1").setResponseType(responseType).setContract(CONTRACT)
                .setViewExportConfig(viewExportConfig).setPublishStatus(0);
    }

    private void publish(FlowApiDO api, String sql, String viewExportConfig) throws Exception {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("serviceType", "DB");
        snap.put("sqlContent", sql);
        snap.put("datasource", "ds1");
        snap.put("responseType", api.getResponseType());
        snap.put("contract", CONTRACT);
        snap.put("viewExportConfig", viewExportConfig);
        api.setPublishStatus(1).setPublishedSnapshot(mapper.writeValueAsString(snap));
    }

    private static ApiDataPreviewRequestDTO previewDraft(int page, int size) {
        ApiDataPreviewRequestDTO request = new ApiDataPreviewRequestDTO();
        request.setUseDraft(true);
        request.setPage(page);
        request.setSize(size);
        request.setQueryParams(Map.of("min", "0"));
        return request;
    }

    private static ApiDataExportRequestDTO exportDraft() {
        ApiDataExportRequestDTO request = new ApiDataExportRequestDTO();
        request.setUseDraft(true);
        request.setQueryParams(Map.of("min", "0"));
        return request;
    }

    private static List<String> headers(List<ViewExportColumnDTO> columns) {
        return columns.stream().map(c -> c.getField() + ":" + c.getHeader()).toList();
    }

    /** 用 POI 生成，与 Excel 另存的文件结构一致 */
    private static byte[] templateXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("数据");
            sheet.createRow(0).createCell(0).setCellValue("{apiName} 导出");
            Row list = sheet.createRow(1);
            list.createCell(0).setCellValue("{.orderNo}");
            list.createCell(1).setCellValue("{.amount}");
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private static MockMultipartFile xlsx(String name, byte[] content) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    /** ValidationException 的消息带 [VALIDATION_ERROR] 前缀，这里只取业务文案 */
    private static String rejected(org.junit.jupiter.api.function.Executable call) {
        String msg = assertThrows(ValidationException.class, call).getMessage();
        return msg.startsWith("[VALIDATION_ERROR] ") ? msg.substring("[VALIDATION_ERROR] ".length()) : msg;
    }

    private static List<Map<Integer, String>> readSheet(byte[] xlsx, int sheetNo) {
        return EasyExcel.read(new ByteArrayInputStream(xlsx)).sheet(sheetNo).headRowNumber(0).doReadSync();
    }
}
