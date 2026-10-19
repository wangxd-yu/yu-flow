package org.yu.flow.module.api.dataview;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.api.dto.ApiExcelTemplateMetaDTO;
import org.yu.flow.module.api.dto.ViewExportColumnDTO;
import org.yu.flow.module.api.dto.ViewExportConfigDTO;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.ExcelTemplateInspector;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 接口 Excel 导出模板：上传（仅 .xlsx、≤2MB、拒绝宏）、删除、下载已上传模板、按当前列生成示例模板。
 * 上传 / 删除同时切换接口导出配置的 exportMode。
 */
@Slf4j
@Component
public class ApiExcelTemplateManager {

    private static final int MAX_TEMPLATE_BYTES = 2 * 1024 * 1024;

    private final FlowApiRepository flowApiRepository;
    private final FlowApiExcelTemplateRepository excelTemplateRepository;
    private final DemoModeGuard demoModeGuard;

    public ApiExcelTemplateManager(FlowApiRepository flowApiRepository,
                                   FlowApiExcelTemplateRepository excelTemplateRepository,
                                   DemoModeGuard demoModeGuard) {
        this.flowApiRepository = flowApiRepository;
        this.excelTemplateRepository = excelTemplateRepository;
        this.demoModeGuard = demoModeGuard;
    }

    public ApiExcelTemplateMetaDTO meta(String apiId) {
        requireApi(apiId);
        return excelTemplateRepository.findByApiId(apiId)
                .map(d -> {
                    ExcelTemplateInspector.ScanResult scan = ExcelTemplateInspector.scan(d.getContent());
                    String warning = scan.hasListPlaceholder() ? null
                            : "模板中未检测到 {.字段} 列表占位符，导出时将自动回退动态表头";
                    return ApiExcelTemplateMetaDTO.fromDO(d, scan.hasListPlaceholder(), warning);
                })
                .orElse(ApiExcelTemplateMetaDTO.empty(apiId));
    }

    public ApiExcelTemplateMetaDTO upload(String apiId, MultipartFile file) {
        FlowApiDO api = requireApi(apiId);
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
        String warning = scan.hasListPlaceholder() ? null
                : "已保存，但未检测到 {.字段} 列表占位符；导出时会回退动态表头。请参考示例模板修改后重新上传。";

        LocalDateTime now = LocalDateTime.now();
        FlowApiExcelTemplateDO entity = excelTemplateRepository.findByApiId(apiId)
                .orElseGet(() -> FlowApiExcelTemplateDO.builder().apiId(apiId).createTime(now).build());
        entity.setFileName(original);
        entity.setContentType(ExcelDownloads.XLSX_MIME);
        entity.setContent(bytes);
        entity.setFileSize(bytes.length);
        entity.setUpdateTime(now);
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        entity = excelTemplateRepository.save(entity);

        ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
        cfg.setExportMode("TEMPLATE");
        cfg.setTemplateFileId(entity.getId());
        if (cfg.getTemplateSheetNo() == null) {
            cfg.setTemplateSheetNo(0);
        }
        saveConfig(api, cfg, now, "回写 viewExportConfig 失败");
        return ApiExcelTemplateMetaDTO.fromDO(entity, scan.hasListPlaceholder(), warning);
    }

    public void delete(String apiId) {
        requireApi(apiId);
        demoModeGuard.checkModifyOrDelete(apiId, "删除 Excel 导出模板");
        excelTemplateRepository.deleteByApiId(apiId);
        flowApiRepository.findById(apiId).ifPresent(api -> {
            ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
            cfg.setExportMode("DYNAMIC");
            cfg.setTemplateFileId(null);
            saveConfig(api, cfg, LocalDateTime.now(), "删除模板后回写配置失败");
        });
    }

    private void saveConfig(FlowApiDO api, ViewExportConfigDTO cfg, LocalDateTime now, String failureLog) {
        try {
            api.setViewExportConfig(ApiDataViewSupport.MAPPER.writeValueAsString(cfg));
            api.setUpdateTime(now);
            flowApiRepository.save(api);
        } catch (Exception e) {
            log.warn("[ApiDataView] {}: {}", failureLog, e.getMessage());
        }
    }

    /** 第一个 Sheet 是标题行 + 表头 + {.字段} 占位符行，第二个 Sheet 是使用说明 */
    public void downloadSample(String apiId, HttpServletResponse response) {
        FlowApiDO api = requireApi(apiId);
        ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
        List<ViewExportColumnDTO> columns = ApiExportColumns.resolve(cfg, api.getContract(), List.of(), null, true);
        if (columns.isEmpty()) {
            columns = List.of(
                    ViewExportColumnDTO.builder().field("field1").header("列1").templateKey("field1").build(),
                    ViewExportColumnDTO.builder().field("field2").header("列2").templateKey("field2").build()
            );
        }
        try {
            ExcelDownloads.applyDownloadHeaders(response, ExcelDownloads.baseName(api.getName()) + "_导出模板示例.xlsx");

            List<List<String>> dataRows = new ArrayList<>();
            dataRows.add(List.of("{apiName} 导出 {exportTime}"));
            List<String> headers = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            for (ViewExportColumnDTO c : columns) {
                if (Boolean.FALSE.equals(c.getExportable())) {
                    continue;
                }
                headers.add(StrUtil.blankToDefault(c.getHeader(), c.getField()));
                String key = ApiExportColumns.templateKey(StrUtil.blankToDefault(c.getTemplateKey(), c.getField()), c.getField());
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
                excelWriter.write(dataRows, EasyExcel.writerSheet(0, "数据").build());
                excelWriter.write(helpRows, EasyExcel.writerSheet(1, "使用说明").build());
            }
        } catch (Exception e) {
            throw new ValidationException("生成示例模板失败: "
                    + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    public void download(String apiId, HttpServletResponse response) {
        FlowApiExcelTemplateDO tpl = excelTemplateRepository.findByApiId(apiId)
                .orElseThrow(() -> new ValidationException("尚未上传导出模板"));
        try {
            ExcelDownloads.applyDownloadHeaders(response, StrUtil.blankToDefault(tpl.getFileName(), "template.xlsx"));
            response.setContentType(StrUtil.blankToDefault(tpl.getContentType(), ExcelDownloads.XLSX_MIME));
            response.getOutputStream().write(tpl.getContent());
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new ValidationException("下载模板失败");
        }
    }

    private FlowApiDO requireApi(String apiId) {
        return flowApiRepository.findById(apiId)
                .orElseThrow(() -> new ValidationException("API 不存在"));
    }
}
