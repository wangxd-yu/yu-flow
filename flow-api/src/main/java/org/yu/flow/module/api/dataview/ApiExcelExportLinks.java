package org.yu.flow.module.api.dataview;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.ApiDataExportRequestDTO;
import org.yu.flow.module.api.dto.ApiExcelExportLinkDTO;
import org.yu.flow.module.api.dto.ApiExcelExportLinkRequestDTO;
import org.yu.flow.module.api.dto.ViewExportConfigDTO;
import org.yu.flow.module.api.privacy.PrivacyClass;
import org.yu.flow.module.api.privacy.PrivacyDecision;
import org.yu.flow.module.api.support.ExcelExportLinkToken;
import org.yu.flow.util.JwtTokenUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Excel 短期下载链：管理端签发时固化查询参数与签发人的脱敏决定，下载时按已发布快照导出，无需登录。
 */
@Component
public class ApiExcelExportLinks {

    static final String DOWNLOAD_PATH = "/flow-api/download/excel/";
    private static final int DEFAULT_TTL_SECONDS = 300;
    private static final int MIN_TTL_SECONDS = 30;
    private static final int MAX_TTL_SECONDS = 3600;

    private final ApiDataViewSupport support;
    private final ApiExcelExporter exporter;
    private final DemoModeGuard demoModeGuard;
    private final YuFlowProperties yuFlowProperties;

    public ApiExcelExportLinks(ApiDataViewSupport support, ApiExcelExporter exporter,
                               DemoModeGuard demoModeGuard, YuFlowProperties yuFlowProperties) {
        this.support = support;
        this.exporter = exporter;
        this.demoModeGuard = demoModeGuard;
        this.yuFlowProperties = yuFlowProperties;
    }

    public ApiExcelExportLinkDTO create(String apiId, ApiExcelExportLinkRequestDTO request) {
        FlowApiDO draft = support.requireApi(apiId);
        demoModeGuard.checkModifyOrDelete(apiId, "签发 Excel 下载链");
        if (draft.getPublishStatus() == null || draft.getPublishStatus() != 1
                || StrUtil.isBlank(draft.getPublishedSnapshot())) {
            throw new ValidationException("请先发布接口后再签发下载链（短期链按已发布快照导出，不能使用草稿）");
        }
        FlowApiDO api = support.workingApi(apiId, false);
        ApiDataViewSupport.assertDbQueryable(api);
        ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
        if (!Boolean.TRUE.equals(cfg.getOpenExportEnabled())) {
            throw new ValidationException("已发布快照未开启「对外 Excel 下载」，请保存配置后重新发布再签发");
        }
        if (Boolean.FALSE.equals(cfg.getSignedLinkEnabled())) {
            throw new ValidationException("该接口已关闭短期下载链");
        }
        int ttl = cfg.getSignedLinkTtlSeconds() == null || cfg.getSignedLinkTtlSeconds() <= 0
                ? DEFAULT_TTL_SECONDS : cfg.getSignedLinkTtlSeconds();
        if (request != null && request.getTtlSeconds() != null && request.getTtlSeconds() > 0) {
            ttl = request.getTtlSeconds();
        }
        ttl = Math.min(Math.max(ttl, MIN_TTL_SECONDS), MAX_TTL_SECONDS);

        ApiDataExportRequestDTO params = new ApiDataExportRequestDTO();
        params.setUseDraft(false);
        if (request != null) {
            params.setQueryParams(request.getQueryParams());
            params.setBodyParams(request.getBodyParams());
            params.setPathParams(request.getPathParams());
        }
        PrivacyDecision privacyDecision = support.decide(support.privacy(api, false));
        String token = ExcelExportLinkToken.issue(
                yuFlowProperties, apiId, JwtTokenUtil.currentUsername(), params, ttl,
                privacyDecision.getPrivacyClass().name(), privacyDecision.getFieldActions());
        long exp = Instant.now().getEpochSecond() + ttl;
        return ApiExcelExportLinkDTO.builder()
                .url(DOWNLOAD_PATH + token)
                .expireAt(LocalDateTime.ofEpochSecond(exp, 0, ZoneOffset.ofHours(8))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .ttlSeconds(ttl)
                .build();
    }

    public void download(String token, HttpServletResponse response) {
        ExcelExportLinkToken.Parsed parsed = ExcelExportLinkToken.verify(yuFlowProperties, token);
        FlowApiDO api = support.workingApi(parsed.apiId(), false);
        ApiDataViewSupport.assertDbQueryable(api);
        ViewExportConfigDTO cfg = ApiDataViewSupport.parseConfig(api.getViewExportConfig());
        if (!Boolean.TRUE.equals(cfg.getOpenExportEnabled())) {
            throw new ValidationException("未启用对外 Excel 下载");
        }
        if (Boolean.FALSE.equals(cfg.getEnabled())) {
            throw new ValidationException("该接口已关闭数据导出");
        }
        exporter.export(api, cfg, parsed.request(), response,
                PrivacyDecision.of(
                        PrivacyClass.valueOf(ExcelExportLinkToken.normalizePrivacyClass(parsed.privacyClass())),
                        parsed.fieldActions(),
                        null));
    }
}
