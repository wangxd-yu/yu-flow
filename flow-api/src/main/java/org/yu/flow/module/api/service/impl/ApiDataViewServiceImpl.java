package org.yu.flow.module.api.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.auto.druid.DynamicSqlParser;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.dto.SqlAndParams;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.api.dto.*;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.service.ApiDataViewService;
import org.yu.flow.module.api.support.ExcelTemplateInspector;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.datasource.wall.DataSourceWallGuard;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApiDataViewServiceImpl implements ApiDataViewService {

    private static final int DEFAULT_MAX_EXPORT_ROWS = 50_000;
    private static final int EXPORT_BATCH = 1_000;
    private static final int MAX_TEMPLATE_BYTES = 2 * 1024 * 1024;
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowApiExcelTemplateRepository excelTemplateRepository;
    @Resource
    private SqlExecutorService sqlExecutorService;
    @Resource
    private DynamicDataSourceService dynamicDataSourceService;
    @Resource
    private ContractParamTypeConverter contractParamTypeConverter;
    @Resource
    private DemoModeGuard demoModeGuard;
    @Resource
    private DataSourceWallGuard dataSourceWallGuard;

    @Override
    public ApiDataPreviewResultDTO preview(String apiId, ApiDataPreviewRequestDTO request) {
        if (request == null) {
            request = new ApiDataPreviewRequestDTO();
        }
        boolean useDraft = Boolean.TRUE.equals(request.getUseDraft());
        FlowApiDO api = resolveWorkingApi(apiId, useDraft);
        assertDbQueryable(api);

        ViewExportConfigDTO cfg = parseConfig(api.getViewExportConfig());
        Map<String, Object> params = buildExecParams(api.getContract(), request.getQueryParams(),
                request.getBodyParams(), request.getPathParams());
        SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(
                api.getSqlContent(), params);

        int page = request.getPage() == null ? 0 : Math.max(request.getPage(), 0);
        int size = request.getSize() == null ? 20 : Math.min(Math.max(request.getSize(), 1), 200);
        Pageable pageable = PageRequest.of(page, size);

        String responseType = StrUtil.blankToDefault(api.getResponseType(), "LIST").toUpperCase(Locale.ROOT);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> object = null;
        Long total = null;
        Integer pages = null;

        switch (responseType) {
            case "PAGE" -> {
                Object result = sqlExecutorService.executePageQuery(api.getDatasource(), sqlAndParams, pageable);
                if (result instanceof PageBean<?> pb) {
                    total = pb.getTotal();
                    pages = pb.getPages();
                    rows = castRows(pb.getItems());
                } else {
                    rows = castRows(result);
                    total = (long) rows.size();
                    pages = 1;
                }
            }
            case "OBJECT" -> {
                Object raw = sqlExecutorService.executeObjectQuery(api.getDatasource(), sqlAndParams);
                Map<String, Object> one = castObjectRow(raw);
                object = one;
                if (one != null) {
                    rows = List.of(one);
                }
                total = one == null ? 0L : 1L;
                pages = 1;
            }
            default -> {
                // LIST
                Object result = sqlExecutorService.executeListQuery(api.getDatasource(), sqlAndParams, pageable);
                rows = castRows(result);
                // 预览分页切片
                total = (long) rows.size();
                int from = Math.min(page * size, rows.size());
                int to = Math.min(from + size, rows.size());
                rows = new ArrayList<>(rows.subList(from, to));
                pages = size <= 0 ? 1 : (int) Math.ceil(total / (double) size);
            }
        }

        List<ViewExportColumnDTO> columns = resolveColumns(cfg, api.getContract(), rows, object, false);

        return ApiDataPreviewResultDTO.builder()
                .apiId(api.getId())
                .apiName(api.getName())
                .serviceType(api.getServiceType())
                .responseType(responseType)
                .useDraft(useDraft)
                .columns(columns)
                .rows(rows)
                .object(object)
                .total(total)
                .page(page)
                .size(size)
                .pages(pages)
                .build();
    }

    @Override
    public void exportExcel(String apiId, ApiDataExportRequestDTO request, HttpServletResponse response) {
        if (request == null) {
            request = new ApiDataExportRequestDTO();
        }
        boolean useDraft = Boolean.TRUE.equals(request.getUseDraft());
        FlowApiDO api = resolveWorkingApi(apiId, useDraft);
        demoModeGuard.checkModifyOrDelete(apiId, "API 数据导出");
        assertDbQueryable(api);

        ViewExportConfigDTO cfg = parseConfig(api.getViewExportConfig());
        if (Boolean.FALSE.equals(cfg.getEnabled())) {
            throw new ValidationException("该接口已关闭数据导出");
        }
        int maxRows = cfg.getMaxExportRows() == null || cfg.getMaxExportRows() <= 0
                ? DEFAULT_MAX_EXPORT_ROWS
                : Math.min(cfg.getMaxExportRows(), DEFAULT_MAX_EXPORT_ROWS);

        Map<String, Object> params = buildExecParams(api.getContract(), request.getQueryParams(),
                request.getBodyParams(), request.getPathParams());
        SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(
                api.getSqlContent(), params);

        String responseType = StrUtil.blankToDefault(api.getResponseType(), "LIST").toUpperCase(Locale.ROOT);
        String sheetName = StrUtil.blankToDefault(cfg.getSheetName(), "数据");
        String fileName = buildFileName(api.getName());

        boolean preferTemplate = "TEMPLATE".equalsIgnoreCase(StrUtil.blankToDefault(cfg.getExportMode(), "DYNAMIC"));
        FlowApiExcelTemplateDO template = preferTemplate
                ? excelTemplateRepository.findByApiId(api.getId()).orElse(null)
                : null;
        String fallbackCode = null;
        String fallbackMessage = null;
        if (preferTemplate && (template == null || template.getContent() == null || template.getContent().length == 0)) {
            log.warn("[ApiDataView] exportMode=TEMPLATE 但模板不存在，回退 DYNAMIC apiId={}", apiId);
            preferTemplate = false;
            fallbackCode = "TEMPLATE_MISSING";
            fallbackMessage = "未找到已上传的 Excel 模板，已使用动态表头导出";
        } else if (preferTemplate && template != null) {
            ExcelTemplateInspector.ScanResult scan = ExcelTemplateInspector.scan(template.getContent());
            if (!scan.hasListPlaceholder()) {
                log.warn("[ApiDataView] 模板缺少 {.list} 占位符，回退 DYNAMIC apiId={}", apiId);
                preferTemplate = false;
                fallbackCode = "TEMPLATE_NO_LIST_PLACEHOLDER";
                fallbackMessage = "模板中未检测到列表占位符 {.字段}，已使用动态表头导出";
            }
        }

        try {
            applyDownloadHeaders(response, fileName);
            if (fallbackCode != null) {
                applyFallbackHeaders(response, fallbackCode, fallbackMessage);
            }

            if (preferTemplate && template != null) {
                List<Map<String, Object>> rows = null;
                List<ViewExportColumnDTO> columns = null;
                try {
                    rows = loadExportRows(api, sqlAndParams, responseType, maxRows);
                    columns = resolveColumns(cfg, api.getContract(), rows,
                            rows.isEmpty() ? null : rows.get(0), true);
                    byte[] filled = fillExcelByTemplate(template.getContent(), cfg, api.getName(), columns, rows);
                    response.setHeader("X-Export-Mode", "TEMPLATE");
                    response.setHeader("X-Export-Rows", String.valueOf(rows.size()));
                    response.getOutputStream().write(filled);
                    response.getOutputStream().flush();
                    log.info("[ApiDataView] 模板导出完成 apiId={}, rows={}", apiId, rows.size());
                    return;
                } catch (Exception fillEx) {
                    log.warn("[ApiDataView] 模板填充失败，回退 DYNAMIC apiId={}: {}",
                            apiId, fillEx.getMessage(), fillEx);
                    applyFallbackHeaders(response, "TEMPLATE_FILL_FAILED",
                            "模板填充失败，已使用动态表头导出："
                                    + StrUtil.blankToDefault(fillEx.getMessage(), fillEx.getClass().getSimpleName()));
                    // 复用已取数，避免二次查库
                    if (rows != null) {
                        response.setHeader("X-Export-Mode", "DYNAMIC");
                        response.setHeader("X-Export-Rows", String.valueOf(rows.size()));
                        if (columns == null) {
                            columns = resolveColumns(cfg, api.getContract(), rows,
                                    rows.isEmpty() ? null : rows.get(0), true);
                        }
                        writeExcel(response, sheetName, columns, rows);
                        return;
                    }
                }
            }

            response.setHeader("X-Export-Mode", "DYNAMIC");
            if ("OBJECT".equals(responseType)) {
                Map<String, Object> one = castObjectRow(
                        sqlExecutorService.executeObjectQuery(api.getDatasource(), sqlAndParams));
                List<Map<String, Object>> rows = one == null ? List.of() : List.of(one);
                List<ViewExportColumnDTO> columns = resolveColumns(cfg, api.getContract(), rows, one, true);
                response.setHeader("X-Export-Rows", String.valueOf(rows.size()));
                writeExcel(response, sheetName, columns, rows);
                return;
            }

            streamExport(api.getDatasource(), sqlAndParams, sheetName, cfg, api.getContract(), maxRows, response);
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            log.error("[ApiDataView] 导出失败 apiId={}", apiId, e);
            throw new ValidationException("导出 Excel 失败: "
                    + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private void applyDownloadHeaders(HttpServletResponse response, String fileName) {
        response.setContentType(XLSX_MIME);
        response.setCharacterEncoding("utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition",
                "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setHeader("Access-Control-Expose-Headers",
                "Content-Disposition, X-Export-Mode, X-Export-Fallback, X-Export-Fallback-Message, X-Export-Rows");
    }

    private void applyFallbackHeaders(HttpServletResponse response, String code, String message) {
        response.setHeader("X-Export-Fallback", code);
        if (StrUtil.isNotBlank(message)) {
            response.setHeader("X-Export-Fallback-Message",
                    URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20"));
        }
    }

    @Override
    public ApiExcelTemplateMetaDTO getExcelTemplateMeta(String apiId) {
        flowApiRepository.findById(apiId)
                .orElseThrow(() -> new ValidationException("API 不存在"));
        return excelTemplateRepository.findByApiId(apiId)
                .map(d -> {
                    ExcelTemplateInspector.ScanResult scan = ExcelTemplateInspector.scan(d.getContent());
                    String warning = scan.hasListPlaceholder() ? null
                            : "模板中未检测到 {.字段} 列表占位符，导出时将自动回退动态表头";
                    return ApiExcelTemplateMetaDTO.fromDO(d, scan.hasListPlaceholder(), warning);
                })
                .orElse(ApiExcelTemplateMetaDTO.empty(apiId));
    }

    @Override
    @Transactional
    public ApiExcelTemplateMetaDTO uploadExcelTemplate(String apiId, MultipartFile file) {
        FlowApiDO api = flowApiRepository.findById(apiId)
                .orElseThrow(() -> new ValidationException("API 不存在"));
        demoModeGuard.checkModifyOrDelete(apiId, "上传 Excel 导出模板");
        if (file == null || file.isEmpty()) {
            throw new ValidationException("请选择 xlsx 文件");
        }
        String original = StrUtil.blankToDefault(file.getOriginalFilename(), "template.xlsx").trim();
        // 去掉路径伪装
        int slash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        if (slash >= 0) {
            original = original.substring(slash + 1);
        }
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsm") || lower.endsWith(".xls") || lower.endsWith(".xlsb")) {
            throw new ValidationException("仅支持 .xlsx（禁止宏包 .xlsm / 旧版 .xls）");
        }
        if (!lower.endsWith(".xlsx")) {
            throw new ValidationException("仅支持 .xlsx 文件");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw new ValidationException("模板文件不能超过 2MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new ValidationException("读取上传文件失败");
        }
        try {
            ExcelTemplateInspector.assertSafeXlsx(bytes);
        } catch (IllegalArgumentException iae) {
            throw new ValidationException(iae.getMessage());
        }

        ExcelTemplateInspector.ScanResult scan = ExcelTemplateInspector.scan(bytes);
        String warning = null;
        if (!scan.hasListPlaceholder()) {
            warning = "已保存，但未检测到 {.字段} 列表占位符；导出时会回退动态表头。请参考示例模板修改后重新上传。";
        }

        LocalDateTime now = LocalDateTime.now();
        FlowApiExcelTemplateDO entity = excelTemplateRepository.findByApiId(apiId).orElse(null);
        if (entity == null) {
            entity = FlowApiExcelTemplateDO.builder()
                    .apiId(apiId)
                    .createTime(now)
                    .build();
        }
        entity.setFileName(original);
        entity.setContentType(XLSX_MIME);
        entity.setContent(bytes);
        entity.setFileSize(bytes.length);
        entity.setUpdateTime(now);
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        entity = excelTemplateRepository.save(entity);

        ViewExportConfigDTO cfg = parseConfig(api.getViewExportConfig());
        cfg.setExportMode("TEMPLATE");
        cfg.setTemplateFileId(entity.getId());
        if (cfg.getTemplateSheetNo() == null) {
            cfg.setTemplateSheetNo(0);
        }
        try {
            api.setViewExportConfig(MAPPER.writeValueAsString(cfg));
            api.setUpdateTime(now);
            flowApiRepository.save(api);
        } catch (Exception e) {
            log.warn("[ApiDataView] 回写 viewExportConfig 失败: {}", e.getMessage());
        }
        return ApiExcelTemplateMetaDTO.fromDO(entity, scan.hasListPlaceholder(), warning);
    }

    @Override
    @Transactional
    public void deleteExcelTemplate(String apiId) {
        flowApiRepository.findById(apiId)
                .orElseThrow(() -> new ValidationException("API 不存在"));
        demoModeGuard.checkModifyOrDelete(apiId, "删除 Excel 导出模板");
        excelTemplateRepository.deleteByApiId(apiId);
        flowApiRepository.findById(apiId).ifPresent(api -> {
            ViewExportConfigDTO cfg = parseConfig(api.getViewExportConfig());
            cfg.setExportMode("DYNAMIC");
            cfg.setTemplateFileId(null);
            try {
                api.setViewExportConfig(MAPPER.writeValueAsString(cfg));
                api.setUpdateTime(LocalDateTime.now());
                flowApiRepository.save(api);
            } catch (Exception e) {
                log.warn("[ApiDataView] 删除模板后回写配置失败: {}", e.getMessage());
            }
        });
    }

    @Override
    public void downloadSampleExcelTemplate(String apiId, HttpServletResponse response) {
        FlowApiDO api = flowApiRepository.findById(apiId)
                .orElseThrow(() -> new ValidationException("API 不存在"));
        ViewExportConfigDTO cfg = parseConfig(api.getViewExportConfig());
        List<ViewExportColumnDTO> columns = resolveColumns(cfg, api.getContract(), List.of(), null, true);
        if (columns.isEmpty()) {
            columns = List.of(
                    ViewExportColumnDTO.builder().field("field1").header("列1").templateKey("field1").build(),
                    ViewExportColumnDTO.builder().field("field2").header("列2").templateKey("field2").build()
            );
        }
        try {
            String base = StrUtil.blankToDefault(api.getName(), "export")
                    .replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = base + "_导出模板示例.xlsx";
            applyDownloadHeaders(response, fileName);

            List<List<String>> dataRows = new ArrayList<>();
            dataRows.add(List.of("{apiName} 导出 {exportTime}"));
            List<String> headers = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            for (ViewExportColumnDTO c : columns) {
                if (Boolean.FALSE.equals(c.getExportable())) {
                    continue;
                }
                headers.add(StrUtil.blankToDefault(c.getHeader(), c.getField()));
                String key = sanitizeTemplateKey(StrUtil.blankToDefault(c.getTemplateKey(), c.getField()), c.getField());
                placeholders.add("{." + key + "}");
            }
            dataRows.add(headers);
            dataRows.add(placeholders);

            List<List<String>> helpRows = List.of(
                    List.of("使用说明"),
                    List.of("1. 列表区占位符必须写成 {.字段名}（注意点号），与「导出模板」映射表中的 key 一致"),
                    List.of("2. 标题等单值可用 {exportTime}、{apiName}"),
                    List.of("3. 一个 Sheet 只放一个列表填充区；可保留公司样式、合并单元格"),
                    List.of("4. 改完后保存为 .xlsx（不要 .xlsm），在管理端上传；勿改「使用说明」Sheet 亦可删除"),
                    List.of("5. 模板缺失或占位符不正确时，导出会自动回退为动态表头，不会白屏")
            );

            try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build()) {
                WriteSheet dataSheet = EasyExcel.writerSheet(0, "数据").build();
                excelWriter.write(dataRows, dataSheet);
                WriteSheet helpSheet = EasyExcel.writerSheet(1, "使用说明").build();
                excelWriter.write(helpRows, helpSheet);
            }
        } catch (Exception e) {
            throw new ValidationException("生成示例模板失败: "
                    + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    @Override
    public void downloadExcelTemplate(String apiId, HttpServletResponse response) {
        FlowApiExcelTemplateDO tpl = excelTemplateRepository.findByApiId(apiId)
                .orElseThrow(() -> new ValidationException("尚未上传导出模板"));
        try {
            String fileName = StrUtil.blankToDefault(tpl.getFileName(), "template.xlsx");
            applyDownloadHeaders(response, fileName);
            response.setContentType(StrUtil.blankToDefault(tpl.getContentType(), XLSX_MIME));
            response.getOutputStream().write(tpl.getContent());
            response.getOutputStream().flush();
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException("下载模板失败");
        }
    }

    private void streamExport(String datasource, SqlAndParams sqlAndParams, String sheetName,
                              ViewExportConfigDTO cfg, String contract, int maxRows,
                              HttpServletResponse response) throws Exception {
        String sql = sqlAndParams.getSql();
        // 去掉已有 limit，避免只导出一页
        sql = org.yu.flow.auto.util.RegularSqlParseUtil.removeLimitAndOffset(sql);
        dataSourceWallGuard.assertSqlAllowed(datasource, sql);

        String finalSql = sql;
        dynamicDataSourceService.execute(datasource, jt -> {
            return jt.query(finalSql, sqlAndParams.getParams().toArray(), rs -> {
                ExcelWriter excelWriter = null;
                try {
                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    // 同时保留原始 label 与 camelCase，避免契约字段与 SQL 别名对不上
                    List<String> labels = new ArrayList<>(columnCount);
                    List<String> camelFields = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        String label = meta.getColumnLabel(i);
                        labels.add(label);
                        camelFields.add(StrUtil.toCamelCase(label));
                    }

                    List<ViewExportColumnDTO> columns = resolveColumns(cfg, contract,
                            camelFields.stream().map(f -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put(f, null);
                                return m;
                            }).collect(Collectors.toList()),
                            null, true);
                    columns = alignColumnsToSqlFields(columns, labels, camelFields, contract);

                    List<List<String>> head = columns.stream()
                            .map(c -> List.of(StrUtil.blankToDefault(c.getHeader(), c.getField())))
                            .collect(Collectors.toList());

                    // EasyExcel 4：表头必须挂在 WriteSheet 上，否则易出现空表
                    excelWriter = EasyExcel.write(response.getOutputStream()).build();
                    WriteSheet writeSheet = EasyExcel.writerSheet(sheetName).head(head).build();

                    List<List<Object>> batch = new ArrayList<>(EXPORT_BATCH);
                    int written = 0;
                    while (rs.next()) {
                        if (written >= maxRows) {
                            break;
                        }
                        Map<String, Object> rowMap = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object val = rs.getObject(i);
                            String camel = camelFields.get(i - 1);
                            String label = labels.get(i - 1);
                            rowMap.put(camel, val);
                            if (!camel.equals(label)) {
                                rowMap.put(label, val);
                            }
                            // 下划线小写再挂一份，兼容契约 field=user_name
                            String snake = label == null ? null : label.toLowerCase(Locale.ROOT);
                            if (StrUtil.isNotBlank(snake) && !rowMap.containsKey(snake)) {
                                rowMap.put(snake, val);
                            }
                        }
                        List<Object> line = new ArrayList<>(columns.size());
                        for (ViewExportColumnDTO col : columns) {
                            line.add(cellValue(rowMap, col.getField()));
                        }
                        batch.add(line);
                        written++;
                        if (batch.size() >= EXPORT_BATCH) {
                            excelWriter.write(batch, writeSheet);
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        excelWriter.write(batch, writeSheet);
                    } else if (written == 0) {
                        // 无数据也写空表头，避免完全空白工作簿
                        excelWriter.write(List.of(), writeSheet);
                    }
                    log.info("[ApiDataView] 导出完成 rows={}, cols={}", written, columns.size());
                    response.setHeader("X-Export-Rows", String.valueOf(written));
                    return written;
                } catch (Exception ex) {
                    throw new RuntimeException(ex.getMessage(), ex);
                } finally {
                    if (excelWriter != null) {
                        excelWriter.finish();
                    }
                }
            });
        });
    }

    private void writeExcel(HttpServletResponse response, String sheetName,
                            List<ViewExportColumnDTO> columns,
                            List<Map<String, Object>> rows) throws Exception {
        if (columns == null || columns.isEmpty()) {
            columns = inferColumnsFromRows(rows);
        }
        List<List<String>> head = columns.stream()
                .map(c -> List.of(StrUtil.blankToDefault(c.getHeader(), c.getField())))
                .collect(Collectors.toList());
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<>();
            for (ViewExportColumnDTO col : columns) {
                line.add(cellValue(row, col.getField()));
            }
            data.add(line);
        }
        EasyExcel.write(response.getOutputStream())
                .head(head)
                .sheet(sheetName)
                .doWrite(data);
    }

    /**
     * 先灌到内存再写出，失败时可回退 DYNAMIC。
     */
    private byte[] fillExcelByTemplate(byte[] templateBytes, ViewExportConfigDTO cfg, String apiName,
                                       List<ViewExportColumnDTO> columns,
                                       List<Map<String, Object>> rows) throws Exception {
        List<Map<String, Object>> fillList = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                if (columns != null) {
                    for (ViewExportColumnDTO col : columns) {
                        if (col == null || StrUtil.isBlank(col.getField())) {
                            continue;
                        }
                        String key = sanitizeTemplateKey(
                                StrUtil.blankToDefault(col.getTemplateKey(), col.getField()),
                                col.getField());
                        mapped.put(key, cellValue(row, col.getField()));
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
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (ExcelWriter excelWriter = EasyExcel.write(bos)
                .withTemplate(new ByteArrayInputStream(templateBytes))
                .build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(sheetNo).build();
            excelWriter.fill(single, writeSheet);
            FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
            excelWriter.fill(fillList, fillConfig, writeSheet);
        }
        return bos.toByteArray();
    }

    /** 占位符 key：字母/数字/下划线；非法则回退 field */
    private static String sanitizeTemplateKey(String key, String fallbackField) {
        String k = StrUtil.trim(key);
        if (StrUtil.isBlank(k) || !k.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
            String fb = StrUtil.trim(fallbackField);
            if (StrUtil.isNotBlank(fb) && fb.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
                return fb;
            }
            return StrUtil.blankToDefault(StrUtil.toCamelCase(fb), "field");
        }
        return k;
    }

    private List<Map<String, Object>> loadExportRows(FlowApiDO api, SqlAndParams sqlAndParams,
                                                     String responseType, int maxRows) {
        if ("OBJECT".equals(responseType)) {
            Map<String, Object> one = castObjectRow(
                    sqlExecutorService.executeObjectQuery(api.getDatasource(), sqlAndParams));
            return one == null ? new ArrayList<>() : new ArrayList<>(List.of(one));
        }

        String sql = org.yu.flow.auto.util.RegularSqlParseUtil.removeLimitAndOffset(sqlAndParams.getSql());
        dataSourceWallGuard.assertSqlAllowed(api.getDatasource(), sql);
        String finalSql = sql;
        List<Map<String, Object>> rows = new ArrayList<>();
        dynamicDataSourceService.execute(api.getDatasource(), jt -> {
            return jt.query(finalSql, sqlAndParams.getParams().toArray(), rs -> {
                java.sql.ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<String> labels = new ArrayList<>(columnCount);
                List<String> camelFields = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    String label = meta.getColumnLabel(i);
                    labels.add(label);
                    camelFields.add(StrUtil.toCamelCase(label));
                }
                int written = 0;
                while (rs.next()) {
                    if (written >= maxRows) {
                        break;
                    }
                    Map<String, Object> rowMap = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object val = rs.getObject(i);
                        String camel = camelFields.get(i - 1);
                        String label = labels.get(i - 1);
                        rowMap.put(camel, val);
                        if (!camel.equals(label)) {
                            rowMap.put(label, val);
                        }
                        String snake = label == null ? null : label.toLowerCase(Locale.ROOT);
                        if (StrUtil.isNotBlank(snake) && !rowMap.containsKey(snake)) {
                            rowMap.put(snake, val);
                        }
                    }
                    rows.add(rowMap);
                    written++;
                }
                return written;
            });
        });
        return rows;
    }

    /**
     * 配置列若与 SQL 结果字段对不上，回退为 SQL 列 + 契约/配置中文头。
     */
    private List<ViewExportColumnDTO> alignColumnsToSqlFields(List<ViewExportColumnDTO> configured,
                                                              List<String> labels,
                                                              List<String> camelFields,
                                                              String contract) {
        Map<String, String> titleMap = extractContractTitles(contract);
        if (configured != null) {
            for (ViewExportColumnDTO c : configured) {
                if (c != null && StrUtil.isNotBlank(c.getField()) && StrUtil.isNotBlank(c.getHeader())) {
                    titleMap.putIfAbsent(c.getField(), c.getHeader());
                }
            }
        }

        Set<String> sqlKeys = new LinkedHashSet<>();
        sqlKeys.addAll(camelFields);
        sqlKeys.addAll(labels);

        boolean matched = false;
        if (configured != null && !configured.isEmpty()) {
            for (ViewExportColumnDTO c : configured) {
                if (c == null || StrUtil.isBlank(c.getField())) {
                    continue;
                }
                if (findSqlField(c.getField(), camelFields, labels) != null) {
                    matched = true;
                    break;
                }
            }
        }

        if (matched) {
            List<ViewExportColumnDTO> aligned = new ArrayList<>();
            for (ViewExportColumnDTO c : configured) {
                if (c == null || StrUtil.isBlank(c.getField()) || Boolean.FALSE.equals(c.getExportable())) {
                    continue;
                }
                String sqlField = findSqlField(c.getField(), camelFields, labels);
                if (sqlField == null) {
                    continue;
                }
                String header = StrUtil.blankToDefault(c.getHeader(), null);
                if (header == null) {
                    header = titleMap.getOrDefault(c.getField(),
                            titleMap.getOrDefault(sqlField, sqlField));
                }
                aligned.add(ViewExportColumnDTO.builder()
                        .field(sqlField)
                        .header(header)
                        .width(c.getWidth())
                        .exportable(true)
                        .visible(true)
                        .templateKey(StrUtil.blankToDefault(c.getTemplateKey(), c.getField()))
                        .build());
            }
            if (!aligned.isEmpty()) {
                return aligned;
            }
        }

        // 完全以 SQL 结果列为准
        List<ViewExportColumnDTO> fromSql = new ArrayList<>();
        for (int i = 0; i < camelFields.size(); i++) {
            String camel = camelFields.get(i);
            String label = labels.get(i);
            String header = titleMap.getOrDefault(camel,
                    titleMap.getOrDefault(label, titleMap.getOrDefault(
                            label == null ? camel : label.toLowerCase(Locale.ROOT), camel)));
            fromSql.add(ViewExportColumnDTO.builder()
                    .field(camel)
                    .header(header)
                    .exportable(true)
                    .visible(true)
                    .templateKey(camel)
                    .build());
        }
        return fromSql;
    }

    private static String findSqlField(String field, List<String> camelFields, List<String> labels) {
        if (StrUtil.isBlank(field)) {
            return null;
        }
        for (String c : camelFields) {
            if (field.equals(c) || field.equalsIgnoreCase(c)) {
                return c;
            }
        }
        for (String l : labels) {
            if (field.equals(l) || field.equalsIgnoreCase(l)) {
                return StrUtil.toCamelCase(l);
            }
        }
        String camel = StrUtil.toCamelCase(field);
        for (String c : camelFields) {
            if (camel.equals(c) || camel.equalsIgnoreCase(c)) {
                return c;
            }
        }
        return null;
    }

    private static Object cellValue(Map<String, Object> row, String field) {
        if (row == null || StrUtil.isBlank(field)) {
            return null;
        }
        if (row.containsKey(field)) {
            return row.get(field);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(field)) {
                return e.getValue();
            }
        }
        String camel = StrUtil.toCamelCase(field);
        if (row.containsKey(camel)) {
            return row.get(camel);
        }
        return null;
    }

    private List<ViewExportColumnDTO> inferColumnsFromRows(List<Map<String, Object>> rows) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row != null) {
                    fields.addAll(row.keySet());
                }
            }
        }
        return fields.stream()
                .map(f -> ViewExportColumnDTO.builder().field(f).header(f).exportable(true).visible(true).build())
                .collect(Collectors.toList());
    }

    private FlowApiDO resolveWorkingApi(String apiId, boolean useDraft) {
        FlowApiDO api = flowApiRepository.findById(apiId)
                .orElseThrow(() -> new ValidationException("API 不存在"));
        if (useDraft) {
            return api;
        }
        if (api.getPublishStatus() == null || api.getPublishStatus() != 1
                || StrUtil.isBlank(api.getPublishedSnapshot())) {
            throw new ValidationException("接口未发布，请勾选「使用草稿」或先发布");
        }
        return materializeFromSnapshot(api);
    }

    private FlowApiDO materializeFromSnapshot(FlowApiDO api) {
        try {
            JsonNode snap = MAPPER.readTree(api.getPublishedSnapshot());
            FlowApiDO w = new FlowApiDO();
            w.setId(api.getId());
            w.setName(text(snap, "name", api.getName()));
            w.setUrl(text(snap, "url", api.getUrl()));
            w.setMethod(text(snap, "method", api.getMethod()));
            w.setServiceType(text(snap, "serviceType", api.getServiceType()));
            w.setSqlContent(text(snap, "sqlContent", api.getSqlContent()));
            w.setDslContent(text(snap, "dslContent", api.getDslContent()));
            w.setDatasource(text(snap, "datasource", api.getDatasource()));
            w.setResponseType(text(snap, "responseType", api.getResponseType()));
            w.setContract(text(snap, "contract", api.getContract()));
            w.setViewExportConfig(text(snap, "viewExportConfig", api.getViewExportConfig()));
            w.setPublishStatus(1);
            w.setPublishedSnapshot(api.getPublishedSnapshot());
            return w;
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException("解析发布快照失败");
        }
    }

    private static String text(JsonNode snap, String field, String fallback) {
        if (snap != null && snap.has(field) && !snap.get(field).isNull()) {
            String v = snap.get(field).asText(null);
            if (StrUtil.isNotBlank(v)) {
                return v;
            }
        }
        return fallback;
    }

    private void assertDbQueryable(FlowApiDO api) {
        if (!"DB".equalsIgnoreCase(api.getServiceType())) {
            throw new ValidationException("P0 仅支持 DB 模式接口的数据查看/导出（当前: "
                    + StrUtil.blankToDefault(api.getServiceType(), "-") + "）");
        }
        if (StrUtil.isBlank(api.getSqlContent())) {
            throw new ValidationException("SQL 为空，无法查询");
        }
        if (StrUtil.isBlank(api.getDatasource())) {
            throw new ValidationException("未配置数据源");
        }
        String rt = StrUtil.blankToDefault(api.getResponseType(), "").toUpperCase(Locale.ROOT);
        if (!Set.of("PAGE", "LIST", "OBJECT").contains(rt)) {
            throw new ValidationException("仅支持 PAGE / LIST / OBJECT 响应类型");
        }
    }

    private Map<String, Object> buildExecParams(String contract,
                                                Map<String, String> queryParams,
                                                Map<String, Object> bodyParams,
                                                Map<String, String> pathParams) {
        Map<String, String> q = queryParams == null ? Map.of() : queryParams;
        Map<String, Object> b = bodyParams == null ? Map.of() : bodyParams;
        Map<String, String> p = pathParams == null ? Map.of() : pathParams;

        Map<String, Object> result = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(contract)) {
            Map<String, Object> query = contractParamTypeConverter.convertSection(contract, "query", toObjMap(q));
            Map<String, Object> body = contractParamTypeConverter.convertSection(contract, "body", b);
            Map<String, Object> path = contractParamTypeConverter.convertSection(contract, "pathParams", toObjMap(p));
            result.put("@QP", query);
            result.put("@BP", body);
            result.put("@PP", path);
            result.put("params", query);
            result.put("queryParams", query);
            result.put("body", body);
            result.put("bodyParams", body);
            result.put("pathParams", path);
            result.putAll(query);
            result.putAll(body);
            result.putAll(path);
        } else {
            result.putAll(toObjMap(q));
            result.putAll(b);
            result.putAll(toObjMap(p));
            result.put("queryParams", toObjMap(q));
            result.put("bodyParams", b);
            result.put("pathParams", toObjMap(p));
        }
        return result;
    }

    private static Map<String, Object> toObjMap(Map<String, String> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in != null) {
            in.forEach(out::put);
        }
        return out;
    }

    private ViewExportConfigDTO parseConfig(String json) {
        if (StrUtil.isBlank(json)) {
            return ViewExportConfigDTO.builder().build();
        }
        try {
            ViewExportConfigDTO cfg = MAPPER.readValue(json, ViewExportConfigDTO.class);
            return cfg == null ? ViewExportConfigDTO.builder().build() : cfg;
        } catch (Exception e) {
            log.warn("[ApiDataView] 解析 viewExportConfig 失败: {}", e.getMessage());
            return ViewExportConfigDTO.builder().build();
        }
    }

    /**
     * 列优先级：配置 header → 契约 title → description → 字段名
     */
    private List<ViewExportColumnDTO> resolveColumns(ViewExportConfigDTO cfg, String contract,
                                                     List<Map<String, Object>> sampleRows,
                                                     Map<String, Object> object,
                                                     boolean exportOnly) {
        Map<String, String> titleMap = extractContractTitles(contract);
        List<ViewExportColumnDTO> configured = cfg.getColumns() == null ? List.of() : cfg.getColumns();

        if (!configured.isEmpty()) {
            return configured.stream()
                    .filter(c -> StrUtil.isNotBlank(c.getField()))
                    .filter(c -> {
                        if (exportOnly) {
                            return !Boolean.FALSE.equals(c.getExportable());
                        }
                        return !Boolean.FALSE.equals(c.getVisible());
                    })
                    .map(c -> {
                        String header = StrUtil.blankToDefault(c.getHeader(), null);
                        if (header == null) {
                            header = titleMap.getOrDefault(c.getField(), c.getField());
                        }
                        return ViewExportColumnDTO.builder()
                                .field(c.getField())
                                .header(header)
                                .width(c.getWidth())
                                .exportable(c.getExportable() == null || c.getExportable())
                                .visible(c.getVisible() == null || c.getVisible())
                                .templateKey(StrUtil.blankToDefault(c.getTemplateKey(), c.getField()))
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (object != null) {
            fields.addAll(object.keySet());
        }
        if (sampleRows != null) {
            for (Map<String, Object> row : sampleRows) {
                if (row != null) {
                    fields.addAll(row.keySet());
                }
            }
        }
        if (fields.isEmpty() && !titleMap.isEmpty()) {
            fields.addAll(titleMap.keySet());
        }

        return fields.stream()
                .map(f -> ViewExportColumnDTO.builder()
                        .field(f)
                        .header(titleMap.getOrDefault(f, f))
                        .exportable(true)
                        .visible(true)
                        .templateKey(f)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 从响应契约扁平提取 field → title/description
     */
    private Map<String, String> extractContractTitles(String contractJson) {
        Map<String, String> map = new LinkedHashMap<>();
        if (StrUtil.isBlank(contractJson)) {
            return map;
        }
        try {
            JsonNode root = MAPPER.readTree(contractJson);
            JsonNode body = null;
            JsonNode responses = root.get("responses");
            if (responses != null && responses.isObject()) {
                JsonNode r200 = responses.get("200");
                if (r200 == null) {
                    Iterator<String> it = responses.fieldNames();
                    if (it.hasNext()) {
                        r200 = responses.get(it.next());
                    }
                }
                if (r200 != null) {
                    body = r200.get("body");
                }
            }
            if (body == null) {
                body = root.get("responseBody");
            }
            if (body != null && body.isArray()) {
                collectSchemaTitles(body, map);
            }
        } catch (Exception e) {
            log.debug("[ApiDataView] 解析契约 title 失败: {}", e.getMessage());
        }
        return map;
    }

    private void collectSchemaTitles(JsonNode nodes, Map<String, String> map) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode n : nodes) {
            String name = textOrNull(n, "name");
            String type = textOrNull(n, "type");
            String title = textOrNull(n, "title");
            if (StrUtil.isBlank(title)) {
                title = textOrNull(n, "description");
            }
            JsonNode children = n.get("children");
            if ("array".equalsIgnoreCase(type) && children != null && children.isArray()) {
                // PAGE/LIST 常见：items 数组 → 子字段即列
                collectSchemaTitles(children, map);
                continue;
            }
            if ("object".equalsIgnoreCase(type) && children != null && children.isArray()) {
                if (StrUtil.isNotBlank(name) && !"根节点".equals(name) && !"root".equalsIgnoreCase(name)) {
                    // 嵌套对象：仍展开子字段作为列（扁平）
                }
                collectSchemaTitles(children, map);
                continue;
            }
            if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(title)) {
                map.putIfAbsent(name, title);
            } else if (StrUtil.isNotBlank(name)) {
                map.putIfAbsent(name, name);
            }
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        String v = n.get(field).asText(null);
        return StrUtil.isBlank(v) ? null : v;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castObjectRow(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> row = new LinkedHashMap<>();
            m.forEach((k, v) -> row.put(String.valueOf(k), v));
            return row;
        }
        throw new ValidationException("OBJECT 查询结果不是对象: " + raw.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRows(Object result) {
        if (result == null) {
            return new ArrayList<>();
        }
        if (result instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    rows.add(row);
                }
            }
            return rows;
        }
        if (result instanceof Map<?, ?> m) {
            Map<String, Object> row = new LinkedHashMap<>();
            m.forEach((k, v) -> row.put(String.valueOf(k), v));
            return new ArrayList<>(List.of(row));
        }
        return new ArrayList<>();
    }

    private String buildFileName(String apiName) {
        String base = StrUtil.blankToDefault(apiName, "export")
                .replaceAll("[\\\\/:*?\"<>|]", "_");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return base + "_" + ts + ".xlsx";
    }
}
