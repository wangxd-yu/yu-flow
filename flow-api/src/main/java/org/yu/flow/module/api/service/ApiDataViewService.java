package org.yu.flow.module.api.service;

import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;
import org.yu.flow.module.api.dto.ApiDataPreviewRequestDTO;
import org.yu.flow.module.api.dto.ApiDataPreviewResultDTO;
import org.yu.flow.module.api.dto.ApiExcelTemplateMetaDTO;

import jakarta.servlet.http.HttpServletResponse;

public interface ApiDataViewService {

    ApiDataPreviewResultDTO preview(String apiId, ApiDataPreviewRequestDTO request);

    void exportExcel(String apiId, ApiDataExportRequestDTO request, HttpServletResponse response);

    ApiExcelTemplateMetaDTO getExcelTemplateMeta(String apiId);

    ApiExcelTemplateMetaDTO uploadExcelTemplate(String apiId, MultipartFile file);

    void deleteExcelTemplate(String apiId);

    void downloadSampleExcelTemplate(String apiId, HttpServletResponse response);

    void downloadExcelTemplate(String apiId, HttpServletResponse response);
}
