package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.FlowApiCacheManager;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.service.FlowAssetVersionService;
import org.yu.flow.module.mqtask.consumer.MqConsumerManager;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.release.domain.FlowRegressionCaseDO;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;
import org.yu.flow.module.release.repository.FlowRegressionCaseRepository;
import org.yu.flow.module.release.repository.FlowRegressionSuiteRepository;
import org.yu.flow.module.release.support.ReleaseImportBackup.Entry;
import org.yu.flow.module.responsetemplate.cache.ResponseTemplateCacheManager;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.task.scheduler.FlowTaskScheduler;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.BundleExcelTemplate;
import org.yu.flow.module.transfer.dto.BundleRegressionSuite;
import org.yu.flow.module.transfer.support.BundleEntityCopier;
import org.yu.flow.util.AfterCommitExecutor;

import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.page.domain.PageInfoDO;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * 导入前备份与回滚恢复。
 *
 * <p>恢复直接写库并刷新运行时（路由缓存、调度器、MQ 订阅都是事务感知的，提交后才生效），
 * 不走各资产的 publish/unpublish，避免门禁与草稿校验把回滚挡住。</p>
 */
@Component
public class ReleaseImportBackupManager {

    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;
    @Resource
    private FlowTaskRepository flowTaskRepository;
    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;
    @Resource
    private ResponseTemplateRepository responseTemplateRepository;
    @Resource
    private FlowApiExcelTemplateRepository flowApiExcelTemplateRepository;
    @Resource
    private FlowRegressionSuiteRepository flowRegressionSuiteRepository;
    @Resource
    private FlowRegressionCaseRepository flowRegressionCaseRepository;
    @Resource
    private FlowApiCacheManager flowApiCacheManager;
    @Resource
    private ApiResponseCacheService apiResponseCacheService;
    @Resource
    private OpenPlatformCache openPlatformCache;
    @Resource
    private FlowTaskScheduler flowTaskScheduler;
    @Resource
    private MqConsumerManager mqConsumerManager;
    @Resource
    private FlowReferenceIndex flowReferenceIndex;
    @Resource
    private ResponseTemplateCacheManager responseTemplateCacheManager;
    @Resource
    private FlowAssetVersionService flowAssetVersionService;
    @Resource
    private PageInfoRepository pageInfoRepository;
    @Resource
    private FlowModelInfoRepository flowModelInfoRepository;
    @Resource
    private SysMacroRepository sysMacroRepository;
    @Resource
    private SysConfigRepository sysConfigRepository;
    @Resource
    private FlowOpenPlatformRepository flowOpenPlatformRepository;
    @Resource
    private FlowOpenApiGrantRepository flowOpenApiGrantRepository;
    @Resource
    private AlertRuleRepository alertRuleRepository;
    @Resource
    private SysMacroCacheManager sysMacroCacheManager;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    /**
     * @param offline 下线项：导入会撤销它们的发布 / 停用，同样要备份以便回滚
     */
    public ReleaseImportBackup snapshot(AssetBundle bundle, List<ReleasePackageFormat.ReleaseEntry> offline) {
        ReleaseImportBackup backup = new ReleaseImportBackup();
        for (String id : ids(bundle.getApis(), FlowApiDO::getId, offline, ReleaseAssetResolver.API)) {
            backup.getApis().add(capture(id, flowApiRepository.findById(id).orElse(null),
                    FlowApiDO.class, flowApiRepository::countAnyById));
        }
        for (String id : ids(bundle.getServices(), FlowServiceFlowDO::getId, offline, ReleaseAssetResolver.SERVICE)) {
            backup.getServices().add(capture(id, flowServiceFlowRepository.findById(id).orElse(null),
                    FlowServiceFlowDO.class, flowServiceFlowRepository::countAnyById));
        }
        for (String id : ids(bundle.getTasks(), FlowTaskDO::getId, offline, ReleaseAssetResolver.TASK)) {
            backup.getTasks().add(capture(id, flowTaskRepository.findById(id).orElse(null),
                    FlowTaskDO.class, flowTaskRepository::countAnyById));
        }
        for (String id : ids(bundle.getMqTasks(), FlowMqTaskDO::getId, offline, ReleaseAssetResolver.MQ_TASK)) {
            backup.getMqTasks().add(capture(id, flowMqTaskRepository.findById(id).orElse(null),
                    FlowMqTaskDO.class, flowMqTaskRepository::countAnyById));
        }
        for (String id : ids(bundle.getPages(), PageInfoDO::getId, offline, ReleaseAssetResolver.PAGE)) {
            backup.getPages().add(capture(id, pageInfoRepository.findById(id).orElse(null), PageInfoDO.class, x -> 0));
        }
        for (FlowModelInfoDO m : nullSafe(bundle.getModels())) {
            backup.getModels().add(capture(m.getId(), flowModelInfoRepository.findById(m.getId()).orElse(null),
                    FlowModelInfoDO.class, x -> 0));
        }
        for (SysMacroDO m : nullSafe(bundle.getSysMacros())) {
            backup.getSysMacros().add(capture(m.getMacroCode(),
                    sysMacroRepository.findByMacroCode(m.getMacroCode()).orElse(null), SysMacroDO.class, x -> 0));
        }
        for (SysConfigDO c : nullSafe(bundle.getSysConfigs())) {
            backup.getSysConfigs().add(capture(c.getConfigKey(),
                    sysConfigRepository.findByConfigKey(c.getConfigKey()).orElse(null), SysConfigDO.class, x -> 0));
        }
        List<String> platformCodes = new ArrayList<>();
        nullSafe(bundle.getOpenPlatforms()).forEach(p -> platformCodes.add(p.getPlatform().getCode()));
        offlineOf(offline, ReleaseAssetResolver.OPEN_PLATFORM).forEach(e -> platformCodes.add(e.getAssetKey()));
        for (String code : new LinkedHashSet<>(platformCodes)) {
            FlowOpenPlatformDO platform = flowOpenPlatformRepository.findByCode(code).orElse(null);
            if (platform == null) {
                backup.getOpenPlatforms().add(new Entry<>(code, ReleaseImportBackup.ABSENT, null));
            } else {
                List<FlowOpenApiGrantDO> grants = flowOpenApiGrantRepository.findByPlatformId(platform.getId()).stream()
                        .map(g -> BundleEntityCopier.detachedCopy(g, FlowOpenApiGrantDO.class)).toList();
                backup.getOpenPlatforms().add(new Entry<>(code, ReleaseImportBackup.ACTIVE, new ReleaseImportBackup.PlatformState(
                        BundleEntityCopier.detachedCopy(platform, FlowOpenPlatformDO.class), grants)));
            }
        }
        List<String> ruleIds = new ArrayList<>();
        nullSafe(bundle.getAlertRules()).forEach(r -> ruleIds.add(r.getRule().getId()));
        offlineOf(offline, ReleaseAssetResolver.ALERT_RULE).forEach(e -> ruleIds.add(e.getAssetId()));
        for (String id : new LinkedHashSet<>(ruleIds)) {
            backup.getAlertRules().add(capture(id, alertRuleRepository.findById(id).orElse(null), AlertRuleDO.class, x -> 0));
        }
        for (ResponseTemplateDO t : nullSafe(bundle.getResponseTemplates())) {
            backup.getResponseTemplates().add(capture(t.getId(), responseTemplateRepository.findById(t.getId()).orElse(null),
                    ResponseTemplateDO.class, id -> 0));
        }
        for (BundleExcelTemplate e : nullSafe(bundle.getExcelTemplates())) {
            backup.getExcelTemplates().add(capture(e.getApiId(),
                    flowApiExcelTemplateRepository.findByApiId(e.getApiId()).orElse(null),
                    FlowApiExcelTemplateDO.class, id -> 0));
        }
        for (BundleRegressionSuite item : nullSafe(bundle.getRegressionSuites())) {
            if (item == null || item.getSuite() == null || StrUtil.isBlank(item.getSuite().getId())) {
                continue;
            }
            String suiteId = item.getSuite().getId();
            FlowRegressionSuiteDO suite = flowRegressionSuiteRepository.findById(suiteId).orElse(null);
            if (suite == null) {
                backup.getRegressionSuites().add(new Entry<>(suiteId, ReleaseImportBackup.ABSENT, null));
            } else {
                List<FlowRegressionCaseDO> cases = flowRegressionCaseRepository.findBySuiteIdOrderBySortOrderAsc(suiteId)
                        .stream().map(c -> BundleEntityCopier.detachedCopy(c, FlowRegressionCaseDO.class)).toList();
                backup.getRegressionSuites().add(new Entry<>(suiteId, ReleaseImportBackup.ACTIVE,
                        new ReleaseImportBackup.SuiteState(
                                BundleEntityCopier.detachedCopy(suite, FlowRegressionSuiteDO.class), cases)));
            }
        }
        return backup;
    }

    /** 须在事务内调用 */
    public void restore(ReleaseImportBackup backup, String releaseCode) {
        String remark = "回滚导入 " + StrUtil.nullToEmpty(releaseCode);
        String user = JwtTokenUtil.currentUsername();

        for (Entry<FlowApiDO> e : backup.getApis()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                FlowApiDO row = flowApiRepository.save(e.getRow());
                appendVersion(AssetBizType.API, row.getId(), row.getPublishStatus(), row.getPublishedSnapshot(), remark, user);
            } else {
                flowApiRepository.deleteById(e.getId());
            }
            String apiId = e.getId();
            AfterCommitExecutor.run("evict api response cache " + apiId, () -> apiResponseCacheService.evictAll(apiId));
        }
        for (Entry<FlowServiceFlowDO> e : backup.getServices()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                FlowServiceFlowDO row = flowServiceFlowRepository.save(e.getRow());
                appendVersion(AssetBizType.SERVICE, row.getId(), row.getPublishStatus(), row.getPublishedSnapshot(), remark, user);
            } else {
                flowServiceFlowRepository.deleteById(e.getId());
            }
        }
        for (Entry<FlowTaskDO> e : backup.getTasks()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                FlowTaskDO row = flowTaskRepository.save(e.getRow());
                appendVersion(AssetBizType.TASK, row.getId(), row.getPublishStatus(), row.getPublishedSnapshot(), remark, user);
                flowTaskScheduler.reschedule(row);
            } else {
                flowTaskRepository.deleteById(e.getId());
                flowTaskScheduler.cancel(e.getId());
            }
        }
        for (Entry<FlowMqTaskDO> e : backup.getMqTasks()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                mqConsumerManager.resubscribe(flowMqTaskRepository.save(e.getRow()));
            } else {
                flowMqTaskRepository.deleteById(e.getId());
                mqConsumerManager.cancel(e.getId());
            }
        }
        for (Entry<ResponseTemplateDO> e : backup.getResponseTemplates()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                responseTemplateRepository.save(e.getRow());
            } else {
                responseTemplateRepository.deleteById(e.getId());
            }
        }
        for (Entry<FlowApiExcelTemplateDO> e : backup.getExcelTemplates()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                flowApiExcelTemplateRepository.save(e.getRow());
            } else {
                flowApiExcelTemplateRepository.deleteByApiId(e.getId());
            }
        }
        for (Entry<ReleaseImportBackup.SuiteState> e : backup.getRegressionSuites()) {
            List<FlowRegressionCaseDO> current = flowRegressionCaseRepository.findBySuiteIdOrderBySortOrderAsc(e.getId());
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                // 同一持久化上下文里先删后按原 ID 保存会被 Hibernate 拒绝，只删导入新增的用例，其余原地覆盖
                Set<String> keep = new HashSet<>();
                e.getRow().getCases().forEach(c -> keep.add(c.getId()));
                flowRegressionCaseRepository.deleteAll(current.stream().filter(c -> !keep.contains(c.getId())).toList());
                flowRegressionSuiteRepository.save(e.getRow().getSuite());
                flowRegressionCaseRepository.saveAll(e.getRow().getCases());
            } else {
                flowRegressionCaseRepository.deleteAll(current);
                flowRegressionSuiteRepository.deleteById(e.getId());
            }
        }

        for (Entry<PageInfoDO> e : backup.getPages()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                pageInfoRepository.save(e.getRow());
            } else {
                pageInfoRepository.deleteById(e.getId());
            }
        }
        for (Entry<FlowModelInfoDO> e : backup.getModels()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                flowModelInfoRepository.save(e.getRow());
            } else {
                flowModelInfoRepository.deleteById(e.getId());
            }
        }
        for (Entry<SysMacroDO> e : backup.getSysMacros()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                sysMacroRepository.save(e.getRow());
            } else {
                sysMacroRepository.findByMacroCode(e.getId()).ifPresent(sysMacroRepository::delete);
            }
        }
        for (Entry<SysConfigDO> e : backup.getSysConfigs()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                sysConfigRepository.save(e.getRow());
            } else {
                sysConfigRepository.findByConfigKey(e.getId()).ifPresent(sysConfigRepository::delete);
            }
        }
        for (Entry<ReleaseImportBackup.PlatformState> e : backup.getOpenPlatforms()) {
            FlowOpenPlatformDO current = flowOpenPlatformRepository.findByCode(e.getId()).orElse(null);
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                FlowOpenPlatformDO restored = flowOpenPlatformRepository.save(e.getRow().getPlatform());
                flowOpenApiGrantRepository.deleteByPlatformId(restored.getId());
                for (FlowOpenApiGrantDO g : e.getRow().getGrants()) {
                    g.setId(null);
                    g.setPlatformId(restored.getId());
                    flowOpenApiGrantRepository.save(g);
                }
            } else if (current != null) {
                // 导入新建的平台之后可能已签发了 AppKey，不删除，只停用并撤销授权
                current.setStatus(0);
                flowOpenPlatformRepository.save(current);
                flowOpenApiGrantRepository.deleteByPlatformId(current.getId());
            }
        }
        for (Entry<AlertRuleDO> e : backup.getAlertRules()) {
            if (ReleaseImportBackup.ACTIVE.equals(e.getState())) {
                alertRuleRepository.save(e.getRow());
            } else {
                alertRuleRepository.deleteById(e.getId());
            }
        }

        flowApiCacheManager.publishRefreshEvent();
        openPlatformCache.publishRefresh();
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        if (!backup.getResponseTemplates().isEmpty()) {
            AfterCommitExecutor.run("reload response templates", responseTemplateCacheManager::reloadAll);
        }
        if (!backup.getSysMacros().isEmpty()) {
            sysMacroCacheManager.publishRefreshEvent();
        }
        if (!backup.getSysConfigs().isEmpty()) {
            sysConfigCacheManager.publishRefreshEvent();
        }
    }

    private static <T> List<String> ids(List<T> bundled, Function<T, String> idOf,
                                        List<ReleasePackageFormat.ReleaseEntry> offline, String type) {
        Set<String> ids = new LinkedHashSet<>();
        nullSafe(bundled).forEach(t -> {
            String id = idOf.apply(t);
            if (StrUtil.isNotBlank(id)) {
                ids.add(id);
            }
        });
        offlineOf(offline, type).forEach(e -> ids.add(e.getAssetId()));
        return new ArrayList<>(ids);
    }

    private static List<ReleasePackageFormat.ReleaseEntry> offlineOf(List<ReleasePackageFormat.ReleaseEntry> offline,
                                                                    String type) {
        return nullSafe(offline).stream().filter(e -> type.equals(e.getAssetType())).toList();
    }

    private <T> Entry<T> capture(String id, T current, Class<T> type, ToLongFunction<String> countAny) {
        if (current != null) {
            return new Entry<>(id, ReleaseImportBackup.ACTIVE, BundleEntityCopier.detachedCopy(current, type));
        }
        return new Entry<>(id, countAny.applyAsLong(id) > 0 ? ReleaseImportBackup.DELETED : ReleaseImportBackup.ABSENT, null);
    }

    /** 恢复后线上快照回到导入前，记一条回滚版本，版本历史里能看出这次变化 */
    private void appendVersion(String bizType, String id, Integer publishStatus, String snapshot, String remark, String user) {
        if (publishStatus != null && publishStatus == 1 && StrUtil.isNotBlank(snapshot)) {
            flowAssetVersionService.append(bizType, id, snapshot, AssetBizType.SOURCE_ROLLBACK, remark, user);
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
