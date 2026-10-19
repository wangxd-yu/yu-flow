package org.yu.flow.module.api.service.impl;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.module.api.dataview.ApiDataPreviewer;
import org.yu.flow.module.api.dataview.ApiExcelExportLinks;
import org.yu.flow.module.api.dataview.ApiExcelExporter;
import org.yu.flow.module.api.dataview.ApiExcelTemplateManager;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;
import org.yu.flow.module.api.dto.ApiDataPreviewRequestDTO;
import org.yu.flow.module.api.dto.ApiDataPreviewResultDTO;
import org.yu.flow.module.api.dto.ApiExcelExportLinkDTO;
import org.yu.flow.module.api.dto.ApiExcelExportLinkRequestDTO;
import org.yu.flow.module.api.dto.ApiExcelTemplateMetaDTO;
import org.yu.flow.module.api.service.ApiDataViewService;

/**
 * 接口数据查看与导出入口。资产编辑锁按本接口的方法名拦截（见 AssetEditLockAspect），具体实现在 {@code module.api.dataview}。
 */
@Service
public class ApiDataViewServiceImpl implements ApiDataViewService {

    private final ApiDataPreviewer previewer;
    private final ApiExcelExporter exporter;
    private final ApiExcelExportLinks exportLinks;
    private final ApiExcelTemplateManager templates;

    public ApiDataViewServiceImpl(ApiDataPreviewer previewer, ApiExcelExporter exporter,
                                  ApiExcelExportLinks exportLinks, ApiExcelTemplateManager templates) {
        this.previewer = previewer;
        this.exporter = exporter;
        this.exportLinks = exportLinks;
        this.templates = templates;
    }

    @Override
    public ApiDataPreviewResultDTO preview(String apiId, ApiDataPreviewRequestDTO request) {
        return previewer.preview(apiId, request);
    }

    @Override
    public void exportExcel(String apiId, ApiDataExportRequestDTO request, HttpServletResponse response) {
        exporter.exportExcel(apiId, request, response);
    }

    @Override
    public void exportPublishedOpenExcel(FlowApiDO cachedApi, ApiDataExportRequestDTO request,
                                         HttpServletResponse response) {
        exporter.exportPublishedOpen(cachedApi, request, response);
    }

    @Override
    public ApiExcelExportLinkDTO createExportLink(String apiId, ApiExcelExportLinkRequestDTO request) {
        return exportLinks.create(apiId, request);
    }

    @Override
    public void exportBySignedToken(String token, HttpServletResponse response) {
        exportLinks.download(token, response);
    }

    @Override
    public ApiExcelTemplateMetaDTO getExcelTemplateMeta(String apiId) {
        return templates.meta(apiId);
    }

    @Override
    @Transactional
    public ApiExcelTemplateMetaDTO uploadExcelTemplate(String apiId, MultipartFile file) {
        return templates.upload(apiId, file);
    }

    @Override
    @Transactional
    public void deleteExcelTemplate(String apiId) {
        templates.delete(apiId);
    }

    @Override
    public void downloadSampleExcelTemplate(String apiId, HttpServletResponse response) {
        templates.downloadSample(apiId, response);
    }

    @Override
    public void downloadExcelTemplate(String apiId, HttpServletResponse response) {
        templates.download(apiId, response);
    }
}
