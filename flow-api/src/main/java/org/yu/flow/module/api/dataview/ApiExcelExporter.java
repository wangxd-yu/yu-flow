package org.yu.flow.module.api.dataview;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.dto.SqlAndParams;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.engine.sql.DynamicSqlParser;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;
import org.yu.flow.module.api.dto.ViewExportColumnDTO;
import org.yu.flow.module.api.dto.ViewExportConfigDTO;
import org.yu.flow.module.api.privacy.EffectivePrivacy;
import org.yu.flow.module.api.privacy.PrivacyDecision;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.api.support.ExcelTemplateInspector;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.datasource.wall.DataSourceWallGuard;
import org.yu.flow.util.RegularSqlParseUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DB 接口的 Excel 导出。TEMPLATE 模式用已上传模板填充 {.字段} 列表区，模板缺失、无列表占位符或填充失败时
 * 回退为动态表头，并通过 X-Export-Fallback 响应头告知原因。整份文件先在内存生成再写出，失败时不会返回半个文件。
 */
@Slf4j
@Component
public class ApiExcelExporter {

    private static final int DEFAULT_MAX_EXPORT_ROWS = 50_000;

    private final ApiDataViewSupport support;
    private final SqlExecutorService sqlExecutorService;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSourceWallGuard dataSourceWallGuard;
    private final FlowApiExcelTemplateRepository excelTemplateRepository;
    private final DemoModeGuard demoModeGuard;

    public ApiExcelExporter(ApiDataViewSupport support,
                            SqlExecutorService sqlExecutorService,
                            DynamicDataSourceService dynamicDataSourceService,
                            DataSourceWallGuard dataSourceWallGuard,
                            FlowApiExcelTemplateRepository excelTemplateRepository,
                            DemoModeGuard demoModeGuard) {
        this.support = support;
        this.sqlExecutorService = sqlExecutorService;
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.dataSourceWallGuard = dataSourceWallGuard;
        this.excelTemplateRepository = excelTemplateRepository;
        this.demoModeGuard = demoModeGuard;
    }

    /** 管理端导出：可选草稿 */
    public void exportExcel(String apiId, ApiDataExportRequestDTO request, HttpServletResponse response) {
        if (request == null) {
            request = new ApiDataExportRequestDTO();
        }
        FlowApiDO api = support.workingApi(apiId, Boolean.TRUE.equals(request.getUseDraft()));
        demoModeGuard.checkModifyOrDelete(apiId, "API 数据导出");
        ApiDataViewSupport.assertDbQueryable(api);
        ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
        if (Boolean.FALSE.equals(cfg.getEnabled())) {
            throw new ValidationException("该接口已关闭数据导出");
        }
        export(api, cfg, request, response, null);
    }

    /** 对外业务路径导出：强制已发布快照，且快照里开启了对外 Excel 下载 */
    public void exportPublishedOpen(FlowApiDO cachedApi, ApiDataExportRequestDTO request, HttpServletResponse response) {
        if (cachedApi == null || StrUtil.isBlank(cachedApi.getId())) {
            throw new ValidationException("接口不存在");
        }
        FlowApiDO api = support.workingApi(cachedApi.getId(), false);
        ApiDataViewSupport.assertDbQueryable(api);
        ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
        if (Boolean.FALSE.equals(cfg.getEnabled())) {
            throw new ValidationException("该接口已关闭数据导出");
        }
        if (!Boolean.TRUE.equals(cfg.getOpenExportEnabled())) {
            throw new ValidationException("未启用对外 Excel 下载");
        }
        if (request == null) {
            request = new ApiDataExportRequestDTO();
        }
        request.setUseDraft(false);
        export(api, cfg, request, response, null);
    }

    /**
     * @param forcedPrivacy 短期下载链签发时固化的脱敏决定；为 null 时按当前调用方身份决定
     */
    public void export(FlowApiDO api, ViewExportConfigDTO cfg, ApiDataExportRequestDTO request,
                       HttpServletResponse response, PrivacyDecision forcedPrivacy) {
        int maxRows = cfg.getMaxExportRows() == null || cfg.getMaxExportRows() <= 0
                ? DEFAULT_MAX_EXPORT_ROWS
                : Math.min(cfg.getMaxExportRows(), DEFAULT_MAX_EXPORT_ROWS);

        Map<String, Object> params = support.execParams(api.getContract(), request.getQueryParams(),
                request.getBodyParams(), request.getPathParams());
        SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(api.getSqlContent(), params);

        String responseType = ApiDataViewSupport.responseType(api);
        String sheetName = StrUtil.blankToDefault(cfg.getSheetName(), "数据");
        String fileName = ExcelDownloads.timestampedFileName(api.getName());
        String apiId = api.getId();
        EffectivePrivacy privacy = support.privacy(api, Boolean.TRUE.equals(request.getUseDraft()));
        PrivacyDecision privacyDecision = !privacy.isEnabled()
                ? PrivacyDecision.mask()
                : (forcedPrivacy != null ? forcedPrivacy : support.decide(privacy));

        boolean preferTemplate = "TEMPLATE".equalsIgnoreCase(StrUtil.blankToDefault(cfg.getExportMode(), "DYNAMIC"));
        FlowApiExcelTemplateDO template = preferTemplate
                ? excelTemplateRepository.findByApiId(apiId).orElse(null)
                : null;
        String fallbackCode = null;
        String fallbackMessage = null;
        if (preferTemplate && (template == null || template.getContent() == null || template.getContent().length == 0)) {
            log.warn("[ApiDataView] exportMode=TEMPLATE 但模板不存在，回退 DYNAMIC apiId={}", apiId);
            preferTemplate = false;
            fallbackCode = "TEMPLATE_MISSING";
            fallbackMessage = "未找到已上传的 Excel 模板，已使用动态表头导出";
        } else if (preferTemplate && !ExcelTemplateInspector.scan(template.getContent()).hasListPlaceholder()) {
            log.warn("[ApiDataView] 模板缺少 {.list} 占位符，回退 DYNAMIC apiId={}", apiId);
            preferTemplate = false;
            fallbackCode = "TEMPLATE_NO_LIST_PLACEHOLDER";
            fallbackMessage = "模板中未检测到列表占位符 {.字段}，已使用动态表头导出";
        }

        try {
            if (preferTemplate) {
                List<Map<String, Object>> rows = null;
                List<ViewExportColumnDTO> columns = null;
                try {
                    rows = support.maskRows(loadRows(api, sqlAndParams, responseType, maxRows), privacy, privacyDecision);
                    columns = ApiExportColumns.resolve(cfg, api.getContract(), rows, rows.isEmpty() ? null : rows.get(0), true);
                    byte[] filled = fillTemplate(template.getContent(), cfg, api.getName(), columns, rows);
                    ExcelDownloads.applyDownloadHeaders(response, fileName);
                    response.setHeader("X-Export-Mode", "TEMPLATE");
                    response.setHeader("X-Export-Rows", String.valueOf(rows.size()));
                    response.setContentLength(filled.length);
                    response.getOutputStream().write(filled);
                    response.getOutputStream().flush();
                    log.info("[ApiDataView] 模板导出完成 apiId={}, rows={}", apiId, rows.size());
                    return;
                } catch (Exception fillEx) {
                    log.warn("[ApiDataView] 模板填充失败，回退 DYNAMIC apiId={}: {}", apiId, fillEx.getMessage(), fillEx);
                    fallbackCode = "TEMPLATE_FILL_FAILED";
                    fallbackMessage = "模板填充失败，已使用动态表头导出："
                            + StrUtil.blankToDefault(fillEx.getMessage(), fillEx.getClass().getSimpleName());
                    if (rows != null) {
                        if (columns == null) {
                            columns = ApiExportColumns.resolve(cfg, api.getContract(), rows,
                                    rows.isEmpty() ? null : rows.get(0), true);
                        }
                        writeDynamic(response, fileName, fallbackCode, fallbackMessage, sheetName, columns, rows);
                        return;
                    }
                }
            }

            if ("OBJECT".equals(responseType)) {
                Map<String, Object> one = ApiDataViewSupport.objectRow(
                        sqlExecutorService.executeObjectQuery(api.getDatasource(), sqlAndParams));
                one = support.maskObject(one, privacy, privacyDecision);
                List<Map<String, Object>> rows = one == null ? List.of() : List.of(one);
                List<ViewExportColumnDTO> columns = ApiExportColumns.resolve(cfg, api.getContract(), rows, one, true);
                writeDynamic(response, fileName, fallbackCode, fallbackMessage, sheetName, columns, rows);
                return;
            }

            List<Map<String, Object>> rows = support.maskRows(
                    loadRows(api, sqlAndParams, responseType, maxRows), privacy, privacyDecision);
            List<ViewExportColumnDTO> columns = ApiExportColumns.resolve(
                    cfg, api.getContract(), rows, rows.isEmpty() ? null : rows.get(0), true);
            if (!rows.isEmpty()) {
                List<String> labels = new ArrayList<>(rows.get(0).keySet());
                List<String> camels = labels.stream().map(StrUtil::toCamelCase).toList();
                columns = ApiExportColumns.alignToSqlFields(columns, labels, camels, api.getContract());
            }
            writeDynamic(response, fileName, fallbackCode, fallbackMessage, sheetName, columns, rows);
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            log.error("[ApiDataView] 导出失败 apiId={}", apiId, e);
            throw new ValidationException("导出 Excel 失败: "
                    + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /**
     * 全量取数（去掉 SQL 自带的 LIMIT，最多 maxRows 行）。每行同时挂驼峰、原始列名、小写列名三种键，便于契约字段对齐。
     */
    private List<Map<String, Object>> loadRows(FlowApiDO api, SqlAndParams sqlAndParams,
                                               String responseType, int maxRows) {
        if ("OBJECT".equals(responseType)) {
            Map<String, Object> one = ApiDataViewSupport.objectRow(
                    sqlExecutorService.executeObjectQuery(api.getDatasource(), sqlAndParams));
            return one == null ? new ArrayList<>() : new ArrayList<>(List.of(one));
        }

        String sql = RegularSqlParseUtil.removeLimitAndOffset(sqlAndParams.getSql());
        dataSourceWallGuard.assertSqlAllowed(api.getDatasource(), sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        dynamicDataSourceService.execute(api.getDatasource(), jt -> jt.query(sql, sqlAndParams.getParams().toArray(), rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> labels = new ArrayList<>(columnCount);
            List<String> camelFields = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                String label = meta.getColumnLabel(i);
                labels.add(label);
                camelFields.add(StrUtil.toCamelCase(label));
            }
            while (rows.size() < maxRows && rs.next()) {
                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object val = rs.getObject(i);
                    String camel = camelFields.get(i - 1);
                    String label = labels.get(i - 1);
                    rowMap.put(camel, val);
                    if (!camel.equals(label)) {
                        rowMap.put(label, val);
                    }
                    String lower = label == null ? null : label.toLowerCase(Locale.ROOT);
                    if (StrUtil.isNotBlank(lower) && !rowMap.containsKey(lower)) {
                        rowMap.put(lower, val);
                    }
                }
                rows.add(rowMap);
            }
            return rows.size();
        }));
        return rows;
    }

    private static void writeDynamic(HttpServletResponse response, String fileName, String fallbackCode,
                                     String fallbackMessage, String sheetName,
                                     List<ViewExportColumnDTO> columns, List<Map<String, Object>> rows) throws Exception {
        ExcelDownloads.applyDownloadHeaders(response, fileName);
        if (fallbackCode != null) {
            ExcelDownloads.applyFallbackHeaders(response, fallbackCode, fallbackMessage);
        }
        response.setHeader("X-Export-Mode", "DYNAMIC");

        if (columns == null || columns.isEmpty()) {
            columns = ApiExportColumns.fromRows(rows);
        }
        if (columns.isEmpty()) {
            columns = List.of(ViewExportColumnDTO.builder()
                    .field("message").header("提示").exportable(true).visible(true).build());
            rows = List.of(Map.of("message", "无导出列，请检查列配置或 SQL 结果"));
        }
        List<List<String>> head = columns.stream()
                .map(c -> List.of(StrUtil.blankToDefault(c.getHeader(), c.getField())))
                .toList();
        List<List<Object>> data = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                List<Object> line = new ArrayList<>(columns.size());
                for (ViewExportColumnDTO col : columns) {
                    line.add(ApiExportColumns.cellValue(row, col.getField()));
                }
                data.add(line);
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos).head(head).sheet(StrUtil.blankToDefault(sheetName, "数据")).doWrite(data);
        byte[] bytes = bos.toByteArray();
        if (bytes.length == 0) {
            throw new ValidationException("生成 Excel 失败：文件内容为空");
        }
        int rowCount = rows == null ? 0 : rows.size();
        response.setHeader("X-Export-Rows", String.valueOf(rowCount));
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
        log.info("[ApiDataView] 动态导出完成 rows={}, cols={}, bytes={}", rowCount, columns.size(), bytes.length);
    }

    /** 列表区按列的 templateKey 填值；另提供 {exportTime}、{apiName} 两个单值占位符 */
    private static byte[] fillTemplate(byte[] templateBytes, ViewExportConfigDTO cfg, String apiName,
                                       List<ViewExportColumnDTO> columns, List<Map<String, Object>> rows) throws Exception {
        List<Map<String, Object>> fillList = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                if (columns != null) {
                    for (ViewExportColumnDTO col : columns) {
                        if (col == null || StrUtil.isBlank(col.getField())) {
                            continue;
                        }
                        String key = ApiExportColumns.templateKey(
                                StrUtil.blankToDefault(col.getTemplateKey(), col.getField()), col.getField());
                        mapped.put(key, ApiExportColumns.cellValue(row, col.getField()));
                    }
                }
                if (mapped.isEmpty() && row != null) {
                    mapped.putAll(row);
                }
                fillList.add(mapped);
            }
        }

        Map<String, Object> single = new HashMap<>();
        single.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        single.put("apiName", StrUtil.blankToDefault(apiName, ""));

        int sheetNo = cfg.getTemplateSheetNo() == null ? 0 : Math.max(cfg.getTemplateSheetNo(), 0);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ExcelWriter excelWriter = EasyExcel.write(bos).withTemplate(new ByteArrayInputStream(templateBytes)).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(sheetNo).build();
            excelWriter.fill(single, writeSheet);
            excelWriter.fill(fillList, FillConfig.builder().forceNewRow(Boolean.TRUE).build(), writeSheet);
        }
        return bos.toByteArray();
    }
}
