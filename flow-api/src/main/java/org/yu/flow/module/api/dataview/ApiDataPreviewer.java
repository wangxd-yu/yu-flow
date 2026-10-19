package org.yu.flow.module.api.dataview;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.yu.flow.dto.PageBean;
import org.yu.flow.dto.SqlAndParams;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.engine.sql.DynamicSqlParser;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.ApiDataPreviewRequestDTO;
import org.yu.flow.module.api.dto.ApiDataPreviewResultDTO;
import org.yu.flow.module.api.dto.ViewExportColumnDTO;
import org.yu.flow.module.api.dto.ViewExportConfigDTO;
import org.yu.flow.module.api.privacy.EffectivePrivacy;
import org.yu.flow.module.api.privacy.PrivacyClass;
import org.yu.flow.module.api.privacy.PrivacyDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理端数据查看：执行 DB 接口的 SQL 并按表格返回，PAGE 走 SQL 分页，LIST 在内存切片，每页最多 200 行。
 */
@Component
public class ApiDataPreviewer {

    private static final int MAX_PAGE_SIZE = 200;

    private final ApiDataViewSupport support;
    private final SqlExecutorService sqlExecutorService;

    public ApiDataPreviewer(ApiDataViewSupport support, SqlExecutorService sqlExecutorService) {
        this.support = support;
        this.sqlExecutorService = sqlExecutorService;
    }

    public ApiDataPreviewResultDTO preview(String apiId, ApiDataPreviewRequestDTO request) {
        if (request == null) {
            request = new ApiDataPreviewRequestDTO();
        }
        boolean useDraft = Boolean.TRUE.equals(request.getUseDraft());
        FlowApiDO api = support.workingApi(apiId, useDraft);
        ApiDataViewSupport.assertDbQueryable(api);

        ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
        Map<String, Object> params = support.execParams(api.getContract(), request.getQueryParams(),
                request.getBodyParams(), request.getPathParams());
        SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(api.getSqlContent(), params);

        int page = request.getPage() == null ? 0 : Math.max(request.getPage(), 0);
        int size = request.getSize() == null ? 20 : Math.min(Math.max(request.getSize(), 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size);

        String responseType = ApiDataViewSupport.responseType(api);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> object = null;
        Long total;
        Integer pages;

        switch (responseType) {
            case "PAGE" -> {
                Object result = sqlExecutorService.executePageQuery(api.getDatasource(), sqlAndParams, pageable);
                if (result instanceof PageBean<?> pb) {
                    total = pb.getTotal();
                    pages = pb.getPages();
                    rows = ApiDataViewSupport.rows(pb.getItems());
                } else {
                    rows = ApiDataViewSupport.rows(result);
                    total = (long) rows.size();
                    pages = 1;
                }
            }
            case "OBJECT" -> {
                object = ApiDataViewSupport.objectRow(
                        sqlExecutorService.executeObjectQuery(api.getDatasource(), sqlAndParams));
                if (object != null) {
                    rows = List.of(object);
                }
                total = object == null ? 0L : 1L;
                pages = 1;
            }
            default -> {
                rows = ApiDataViewSupport.rows(
                        sqlExecutorService.executeListQuery(api.getDatasource(), sqlAndParams, pageable));
                total = (long) rows.size();
                int from = Math.min(page * size, rows.size());
                int to = Math.min(from + size, rows.size());
                rows = new ArrayList<>(rows.subList(from, to));
                pages = (int) Math.ceil(total / (double) size);
            }
        }

        List<ViewExportColumnDTO> columns = ApiExportColumns.resolve(cfg, api.getContract(), rows, object, false);

        EffectivePrivacy privacy = support.privacy(api, useDraft);
        PrivacyClass privacyClass = PrivacyClass.MASK;
        if (privacy.isEnabled()) {
            PrivacyDecision decision = support.decide(privacy);
            privacyClass = decision.getPrivacyClass();
            rows = support.maskRows(rows, privacy, decision);
            if (object != null) {
                object = rows.isEmpty() ? support.maskObject(object, privacy, decision) : rows.get(0);
            }
            columns = ApiExportColumns.resolve(cfg, api.getContract(), rows, object, false);
        }

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
                .privacyEnabled(privacy.isEnabled())
                .privacyClass(privacy.isEnabled() ? privacyClass.name() : null)
                .build();
    }
}
