package org.yu.flow.module.api.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yu.flow.config.DemoModeGuard;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.FlowApiCacheManager;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.api.dto.BatchApplyDirPrefixResult;
import org.yu.flow.module.api.dto.FlowApiCopyDTO;
import org.yu.flow.module.api.dto.FlowApiDTO;
import org.yu.flow.module.api.dto.FlowApiListProjection;
import org.yu.flow.module.api.query.FlowApiQueryDTO;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.ApiExportPathSupport;
import org.yu.flow.module.api.support.ApiInterceptMode;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.assetversion.service.FlowAssetVersionService;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.log.audit.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FlowApi 业务服务实现 —— 仅负责 CRUD 管理操作
 *
 * @author yu-flow
 */
@Service
public class FlowApiCrudServiceImpl implements FlowApiCrudService {

    /** 分页每页上限 */
    private static final int MAX_PAGE_SIZE = 200;

    @Resource
    private FlowApiReferenceChecker flowApiReferenceChecker;

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private org.yu.flow.module.host.HostCatalogApiBootstrap hostCatalogApiBootstrap;

    @Resource
    private org.yu.flow.module.host.HostReservedApiWriteGuard hostReservedApiWriteGuard;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private FlowApiCacheManager flowApiCacheManager;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private ApiResponseCacheService apiResponseCacheService;

    @Resource
    private FlowAssetVersionService flowAssetVersionService;

    @Resource
    private FlowReferenceIndex flowReferenceIndex;

    @Resource
    private FlowOpenApiGrantRepository flowOpenApiGrantRepository;

    @Resource
    private FlowApiExcelTemplateRepository flowApiExcelTemplateRepository;

    @Resource
    private org.yu.flow.module.release.service.PublishGateService publishGateService;

    @Resource
    private OpenPlatformCache openPlatformCache;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private org.yu.flow.config.YuFlowProperties yuFlowProperties;

    private void assertSecurityConfigAllowed(String securityConfigJson) {
        boolean allowNone = yuFlowProperties.getSecurity() != null
                && yuFlowProperties.getSecurity().isAllowIngressAuthNone();
        org.yu.flow.module.api.security.ApiSecurityConfigGuard.assertAuthModeAllowed(
                securityConfigJson, allowNone);
        org.yu.flow.module.api.security.ApiSecurityConfigGuard.assertCallerPolicyAllowed(securityConfigJson);
    }

    /**
     * 归一化 interceptMode，并校验与 serviceType 的互斥约束。
     */
    private void normalizeAndAssertInterceptMode(FlowApiDO api) {
        if (api == null) {
            return;
        }
        String mode = ApiInterceptMode.normalize(api.getInterceptMode());
        String serviceType = StrUtil.blankToDefault(api.getServiceType(), "").trim().toUpperCase();
        if (ApiInterceptMode.WRAP.equals(mode)) {
            if (!ApiInterceptMode.SERVICE_TYPE_HOST.equals(serviceType)) {
                throw new RuntimeException("包裹模式（WRAP）的实现类型必须为 HOST");
            }
            if (StrUtil.isBlank(api.getHostBinding())) {
                api.setHostBinding("{\"forward\":\"LOCAL\"}");
            }
        } else if (ApiInterceptMode.SERVICE_TYPE_HOST.equals(serviceType)) {
            throw new RuntimeException("实现类型 HOST 仅可用于包裹模式（WRAP）");
        }
        api.setInterceptMode(mode);
        if (api.getServiceType() != null) {
            api.setServiceType(serviceType);
        }
    }

    private void notifyRefIndex() {
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
    }

    private void purgeOpenGrantsForApi(String apiId) {
        try {
            flowOpenApiGrantRepository.deleteByApiId(apiId);
            openPlatformCache.publishRefresh();
        } catch (Exception e) {
            // 表未迁移时不阻断删除 API
        }
    }

    private void purgeOpenGrantsForApis(List<String> apiIds) {
        try {
            flowOpenApiGrantRepository.deleteByApiIdIn(apiIds);
            openPlatformCache.publishRefresh();
        } catch (Exception e) {
            // ignore
        }
    }

    // ============================= FlowApiDO CRUD =============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO save(FlowApiDO flowApiDO) {
        // 请求体直接绑定实体，主键与发布态必须由服务端接管：
        // 自带 id 会让 save() 走 merge 覆盖同 ID 的存量接口（绕过宿主保留守卫、演示守卫与目录锁）；
        // 自带 publishStatus=1 + publishedSnapshot 则能跳过发布门禁与 URL 冲突校验直接上线，
        // 甚至顶掉已发布接口的路由。新建一律落为「未发布草稿」。
        flowApiDO.setId(null);
        flowApiDO.setPublishStatus(0);
        flowApiDO.setPublishedSnapshot(null);
        flowApiDO.setPublishTime(null);
        flowApiDO.setDeleted(0);
        // [Demo 模式] 禁止新建写入类 DB API
        demoModeGuard.checkApiResponseType(flowApiDO.getResponseType());
        flowDirectoryService.assertDirectoryBizType(flowApiDO.getDirectoryId(), "api");
        org.yu.flow.module.host.HostCatalogLocks.assertCreateAllowed(flowApiDO);

        normalizeAndAssertInterceptMode(flowApiDO);
        if (flowApiDO.getLogEnabled() == null) {
            // WRAP 默认关日志，避免宿主流量打爆；REPLACE 保持历史默认开
            flowApiDO.setLogEnabled(!ApiInterceptMode.isWrap(flowApiDO.getInterceptMode()));
        }
        if (StrUtil.isBlank(flowApiDO.getLogMode())) {
            flowApiDO.setLogMode("SYSTEM_DEFAULT");
        }
        // 保留天数 <0 视为未配置（跟随系统）
        if (flowApiDO.getLogRetentionDays() != null && flowApiDO.getLogRetentionDays() < 0) {
            flowApiDO.setLogRetentionDays(null);
        }
        assertUrlNotReserved(flowApiDO.getUrl());
        assertSecurityConfigAllowed(flowApiDO.getSecurityConfig());
        flowApiDO.setCreateTime(LocalDateTime.now());
        flowApiDO = flowApiRepository.save(flowApiDO);
        notifyRefIndex();
        return flowApiDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FlowApiDO> batchSave(List<FlowApiDO> flowApiDOList) {
        if (flowApiDOList == null || flowApiDOList.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        boolean needRefreshCache = false;

        for (FlowApiDO api : flowApiDOList) {
            // 批量新增同样只接受新记录：带 id 会走 merge 覆盖存量接口，
            // 带 publishedSnapshot 则可伪造线上快照。发布态保留给低代码建模的「生成即上线」。
            api.setId(null);
            api.setPublishedSnapshot(null);
            api.setPublishTime(null);
            api.setDeleted(0);
            org.yu.flow.module.host.HostCatalogLocks.assertCreateAllowed(api);
            demoModeGuard.checkApiResponseType(api.getResponseType());
            normalizeAndAssertInterceptMode(api);
            if (api.getLogEnabled() == null) {
                api.setLogEnabled(!ApiInterceptMode.isWrap(api.getInterceptMode()));
            }
            if (StrUtil.isBlank(api.getLogMode())) {
                api.setLogMode("SYSTEM_DEFAULT");
            }
            // 保留天数 <0 视为未配置（跟随系统）
            if (api.getLogRetentionDays() != null && api.getLogRetentionDays() < 0) {
                api.setLogRetentionDays(null);
            }
            assertSecurityConfigAllowed(api.getSecurityConfig());
            api.setCreateTime(now);
            if (!needRefreshCache && api.getPublishStatus() != null && api.getPublishStatus().equals(1)) {
                needRefreshCache = true;
            }
        }

        // 统一在单个事务内批量保存，比外层 for 循环单条 save 减少大量事务开销和数据库交互
        List<FlowApiDO> savedList = flowApiRepository.saveAll(flowApiDOList);

        // 如果该批次中有任何已发布的 API，只发出【一次】缓存刷新事件
        // 彻底解决原来的 "100次批量插入 = 100次DB保存 + 100次全网刷新风暴"
        if (needRefreshCache) {
            flowApiCacheManager.publishRefreshEvent();
        }
        notifyRefIndex();
        return savedList;
    }

    @Override
    @Transactional
    public FlowApiDO update(FlowApiDO flowApiDO) {
        hostReservedApiWriteGuard.assertAllowed(flowApiDO.getId());
        // [Demo 模式] 系统预置 API 不可修改；禁止改为写入类 API
        demoModeGuard.checkModifyOrDelete(flowApiDO.getId(), "API 接口");
        demoModeGuard.checkApiResponseType(flowApiDO.getResponseType());
        flowDirectoryService.assertDirectoryBizType(flowApiDO.getDirectoryId(), "api");

        Optional<FlowApiDO> existing = flowApiRepository.findById(flowApiDO.getId());
        if (!existing.isPresent() && org.yu.flow.module.host.HostCatalogReserved.isReservedId(flowApiDO.getId())) {
            hostCatalogApiBootstrap.ensureReservedApi(flowApiDO.getId());
            existing = flowApiRepository.findById(flowApiDO.getId());
        }
        if (!existing.isPresent()) {
            throw new RuntimeException("配置不存在，id: " + flowApiDO.getId());
        }
        FlowApiDO dbRecord = existing.get();
        org.yu.flow.module.host.HostCatalogLocks.assertUpdateAllowed(flowApiDO, dbRecord);
        flowApiDO.setCreateTime(dbRecord.getCreateTime());
        flowApiDO.setUpdateTime(LocalDateTime.now());

        // ── 版本快照策略 ──
        // 保留已有的发布快照和发布时间，草稿编辑不影响线上
        flowApiDO.setPublishedSnapshot(dbRecord.getPublishedSnapshot());
        flowApiDO.setPublishTime(dbRecord.getPublishTime());
        if (flowApiDO.getLogEnabled() == null) {
            flowApiDO.setLogEnabled(dbRecord.getLogEnabled());
        }
        if (StrUtil.isBlank(flowApiDO.getLogMode())) {
            flowApiDO.setLogMode(StrUtil.blankToDefault(dbRecord.getLogMode(), "SYSTEM_DEFAULT"));
        }
        // 保留天数：null=未传保留旧值，-1=清除 API 级配置（回退系统），0=永久保留，>0=自定义天数
        if (flowApiDO.getLogRetentionDays() == null) {
            flowApiDO.setLogRetentionDays(dbRecord.getLogRetentionDays());
        } else if (flowApiDO.getLogRetentionDays() < 0) {
            flowApiDO.setLogRetentionDays(null);
        }
        if (flowApiDO.getCacheConfig() == null) {
            flowApiDO.setCacheConfig(dbRecord.getCacheConfig());
        }
        if (flowApiDO.getSecurityConfig() == null) {
            flowApiDO.setSecurityConfig(dbRecord.getSecurityConfig());
        }
        if (flowApiDO.getViewExportConfig() == null) {
            flowApiDO.setViewExportConfig(dbRecord.getViewExportConfig());
        }
        if (StrUtil.isBlank(flowApiDO.getInterceptMode())) {
            flowApiDO.setInterceptMode(dbRecord.getInterceptMode());
        }
        if (flowApiDO.getHostBinding() == null) {
            flowApiDO.setHostBinding(dbRecord.getHostBinding());
        }
        normalizeAndAssertInterceptMode(flowApiDO);
        assertUrlNotReserved(flowApiDO.getUrl());
        assertSecurityConfigAllowed(flowApiDO.getSecurityConfig());
        boolean cacheConfigChanged = !Objects.equals(
                StrUtil.nullToEmpty(flowApiDO.getCacheConfig()),
                StrUtil.nullToEmpty(dbRecord.getCacheConfig()));
        boolean cacheWasEnabled = isResponseCacheEnabled(dbRecord.getCacheConfig());
        boolean cacheNowEnabled = isResponseCacheEnabled(flowApiDO.getCacheConfig());

        flowApiRepository.save(flowApiDO);

        // 如果当前是未发布状态，仍然刷新缓存（兑容旧逻辑）
        // 已发布状态下编辑草稿不刷新缓存（快照隔离）
        // 例外：cacheConfig 为运行时配置，变更需即时刷新 L1
        if (flowApiDO.getPublishStatus() == null || flowApiDO.getPublishStatus() != 1 || cacheConfigChanged) {
            flowApiCacheManager.publishRefreshEvent();
        }
        // 停用缓存，或缓存配置有变更时，清空该接口已有响应缓存
        if (cacheConfigChanged && (cacheWasEnabled || cacheNowEnabled)) {
            apiResponseCacheService.evictAll(flowApiDO.getId());
        }
        notifyRefIndex();
        return flowApiDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO copy(String id, FlowApiCopyDTO copyDTO) {
        FlowApiDO source = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));

        String name = StrUtil.blankToDefault(
                copyDTO != null ? StrUtil.trim(copyDTO.getName()) : null,
                StrUtil.nullToEmpty(source.getName()) + "_副本");
        String url = normalizeApiUrl(StrUtil.blankToDefault(
                copyDTO != null ? StrUtil.trim(copyDTO.getUrl()) : null,
                source.getUrl()));
        if (StrUtil.isBlank(url)) {
            throw new RuntimeException("复制失败：接口 path 不能为空");
        }
        String directoryId = StrUtil.blankToDefault(
                copyDTO != null ? StrUtil.trim(copyDTO.getDirectoryId()) : null,
                source.getDirectoryId());

        FlowApiDO copy = FlowApiDO.builder()
                .name(name)
                .url(url)
                .directoryId(directoryId)
                .responseType(source.getResponseType())
                .version(source.getVersion())
                .method(source.getMethod())
                .serviceType(source.getServiceType())
                .interceptMode(source.getInterceptMode())
                .hostBinding(source.getHostBinding())
                .dslContent(source.getDslContent())
                .sqlContent(source.getSqlContent())
                .jsonContent(source.getJsonContent())
                .textContent(source.getTextContent())
                .datasource(source.getDatasource())
                .logEnabled(source.getLogEnabled())
                .logMode(source.getLogMode())
                .logRetentionDays(source.getLogRetentionDays())
                .cacheConfig(source.getCacheConfig())
                .securityConfig(source.getSecurityConfig())
                .privacyConfig(source.getPrivacyConfig())
                .viewExportConfig(source.getViewExportConfig())
                .level(source.getLevel())
                .templateId(source.getTemplateId())
                .customSuccessWrapper(source.getCustomSuccessWrapper())
                .customPageWrapper(source.getCustomPageWrapper())
                .customFailWrapper(source.getCustomFailWrapper())
                .info(source.getInfo())
                .tags(source.getTags())
                .contract(source.getContract())
                // 副本一律为未发布草稿，不继承线上快照
                .publishStatus(0)
                .publishedSnapshot(null)
                .publishTime(null)
                .deleted(0)
                .build();

        FlowApiDO saved = save(copy);
        copyExcelTemplate(source.getId(), saved.getId());
        copyOpenGrants(source.getId(), saved.getId());

        auditLogService.record("API_COPY", "API", saved.getId(),
                "{\"sourceId\":\"" + StrUtil.nullToEmpty(source.getId())
                        + "\",\"method\":\"" + StrUtil.nullToEmpty(saved.getMethod())
                        + "\",\"url\":\"" + StrUtil.nullToEmpty(saved.getUrl())
                        + "\",\"name\":\"" + StrUtil.nullToEmpty(saved.getName()) + "\"}");
        return saved;
    }

    /** 复制已上传的 Excel 导出模板（表内 api_id 唯一，副本单独存一份） */
    private void copyExcelTemplate(String sourceApiId, String targetApiId) {
        flowApiExcelTemplateRepository.findByApiId(sourceApiId).ifPresent(template -> {
            LocalDateTime now = LocalDateTime.now();
            flowApiExcelTemplateRepository.save(FlowApiExcelTemplateDO.builder()
                    .apiId(targetApiId)
                    .fileName(template.getFileName())
                    .contentType(template.getContentType())
                    .content(template.getContent())
                    .fileSize(template.getFileSize())
                    .createTime(now)
                    .updateTime(now)
                    .build());
        });
    }

    /** 复制开放平台授权关系；副本未发布，授权在发布后才实际生效 */
    private void copyOpenGrants(String sourceApiId, String targetApiId) {
        try {
            List<FlowOpenApiGrantDO> grants = flowOpenApiGrantRepository.findByApiId(sourceApiId);
            if (grants.isEmpty()) {
                return;
            }
            List<FlowOpenApiGrantDO> copies = grants.stream()
                    .map(g -> new FlowOpenApiGrantDO()
                            .setPlatformId(g.getPlatformId())
                            .setApiId(targetApiId)
                            .setAllowMethods(g.getAllowMethods()))
                    .collect(Collectors.toList());
            flowOpenApiGrantRepository.saveAll(copies);
            openPlatformCache.publishRefresh();
        } catch (Exception e) {
            // 表未迁移时不阻断接口复制
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        org.yu.flow.module.host.HostCatalogLocks.assertNotDeleted(id);
        // [Demo 模式] 系统预置 API 不可删除
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        flowApiReferenceChecker.assertDeletable(id);
        purgeOpenGrantsForApi(id);
        flowApiRepository.deleteById(id);
        apiResponseCacheService.evictAll(id);
        flowApiCacheManager.publishRefreshEvent();
        notifyRefIndex();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            org.yu.flow.module.host.HostCatalogLocks.assertNotDeleted(ids);
            // [Demo 模式] 逐一检查，只要有一个受保护的 ID 就整体拒绝
            ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "API 接口"));
            ids.forEach(flowApiReferenceChecker::assertDeletable);
            purgeOpenGrantsForApis(ids);
            flowApiRepository.logicDeleteByIds(ids);
            ids.forEach(apiResponseCacheService::evictAll);
            flowApiCacheManager.publishRefreshEvent();
            notifyRefIndex();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(List<String> ids, String targetDirectoryId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        org.yu.flow.module.host.HostCatalogLocks.assertNotMoved(ids);
        ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "API 接口"));
        if ("0".equals(targetDirectoryId) || StrUtil.isBlank(targetDirectoryId)) {
            targetDirectoryId = null;
        }
        flowApiRepository.updateDirectoryIdByIds(targetDirectoryId, ids);
        flowApiCacheManager.publishRefreshEvent();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchApplyDirPrefixResult batchApplyDirPrefix(List<String> ids, String oldPrefix) {
        BatchApplyDirPrefixResult result = new BatchApplyDirPrefixResult();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        hostReservedApiWriteGuard.assertAllowed(ids);
        ids.forEach(id -> demoModeGuard.checkModifyOrDelete(id, "API 接口"));
        List<FlowApiDO> apis = flowApiRepository.findAllById(ids);
        Map<String, FlowApiDO> byId = apis.stream()
                .collect(Collectors.toMap(FlowApiDO::getId, a -> a, (a, b) -> a));

        List<String> urlsForLcp = new ArrayList<>();
        for (String id : ids) {
            FlowApiDO api = byId.get(id);
            if (api == null || StrUtil.isBlank(api.getUrl())) {
                continue;
            }
            if (ApiInterceptMode.isWrap(api.getInterceptMode())) {
                continue;
            }
            urlsForLcp.add(api.getUrl());
        }
        String oldUsed = StrUtil.isNotBlank(oldPrefix)
                ? normalizePathPrefixSegment(oldPrefix)
                : longestCommonPathPrefix(urlsForLcp);
        result.setOldPrefixUsed(oldUsed);

        // 批内已占用的新 method+url，避免互相冲突
        Set<String> claimed = new HashSet<>();
        boolean needDraftCacheRefresh = false;

        for (String id : ids) {
            FlowApiDO api = byId.get(id);
            BatchApplyDirPrefixResult.Item item = new BatchApplyDirPrefixResult.Item();
            item.setId(id);
            if (api == null) {
                item.setStatus("failed");
                item.setMessage("接口不存在");
                result.setFailed(result.getFailed() + 1);
                result.getItems().add(item);
                continue;
            }
            item.setName(api.getName());
            item.setFrom(api.getUrl());
            try {
                demoModeGuard.checkModifyOrDelete(id, "API 接口");
            } catch (RuntimeException e) {
                item.setStatus("failed");
                item.setMessage(e.getMessage());
                result.setFailed(result.getFailed() + 1);
                result.getItems().add(item);
                continue;
            }
            if (ApiInterceptMode.isWrap(api.getInterceptMode())) {
                item.setStatus("skipped");
                item.setMessage("包裹模式接口跳过");
                item.setTo(api.getUrl());
                result.setSkipped(result.getSkipped() + 1);
                result.getItems().add(item);
                continue;
            }
            if (StrUtil.isBlank(api.getDirectoryId())) {
                item.setStatus("skipped");
                item.setMessage("未归属目录");
                item.setTo(api.getUrl());
                result.setSkipped(result.getSkipped() + 1);
                result.getItems().add(item);
                continue;
            }
            String newPrefix = flowDirectoryService.resolveEffectivePathPrefix(api.getDirectoryId());
            newPrefix = normalizePathPrefixSegment(newPrefix);
            if (StrUtil.isBlank(newPrefix)) {
                item.setStatus("skipped");
                item.setMessage("目录无有效 pathPrefix");
                item.setTo(api.getUrl());
                result.setSkipped(result.getSkipped() + 1);
                result.getItems().add(item);
                continue;
            }
            String fromUrl = normalizeApiUrl(api.getUrl());
            if (StrUtil.isBlank(fromUrl)) {
                item.setStatus("skipped");
                item.setMessage("接口 path 为空");
                item.setTo(api.getUrl());
                result.setSkipped(result.getSkipped() + 1);
                result.getItems().add(item);
                continue;
            }
            String relative;
            if (StrUtil.isBlank(oldUsed)) {
                relative = fromUrl;
            } else if (fromUrl.equals(oldUsed) || fromUrl.startsWith(oldUsed + "/")) {
                relative = fromUrl.equals(oldUsed) ? "/" : fromUrl.substring(oldUsed.length());
                if (relative.isEmpty()) {
                    relative = "/";
                } else if (!relative.startsWith("/")) {
                    relative = "/" + relative;
                }
            } else {
                item.setStatus("skipped");
                item.setMessage("path 不以旧前缀开头：" + oldUsed);
                item.setTo(fromUrl);
                result.setSkipped(result.getSkipped() + 1);
                result.getItems().add(item);
                continue;
            }
            String toUrl = joinPathPrefixes(newPrefix, relative);
            item.setTo(toUrl);
            if (fromUrl.equals(toUrl)) {
                item.setStatus("skipped");
                item.setMessage("无需变更");
                result.setSkipped(result.getSkipped() + 1);
                result.getItems().add(item);
                continue;
            }
            try {
                assertUrlNotReserved(toUrl);
            } catch (RuntimeException e) {
                item.setStatus("failed");
                item.setMessage(e.getMessage());
                result.setFailed(result.getFailed() + 1);
                result.getItems().add(item);
                continue;
            }
            String method = StrUtil.blankToDefault(api.getMethod(), "GET").trim().toUpperCase();
            String claimKey = method + " " + toUrl;
            // 本批占用 / 其它接口草稿 url / 已发布快照 任一冲突则拒绝
            if (claimed.contains(claimKey)
                    || flowApiRepository.existsDraftByUrlAndMethod(toUrl, method, id)
                    || existsByUrlAndMethod(toUrl, method, id)) {
                item.setStatus("failed");
                item.setMessage("与其它接口路径冲突（含未发布草稿）: " + method + " " + toUrl);
                result.setFailed(result.getFailed() + 1);
                result.getItems().add(item);
                continue;
            }
            claimed.add(claimKey);
            api.setUrl(toUrl);
            api.setUpdateTime(LocalDateTime.now());
            flowApiRepository.save(api);
            item.setStatus("updated");
            item.setMessage("已更新草稿");
            result.setUpdated(result.getUpdated() + 1);
            if (api.getPublishStatus() != null && api.getPublishStatus() == 1) {
                result.setPublishedTouched(result.getPublishedTouched() + 1);
                item.setMessage("已更新草稿（已发布，需重新发布后线上生效）");
            } else {
                needDraftCacheRefresh = true;
            }
            result.getItems().add(item);
        }
        if (needDraftCacheRefresh) {
            flowApiCacheManager.publishRefreshEvent();
        }
        if (result.getUpdated() > 0) {
            notifyRefIndex();
        }
        return result;
    }

    /** 规范化路径前缀：保证以 / 开头、无尾 / */
    private static String normalizePathPrefixSegment(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (!v.startsWith("/")) {
            v = "/" + v;
        }
        while (v.length() > 1 && v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String joinPathPrefixes(String prefix, String relative) {
        String p = normalizePathPrefixSegment(prefix);
        String r = normalizeApiUrl(relative);
        if (StrUtil.isBlank(p)) {
            return r;
        }
        if (StrUtil.isBlank(r) || "/".equals(r)) {
            return p;
        }
        return p + r;
    }

    /** 按 / 分段的最长公共前缀；无公共段返回 null */
    static String longestCommonPathPrefix(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        List<String[]> partsList = new ArrayList<>();
        for (String u : urls) {
            String n = normalizeApiUrl(u);
            if (StrUtil.isBlank(n) || "/".equals(n)) {
                return null;
            }
            String trimmed = n.startsWith("/") ? n.substring(1) : n;
            String[] parts = trimmed.split("/");
            if (parts.length == 0 || (parts.length == 1 && parts[0].isEmpty())) {
                return null;
            }
            partsList.add(parts);
        }
        String[] first = partsList.get(0);
        int common = first.length;
        for (int i = 1; i < partsList.size(); i++) {
            String[] cur = partsList.get(i);
            int m = Math.min(common, cur.length);
            int j = 0;
            while (j < m && Objects.equals(first[j], cur[j])) {
                j++;
            }
            common = j;
            if (common == 0) {
                return null;
            }
        }
        // 至少保留一段；若公共等于某条完整 path，仍可作为旧前缀
        if (common <= 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < common; i++) {
            sb.append('/').append(first[i]);
        }
        return sb.toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO updateLogEnabled(String id, boolean enabled) {
        hostReservedApiWriteGuard.assertAllowed(id);
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));
        api.setLogEnabled(enabled);
        FlowApiDO saved = flowApiRepository.save(api);
        if (saved.getPublishStatus() != null && saved.getPublishStatus() == 1) {
            flowApiCacheManager.publishRefreshEvent();
        }
        return saved;
    }

    /** 判断 cache_config JSON 是否启用响应缓存 */
    private boolean isResponseCacheEnabled(String cacheConfigJson) {
        if (StrUtil.isBlank(cacheConfigJson)) {
            return false;
        }
        try {
            var cfg = apiResponseCacheService.parseConfig(cacheConfigJson);
            return cfg != null && cfg.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO updateCacheConfig(String id, String cacheConfig) {
        hostReservedApiWriteGuard.assertAllowed(id);
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));
        // 校验 JSON 合法性（空串视为清空）
        if (StrUtil.isNotBlank(cacheConfig)) {
            if (apiResponseCacheService.parseConfig(cacheConfig) == null) {
                throw new IllegalArgumentException("cacheConfig JSON 无效");
            }
        } else {
            cacheConfig = null;
        }
        String oldConfig = api.getCacheConfig();
        api.setCacheConfig(cacheConfig);
        api.setUpdateTime(LocalDateTime.now());
        FlowApiDO saved = flowApiRepository.save(api);
        if (saved.getPublishStatus() != null && saved.getPublishStatus() == 1) {
            flowApiCacheManager.publishRefreshEvent();
        }
        // 配置变更后清掉旧响应缓存，避免 key 规则不一致残留
        if (!Objects.equals(StrUtil.nullToEmpty(oldConfig), StrUtil.nullToEmpty(cacheConfig))) {
            apiResponseCacheService.evictAll(id);
        }
        return saved;
    }

    @Override
    public FlowApiDO findById(String id) {
        return flowApiRepository.findById(id).orElse(null);
    }

    @Override
    public FlowApiDO findByUrl(String url) {
        return flowApiRepository.findByUrlAndPublishStatus(url, 1).orElse(null);
    }

    @Override
    public List<String> findAllUrls() {
        return flowApiRepository.findByPublishStatus(1).stream()
                .filter(a -> !org.yu.flow.module.host.HostCatalogReserved.isReservedId(a.getId()))
                .map(FlowApiDO::getUrl)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlowApiDTO> findAll() {
        return flowApiRepository.findAll().stream()
                .filter(a -> !org.yu.flow.module.host.HostCatalogReserved.isReservedId(a.getId()))
                .map(FlowApiDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<FlowApiDTO> findAll(Pageable pageableIn) {
        Pageable pageable = PageRequest.of(Math.max(pageableIn.getPageNumber() - 1, 0), pageableIn.getPageSize(), Sort.by(Sort.Direction.DESC, "createTime"));
        Page<FlowApiListProjection> page = flowApiRepository.findPageWithoutLargeFields(
                org.yu.flow.module.host.HostCatalogReserved.ids(), pageable);
        List<FlowApiDTO> dtoList = page.getContent().stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());

        // 批量获取 directoryName（内存拼装，避免 N+1）
        enrichDirectoryNames(dtoList);

        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    /**
     * 列表投影转换为 DTO：不携带大字段（dslContent/sqlContent/jsonContent/textContent/contract/
     * publishedSnapshot/custom*Wrapper/securityConfig 等），减少网络传输与内存占用。
     */
    private FlowApiDTO toListDTO(FlowApiListProjection p) {
        FlowApiDTO dto = new FlowApiDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setInfo(p.getInfo());
        dto.setUrl(p.getUrl());
        dto.setDatasource(p.getDatasource());
        dto.setDirectoryId(p.getDirectoryId());
        dto.setResponseType(p.getResponseType());
        dto.setVersion(p.getVersion());
        dto.setMethod(p.getMethod());
        dto.setServiceType(p.getServiceType());
        dto.setInterceptMode(p.getInterceptMode() == null || p.getInterceptMode().isBlank()
                ? "REPLACE" : p.getInterceptMode());
        dto.setPublishStatus(p.getPublishStatus());
        dto.setLogEnabled(p.getLogEnabled() == null || p.getLogEnabled());
        dto.setLevel(p.getLevel());
        dto.setTags(p.getTags());
        dto.setTemplateId(p.getTemplateId());
        dto.setPublishTime(p.getPublishTime());
        dto.setDeleted(p.getDeleted());
        dto.setCreateTime(p.getCreateTime());
        dto.setUpdateTime(p.getUpdateTime());
        dto.setCacheConfig(p.getCacheConfig());
        dto.setSecurityConfig(p.getSecurityConfig());
        dto.setHasUnpublishedChanges(hasUnpublishedChanges(p));
        return dto;
    }

    /** 列表场景下用更新时间 vs 发布时间粗略判断是否存在未发布的草稿变更。 */
    private boolean hasUnpublishedChanges(FlowApiListProjection p) {
        return p.getPublishStatus() != null && p.getPublishStatus() == 1
                && p.getUpdateTime() != null && p.getPublishTime() != null
                && p.getUpdateTime().isAfter(p.getPublishTime());
    }

    @Override
    public PageBean<FlowApiDTO> findPage(FlowApiQueryDTO queryDTO) {
        // 分页参数来自 query string：负页码会让 PageRequest 直接抛异常，超大 size 则整表拉进内存
        int pageNo = Math.max(0, queryDTO.getPage());
        int pageSize = Math.min(Math.max(1, queryDTO.getSize()), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));

        List<String> directoryIds = Collections.singletonList("");
        boolean directoryIdsEmpty = true;
        if (StrUtil.isNotBlank(queryDTO.getDirectoryId())) {
            List<String> dirIds = flowDirectoryService.getAllChildIds(queryDTO.getDirectoryId());
            if (!dirIds.isEmpty()) {
                directoryIds = dirIds;
                directoryIdsEmpty = false;
            } else {
                directoryIds = Collections.singletonList("-1");
                directoryIdsEmpty = false;
            }
        }

        String name = StrUtil.isBlank(queryDTO.getName()) ? null : queryDTO.getName();
        String method = StrUtil.isBlank(queryDTO.getMethod()) ? null : queryDTO.getMethod();
        String url = StrUtil.isBlank(queryDTO.getUrl()) ? null : queryDTO.getUrl();
        String serviceType = StrUtil.isBlank(queryDTO.getServiceType()) ? null : queryDTO.getServiceType();
        Integer publishStatus = queryDTO.getPublishStatus();

        Page<FlowApiListProjection> result = flowApiRepository.findPageWithoutLargeFields(
                directoryIds,
                directoryIdsEmpty,
                name,
                method,
                url,
                publishStatus == null,
                publishStatus == null ? -1 : publishStatus,
                serviceType,
                org.yu.flow.module.host.HostCatalogReserved.ids(),
                pageable);

        List<FlowApiDTO> content = result.getContent().stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());

        // 批量获取 directoryName
        enrichDirectoryNames(content);

        return new PageBean<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Override
    public List<FlowApiDTO> findByPublishStatus(Integer publishStatus) {
        return flowApiRepository.findByPublishStatus(publishStatus).stream()
                .filter(a -> !org.yu.flow.module.host.HostCatalogReserved.isReservedId(a.getId()))
                .map(FlowApiDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlowApiDO> findPublishApi() {
        return flowApiRepository.findByPublishStatus(1).stream()
                .filter(a -> !org.yu.flow.module.host.HostCatalogReserved.isReservedId(a.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public FlowApiDTO findByName(String name) {
        return FlowApiDTO.fromDO(flowApiRepository.findByName(name));
    }

    @Override
    public boolean existsByUrlAndMethod(String url, String method, String excludeId) {
        if (StrUtil.isBlank(url) || StrUtil.isBlank(method)) {
            return false;
        }
        String normalizedUrl = normalizeApiUrl(url);
        String normalizedMethod = method.trim().toUpperCase();
        for (FlowApiDO api : flowApiRepository.findByPublishStatus(1)) {
            if (StrUtil.isNotBlank(excludeId) && excludeId.equals(api.getId())) {
                continue;
            }
            String pubUrl = normalizeApiUrl(PublishedApiSnapshot.resolveUrl(api));
            String pubMethod = PublishedApiSnapshot.resolveMethod(api);
            if (pubUrl == null || StrUtil.isBlank(pubMethod)) {
                continue;
            }
            if (normalizedUrl.equals(pubUrl) && normalizedMethod.equals(pubMethod.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeApiUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static void assertUrlNotReserved(String url) {
        if (ApiExportPathSupport.urlEndsWithExportSuffix(url)) {
            throw new RuntimeException("接口 URL 不能以 /export 结尾（该后缀保留给对外 Excel 下载）");
        }
    }

    // ============================= 发布/下线/回滚 =============================

    private static final ObjectMapper SNAPSHOT_MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    /**
     * 发布 API：将草稿内容冻结为发布快照
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO publish(String id) {
        return publish(id, "DEV");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO publish(String id, String envCode) {
        hostReservedApiWriteGuard.assertAllowed(id);
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        publishGateService.assertCanPublish("API", id, envCode);
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));

        if (existsByUrlAndMethod(api.getUrl(), api.getMethod(), id)) {
            throw new RuntimeException("发布失败：路径 " + api.getMethod() + " " + api.getUrl()
                    + " 与其他已发布接口冲突");
        }
        normalizeAndAssertInterceptMode(api);
        assertUrlNotReserved(api.getUrl());
        assertSecurityConfigAllowed(api.getSecurityConfig());

        // 生成快照 JSON
        String snapshot = buildSnapshot(api);
        api.setPublishedSnapshot(snapshot);
        api.setPublishTime(LocalDateTime.now());
        api.setPublishStatus(1);
        flowApiRepository.save(api);
        flowAssetVersionService.append(
                AssetBizType.API, id, snapshot, AssetBizType.SOURCE_PUBLISH, null, JwtTokenUtil.currentUsername());

        // 刷新缓存，线上生效
        flowApiCacheManager.publishRefreshEvent();
        notifyRefIndex();
        String env = org.yu.flow.module.release.support.RegressionSecurity.normalizeEnvCode(envCode);
        auditLogService.record("API_PUBLISH", "API", id,
                "{\"method\":\"" + StrUtil.nullToEmpty(api.getMethod())
                        + "\",\"url\":\"" + StrUtil.nullToEmpty(api.getUrl())
                        + "\",\"name\":\"" + StrUtil.nullToEmpty(api.getName())
                        + "\",\"env\":\"" + env + "\"}");
        return api;
    }

    /**
     * 下线 API：清除快照，线上停止服务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO unpublish(String id) {
        hostReservedApiWriteGuard.assertAllowed(id);
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));

        api.setPublishStatus(0);
        api.setPublishedSnapshot(null);
        flowApiRepository.save(api);

        apiResponseCacheService.evictAll(id);
        flowApiCacheManager.publishRefreshEvent();
        // 授权保留但网关 404；刷新开放缓存以便文档/授权视图尽快感知
        try {
            openPlatformCache.publishRefresh();
        } catch (Exception ignored) {
        }
        notifyRefIndex();
        return api;
    }

    /**
     * 回滚草稿：将发布快照中的内容复制回草稿字段
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO rollbackToPublished(String id) {
        hostReservedApiWriteGuard.assertAllowed(id);
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));

        if (api.getPublishedSnapshot() == null) {
            throw new RuntimeException("该 API 没有发布快照，无法回滚");
        }

        applySnapshotToDraft(api, api.getPublishedSnapshot());
        api.setUpdateTime(api.getPublishTime()); // 将 updateTime 对齐到发布时间，消除变更标记
        flowApiRepository.save(api);
        notifyRefIndex();
        return api;
    }

    /**
     * 重新发布：将最新草稿重新冻结为快照
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO republish(String id) {
        return publish(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowAssetVersionDTO> listVersions(String id) {
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));
        return flowAssetVersionService.list(AssetBizType.API, id, api.getPublishedSnapshot());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowApiDO restoreVersion(String id, String versionId) {
        hostReservedApiWriteGuard.assertAllowed(id);
        demoModeGuard.checkModifyOrDelete(id, "API 接口");
        FlowApiDO api = flowApiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("API 不存在，id: " + id));
        FlowAssetVersionDO version = flowAssetVersionService.requireOwned(versionId, AssetBizType.API, id);
        LocalDateTime now = LocalDateTime.now();
        // 线上快照 + 草稿同步为该历史版本，避免编辑器仍显示回退前内容
        applySnapshotToDraft(api, version.getSnapshot());
        api.setPublishedSnapshot(version.getSnapshot());
        api.setPublishStatus(1);
        api.setPublishTime(now);
        api.setUpdateTime(now);
        flowApiRepository.save(api);
        flowAssetVersionService.append(
                AssetBizType.API, id, version.getSnapshot(), AssetBizType.SOURCE_ROLLBACK,
                "回退至 v" + version.getVersionNo(), JwtTokenUtil.currentUsername());
        apiResponseCacheService.evictAll(id);
        flowApiCacheManager.publishRefreshEvent();
        notifyRefIndex();
        return api;
    }

    /**
     * 将快照字段写回草稿列。
     * <p>仅覆盖快照中出现的字段，兼容旧版历史（缺字段时不把现有配置清空）。</p>
     */
    private void applySnapshotToDraft(FlowApiDO api, String snapshotJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode snap = SNAPSHOT_MAPPER.readTree(snapshotJson);
            applyText(snap, "name", api::setName);
            applyText(snap, "url", api::setUrl);
            applyText(snap, "method", api::setMethod);
            applyText(snap, "info", api::setInfo);
            applyText(snap, "tags", api::setTags);
            applyText(snap, "version", api::setVersion);
            applyText(snap, "serviceType", api::setServiceType);
            applyText(snap, "interceptMode", api::setInterceptMode);
            applyText(snap, "hostBinding", api::setHostBinding);
            applyText(snap, "dslContent", api::setDslContent);
            applyText(snap, "sqlContent", api::setSqlContent);
            applyText(snap, "jsonContent", api::setJsonContent);
            applyText(snap, "textContent", api::setTextContent);
            applyText(snap, "datasource", api::setDatasource);
            applyText(snap, "responseType", api::setResponseType);
            applyText(snap, "contract", api::setContract);
            applyText(snap, "cacheConfig", api::setCacheConfig);
            applyText(snap, "securityConfig", api::setSecurityConfig);
            applyText(snap, "privacyConfig", api::setPrivacyConfig);
            applyText(snap, "viewExportConfig", api::setViewExportConfig);
            applyText(snap, "templateId", api::setTemplateId);
            applyText(snap, "customSuccessWrapper", api::setCustomSuccessWrapper);
            applyText(snap, "customPageWrapper", api::setCustomPageWrapper);
            applyText(snap, "customFailWrapper", api::setCustomFailWrapper);
            if (snap.has("logEnabled") && !snap.get("logEnabled").isNull()) {
                api.setLogEnabled(snap.get("logEnabled").asBoolean());
            }
            if (snap.has("level") && !snap.get("level").isNull()) {
                api.setLevel(snap.get("level").asInt());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析历史版本快照失败", e);
        }
    }

    /**
     * 构建完整发布快照（实现内容 + 契约 + 基础/缓存/响应包装等配置）
     */
    private String buildSnapshot(FlowApiDO api) {
        try {
            ObjectNode snap = SNAPSHOT_MAPPER.createObjectNode();
            snap.put("name", api.getName());
            snap.put("url", api.getUrl());
            snap.put("method", api.getMethod());
            snap.put("info", api.getInfo());
            snap.put("tags", api.getTags());
            snap.put("version", api.getVersion());
            snap.put("serviceType", api.getServiceType());
            snap.put("interceptMode", ApiInterceptMode.normalize(api.getInterceptMode()));
            snap.put("hostBinding", api.getHostBinding());
            snap.put("dslContent", api.getDslContent());
            snap.put("sqlContent", api.getSqlContent());
            snap.put("jsonContent", api.getJsonContent());
            snap.put("textContent", api.getTextContent());
            snap.put("datasource", api.getDatasource());
            snap.put("responseType", api.getResponseType());
            snap.put("contract", api.getContract());
            snap.put("cacheConfig", api.getCacheConfig());
            snap.put("securityConfig", api.getSecurityConfig());
            snap.put("privacyConfig", api.getPrivacyConfig());
            snap.put("directoryId", api.getDirectoryId());
            snap.put("viewExportConfig", api.getViewExportConfig());
            snap.put("templateId", api.getTemplateId());
            snap.put("customSuccessWrapper", api.getCustomSuccessWrapper());
            snap.put("customPageWrapper", api.getCustomPageWrapper());
            snap.put("customFailWrapper", api.getCustomFailWrapper());
            if (api.getLogEnabled() != null) {
                snap.put("logEnabled", api.getLogEnabled());
            }
            if (api.getLevel() != null) {
                snap.put("level", api.getLevel());
            }
            return SNAPSHOT_MAPPER.writeValueAsString(snap);
        } catch (Exception e) {
            throw new RuntimeException("生成发布快照失败", e);
        }
    }

    private void applyText(com.fasterxml.jackson.databind.JsonNode snap, String field,
                           java.util.function.Consumer<String> setter) {
        if (!snap.has(field)) {
            return;
        }
        com.fasterxml.jackson.databind.JsonNode node = snap.get(field);
        setter.accept(node == null || node.isNull() ? null : node.asText());
    }

    private String getSnapText(com.fasterxml.jackson.databind.JsonNode snap, String field) {
        com.fasterxml.jackson.databind.JsonNode node = snap.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    // ============================= 私有方法 =============================

    /**
     * 批量填充 DTO 列表中的 directoryName（内存拼装，解决 N+1 问题）
     */
    private void enrichDirectoryNames(List<FlowApiDTO> dtoList) {
        Set<String> directoryIds = dtoList.stream()
                .map(FlowApiDTO::getDirectoryId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());

        if (!directoryIds.isEmpty()) {
            Map<String, String> dirMap = flowDirectoryRepository.findAllById(directoryIds).stream()
                    .collect(Collectors.toMap(
                            FlowDirectoryDO::getId,
                            FlowDirectoryDO::getName));
            dtoList.forEach(dto -> dto.setDirectoryName(dirMap.get(dto.getDirectoryId())));
        }
    }
}
