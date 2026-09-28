package org.yu.flow.module.transfer.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.responsetemplate.cache.ResponseTemplateCacheManager;
import org.yu.flow.util.AfterCommitExecutor;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.transfer.dto.BundleExcelTemplate;
import org.yu.flow.module.assetref.FlowDslReferenceScanner;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager;
import org.yu.flow.module.envvar.repository.SysEnvVariableRepository;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.mq.repository.MqConnectionRepository;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.release.domain.FlowRegressionCaseDO;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;
import org.yu.flow.module.release.repository.FlowRegressionCaseRepository;
import org.yu.flow.module.release.repository.FlowRegressionSuiteRepository;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.AssetExportRequestDTO;
import org.yu.flow.module.transfer.dto.BundleRegressionSuite;
import org.yu.flow.module.transfer.dto.TransferItemDTO;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;
import org.yu.flow.module.transfer.service.AssetTransferService;
import org.yu.flow.module.transfer.support.BundleEntityCopier;
import org.yu.flow.module.transfer.support.ConfigAssetTransfer;
import org.yu.flow.module.transfer.support.RequirementAttributes;
import org.yu.flow.module.transfer.support.BundleRequirementScanner;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资产迁移实现。
 *
 * <p>导出保留源环境 ID，导入按 ID upsert：DSL 里的 {@code serviceId} 与 {@code directoryId}
 * 都是 ID 引用，只有保留 ID 才能让引用在目标环境自动对上，同一个包也才能反复导入做版本更新。</p>
 *
 * <p>导入只写草稿列，不动目标环境的 {@code publishedSnapshot} 与发布状态——线上流量始终由快照驱动，
 * 导入完成后必须再走一次发布（含发布门禁）才会生效。</p>
 */
@Slf4j
@Service
public class AssetTransferServiceImpl implements AssetTransferService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 依赖闭包展开上限，防止环状引用或超大编排拖垮导出 */
    private static final int MAX_CLOSURE_SIZE = 500;
    private static final int MAX_DIR_DEPTH = 64;

    private static final String TYPE_API = "API";
    private static final String TYPE_SERVICE = "SERVICE";
    private static final String TYPE_TASK = "TASK";
    private static final String TYPE_MQ_TASK = "MQ_TASK";
    private static final String TYPE_TEMPLATE = "RESPONSE_TEMPLATE";
    private static final String TYPE_EXCEL = "EXCEL_TEMPLATE";
    private static final String TYPE_DIRECTORY = "DIRECTORY";
    private static final String TYPE_REGRESSION = "REGRESSION_SUITE";
    /** 与列宽 MEDIUMBLOB（16MB）留足余量 */
    private static final int MAX_EXCEL_BYTES = 10 * 1024 * 1024;

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowDirectoryRepository flowDirectoryRepository;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    @Resource
    private FlowRegressionSuiteRepository flowRegressionSuiteRepository;

    @Resource
    private FlowRegressionCaseRepository flowRegressionCaseRepository;

    @Resource
    private DynamicDataSourceService dynamicDataSourceService;

    @Resource
    private MqConnectionRepository mqConnectionRepository;

    @Resource
    private OssConnectionRepository ossConnectionRepository;

    @Resource
    private ResponseTemplateRepository responseTemplateRepository;

    @Resource
    private SysEnvVariableRepository sysEnvVariableRepository;

    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;

    @Resource
    private FlowApiExcelTemplateRepository flowApiExcelTemplateRepository;

    @Resource
    private ResponseTemplateCacheManager responseTemplateCacheManager;

    @Resource
    private ConfigAssetTransfer configAssetTransfer;

    @Resource
    private RequirementAttributes requirementAttributes;

    @Resource
    private EnvVariableCacheManager envVariableCacheManager;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private RbacService rbacService;

    @Resource
    private FlowReferenceIndex flowReferenceIndex;

    @Resource
    private AuditLogService auditLogService;

    // ─────────────────────────────────────────────────────────────────────────
    // 导出
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AssetBundle export(AssetExportRequestDTO request) {
        if (request == null) {
            throw new FlowException("TRANSFER_BAD_REQUEST", "导出请求不能为空");
        }
        boolean publishedFirst = !AssetExportRequestDTO.SOURCE_DRAFT.equalsIgnoreCase(request.getContentSource());
        boolean withDependencies = !Boolean.FALSE.equals(request.getIncludeDependencies());
        boolean withRegression = !Boolean.FALSE.equals(request.getIncludeRegression());

        Map<String, FlowApiDO> apis = new LinkedHashMap<>();
        Map<String, FlowServiceFlowDO> services = new LinkedHashMap<>();
        Map<String, FlowTaskDO> tasks = new LinkedHashMap<>();
        Map<String, FlowMqTaskDO> mqTasks = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> autoAdded = new ArrayList<>();

        Deque<String[]> pending = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        seed(pending, visited, TYPE_API, request.getApiIds());
        seed(pending, visited, TYPE_SERVICE, request.getServiceIds());
        seed(pending, visited, TYPE_TASK, request.getTaskIds());
        seed(pending, visited, TYPE_MQ_TASK, request.getMqTaskIds());
        Set<String> explicit = new LinkedHashSet<>(visited);
        List<ResponseTemplateDO> templates = collectTemplates(request.getResponseTemplateIds(), warnings);
        if (pending.isEmpty() && templates.isEmpty() && !configAssetTransfer.hasSelection(request)) {
            throw new FlowException("TRANSFER_BAD_REQUEST", "请至少选择一个要导出的资产");
        }

        while (!pending.isEmpty()) {
            String[] cur = pending.poll();
            String type = cur[0];
            String id = cur[1];
            String dsl = null;
            switch (type) {
                case TYPE_API -> {
                    FlowApiDO row = flowApiRepository.findById(id).orElse(null);
                    if (row == null) {
                        warnings.add("接口不存在或已删除，已跳过: " + id);
                        continue;
                    }
                    if (HostCatalogReserved.isReservedId(id)) {
                        warnings.add("系统保留接口不参与迁移，已跳过: " + StrUtil.nullToEmpty(row.getName()));
                        continue;
                    }
                    FlowApiDO copy = materializeApi(row, publishedFirst);
                    apis.put(id, copy);
                    dsl = copy.getDslContent();
                }
                case TYPE_SERVICE -> {
                    FlowServiceFlowDO row = flowServiceFlowRepository.findById(id).orElse(null);
                    if (row == null) {
                        warnings.add("内部服务不存在或已删除，已跳过: " + id);
                        continue;
                    }
                    FlowServiceFlowDO copy = materializeService(row, publishedFirst);
                    services.put(id, copy);
                    dsl = copy.getDslContent();
                }
                case TYPE_TASK -> {
                    FlowTaskDO row = flowTaskRepository.findById(id).orElse(null);
                    if (row == null) {
                        warnings.add("定时任务不存在或已删除，已跳过: " + id);
                        continue;
                    }
                    FlowTaskDO copy = materializeTask(row, publishedFirst);
                    tasks.put(id, copy);
                    dsl = copy.getDslContent();
                }
                case TYPE_MQ_TASK -> {
                    FlowMqTaskDO row = flowMqTaskRepository.findById(id).orElse(null);
                    if (row == null) {
                        warnings.add("MQ 任务不存在或已删除，已跳过: " + id);
                        continue;
                    }
                    FlowMqTaskDO copy = materializeMqTask(row, publishedFirst);
                    mqTasks.put(id, copy);
                    dsl = copy.getDslContent();
                }
                default -> {
                }
            }
            if (!withDependencies || StrUtil.isBlank(dsl)) {
                continue;
            }
            for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(dsl)) {
                String refType = "service".equals(ref.targetType()) ? TYPE_SERVICE : TYPE_API;
                String key = refType + ":" + ref.targetId();
                if (visited.contains(key)) {
                    continue;
                }
                if (visited.size() >= MAX_CLOSURE_SIZE) {
                    warnings.add("依赖数量超过 " + MAX_CLOSURE_SIZE + " 个，剩余引用未展开，请拆分导出");
                    break;
                }
                visited.add(key);
                autoAdded.add(key);
                pending.add(new String[]{refType, ref.targetId()});
            }
        }

        AssetBundle bundle = new AssetBundle();
        bundle.setSchemaVersion(AssetBundle.SCHEMA_VERSION);
        bundle.setKind(AssetBundle.KIND);
        bundle.setExportedAt(LocalDateTime.now().format(TS));
        bundle.setExportedBy(StrUtil.blankToDefault(JwtTokenUtil.currentUsername(), "system"));
        bundle.setSourceEnv(request.getSourceEnv());
        bundle.setContentSource(publishedFirst
                ? AssetExportRequestDTO.SOURCE_PUBLISHED_FIRST : AssetExportRequestDTO.SOURCE_DRAFT);
        bundle.setApis(new ArrayList<>(apis.values()));
        bundle.setServices(new ArrayList<>(services.values()));
        bundle.setTasks(new ArrayList<>(tasks.values()));
        bundle.setMqTasks(new ArrayList<>(mqTasks.values()));
        bundle.setResponseTemplates(templates);
        bundle.setExcelTemplates(collectExcelTemplates(apis.keySet()));
        configAssetTransfer.export(request, bundle, warnings);
        bundle.setDirectories(collectDirectories(apis.values(), services.values(), tasks.values(), mqTasks.values(),
                configAssetTransfer.directoryIds(bundle)));
        if (withRegression) {
            bundle.setRegressionSuites(collectRegression(apis.keySet(), services.keySet(), tasks.keySet()));
        }
        bundle.setRequirements(collectRequirements(bundle, apis.values(), services.values(), tasks.values(), mqTasks.values()));
        if (!autoAdded.isEmpty()) {
            warnings.add("已按引用关系自动补齐 " + autoAdded.size() + " 个资产："
                    + String.join("、", autoAdded));
        }
        bundle.setWarnings(warnings);

        auditLogService.record("ASSET_EXPORT", "TRANSFER", null,
                "{\"apis\":" + apis.size() + ",\"services\":" + services.size()
                        + ",\"tasks\":" + tasks.size() + ",\"mqTasks\":" + mqTasks.size()
                        + ",\"templates\":" + templates.size() + ",\"pages\":" + bundle.getPages().size()
                        + ",\"models\":" + bundle.getModels().size() + ",\"macros\":" + bundle.getSysMacros().size()
                        + ",\"configs\":" + bundle.getSysConfigs().size() + ",\"openPlatforms\":" + bundle.getOpenPlatforms().size()
                        + ",\"alertRules\":" + bundle.getAlertRules().size() + ",\"explicit\":" + explicit.size() + "}");
        return bundle;
    }

    private void seed(Deque<String[]> pending, Set<String> visited, String type, List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (StrUtil.isBlank(id) || !visited.add(type + ":" + id)) {
                continue;
            }
            pending.add(new String[]{type, id});
        }
    }

    /** 已发布优先：把线上快照回填成草稿内容，导出的就是测试环境验证过的那一版 */
    private FlowApiDO materializeApi(FlowApiDO row, boolean publishedFirst) {
        FlowApiDO copy = BundleEntityCopier.detachedCopy(row, FlowApiDO.class);
        if (publishedFirst && isPublished(row.getPublishStatus(), row.getPublishedSnapshot())) {
            BundleEntityCopier.applySnapshot(copy, row.getPublishedSnapshot());
        }
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
        return copy;
    }

    private FlowServiceFlowDO materializeService(FlowServiceFlowDO row, boolean publishedFirst) {
        FlowServiceFlowDO copy = BundleEntityCopier.detachedCopy(row, FlowServiceFlowDO.class);
        if (publishedFirst && isPublished(row.getPublishStatus(), row.getPublishedSnapshot())) {
            BundleEntityCopier.applySnapshot(copy, row.getPublishedSnapshot());
        }
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
        return copy;
    }

    private FlowTaskDO materializeTask(FlowTaskDO row, boolean publishedFirst) {
        FlowTaskDO copy = BundleEntityCopier.detachedCopy(row, FlowTaskDO.class);
        if (publishedFirst && isPublished(row.getPublishStatus(), row.getPublishedSnapshot())) {
            BundleEntityCopier.applySnapshot(copy, row.getPublishedSnapshot());
        }
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
        return copy;
    }

    private FlowMqTaskDO materializeMqTask(FlowMqTaskDO row, boolean publishedFirst) {
        FlowMqTaskDO copy = BundleEntityCopier.detachedCopy(row, FlowMqTaskDO.class);
        if (publishedFirst && isPublished(row.getPublishStatus(), row.getPublishedSnapshot())) {
            BundleEntityCopier.applySnapshot(copy, row.getPublishedSnapshot());
        }
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
        return copy;
    }

    private List<ResponseTemplateDO> collectTemplates(List<String> ids, List<String> warnings) {
        List<ResponseTemplateDO> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (String id : new LinkedHashSet<>(ids)) {
            if (StrUtil.isBlank(id)) {
                continue;
            }
            ResponseTemplateDO row = responseTemplateRepository.findById(id).orElse(null);
            if (row == null) {
                warnings.add("响应模板不存在或已删除，已跳过: " + id);
                continue;
            }
            ResponseTemplateDO copy = BundleEntityCopier.detachedCopy(row, ResponseTemplateDO.class);
            copy.setCreateTime(null);
            copy.setUpdateTime(null);
            result.add(copy);
        }
        return result;
    }

    private List<BundleExcelTemplate> collectExcelTemplates(Set<String> apiIds) {
        List<BundleExcelTemplate> result = new ArrayList<>();
        for (String apiId : apiIds) {
            flowApiExcelTemplateRepository.findByApiId(apiId).ifPresent(tpl -> {
                BundleExcelTemplate item = new BundleExcelTemplate();
                item.setApiId(apiId);
                item.setFileName(tpl.getFileName());
                item.setContentType(tpl.getContentType());
                item.setFileSize(tpl.getFileSize());
                item.setContentBase64(Base64.getEncoder().encodeToString(tpl.getContent()));
                result.add(item);
            });
        }
        return result;
    }

    private static boolean isPublished(Integer publishStatus, String snapshot) {
        return publishStatus != null && publishStatus == 1 && StrUtil.isNotBlank(snapshot);
    }

    /** 目录连同各级父目录一起导出，否则导入后资产会掉到根目录、丢掉目录级防护配置 */
    private List<FlowDirectoryDO> collectDirectories(java.util.Collection<FlowApiDO> apis,
                                                     java.util.Collection<FlowServiceFlowDO> services,
                                                     java.util.Collection<FlowTaskDO> tasks,
                                                     java.util.Collection<FlowMqTaskDO> mqTasks,
                                                     Set<String> extraDirectoryIds) {
        Set<String> seeds = new LinkedHashSet<>(extraDirectoryIds);
        apis.forEach(a -> addIfNotBlank(seeds, a.getDirectoryId()));
        services.forEach(s -> addIfNotBlank(seeds, s.getDirectoryId()));
        tasks.forEach(t -> addIfNotBlank(seeds, t.getDirectoryId()));
        mqTasks.forEach(t -> addIfNotBlank(seeds, t.getDirectoryId()));

        Map<String, FlowDirectoryDO> result = new LinkedHashMap<>();
        for (String seed : seeds) {
            String cursor = seed;
            int depth = 0;
            while (StrUtil.isNotBlank(cursor) && depth++ < MAX_DIR_DEPTH && !result.containsKey(cursor)) {
                FlowDirectoryDO dir = flowDirectoryRepository.findById(cursor).orElse(null);
                if (dir == null) {
                    break;
                }
                FlowDirectoryDO copy = BundleEntityCopier.detachedCopy(dir, FlowDirectoryDO.class);
                copy.setCreateTime(null);
                copy.setUpdateTime(null);
                result.put(cursor, copy);
                cursor = dir.getParentId();
            }
        }
        return new ArrayList<>(result.values());
    }

    private List<BundleRegressionSuite> collectRegression(Set<String> apiIds, Set<String> serviceIds,
                                                          Set<String> taskIds) {
        List<BundleRegressionSuite> result = new ArrayList<>();
        collectRegressionFor(TYPE_API, apiIds, result);
        collectRegressionFor(TYPE_SERVICE, serviceIds, result);
        collectRegressionFor(TYPE_TASK, taskIds, result);
        return result;
    }

    private void collectRegressionFor(String assetType, Set<String> ids, List<BundleRegressionSuite> result) {
        for (String id : ids) {
            for (FlowRegressionSuiteDO suite : flowRegressionSuiteRepository.findByAssetTypeAndAssetId(assetType, id)) {
                BundleRegressionSuite item = new BundleRegressionSuite();
                FlowRegressionSuiteDO suiteCopy = BundleEntityCopier.detachedCopy(suite, FlowRegressionSuiteDO.class);
                suiteCopy.setCreateTime(null);
                suiteCopy.setUpdateTime(null);
                item.setSuite(suiteCopy);
                for (FlowRegressionCaseDO c : flowRegressionCaseRepository
                        .findBySuiteIdOrderBySortOrderAsc(suite.getId())) {
                    FlowRegressionCaseDO caseCopy = BundleEntityCopier.detachedCopy(c, FlowRegressionCaseDO.class);
                    caseCopy.setCreateTime(null);
                    caseCopy.setUpdateTime(null);
                    item.getCases().add(caseCopy);
                }
                result.add(item);
            }
        }
    }

    private List<TransferRequirementDTO> collectRequirements(AssetBundle bundle,
                                                             java.util.Collection<FlowApiDO> apis,
                                                             java.util.Collection<FlowServiceFlowDO> services,
                                                             java.util.Collection<FlowTaskDO> tasks,
                                                             java.util.Collection<FlowMqTaskDO> mqTasks) {
        Map<String, TransferRequirementDTO> acc = new LinkedHashMap<>();
        for (FlowApiDO api : apis) {
            String name = StrUtil.nullToEmpty(api.getName());
            BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_DATASOURCE, api.getDatasource(), name);
            BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_TEMPLATE, api.getTemplateId(), name);
            BundleRequirementScanner.scanDsl(api.getDslContent(), name, acc);
        }
        for (FlowServiceFlowDO service : services) {
            BundleRequirementScanner.scanDsl(service.getDslContent(), StrUtil.nullToEmpty(service.getName()), acc);
        }
        for (FlowTaskDO task : tasks) {
            BundleRequirementScanner.scanDsl(task.getDslContent(), StrUtil.nullToEmpty(task.getName()), acc);
        }
        for (FlowMqTaskDO task : mqTasks) {
            String name = StrUtil.nullToEmpty(task.getName());
            BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_MQ, task.getConnectionCode(), name);
            BundleRequirementScanner.scanDsl(task.getDslContent(), name, acc);
        }
        configAssetTransfer.collectRequirements(bundle, acc);
        requirementAttributes.fill(acc.values());
        for (TransferRequirementDTO req : acc.values()) {
            if (TransferRequirementDTO.KIND_ENV_VAR.equals(req.getKind())) {
                sysEnvVariableRepository.findByCode(req.getKey()).ifPresent(v -> req.setRemark(v.getRemark()));
            }
        }
        return new ArrayList<>(acc.values());
    }

    private static void addIfNotBlank(Set<String> target, String value) {
        if (StrUtil.isNotBlank(value)) {
            target.add(value);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 预检 / 导入
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public TransferReportDTO preflight(AssetBundle bundle, boolean overwriteExisting) {
        return analyze(bundle, overwriteExisting, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferReportDTO importBundle(AssetBundle bundle, boolean overwriteExisting) {
        assertImportPermissions(bundle);
        return doImport(bundle, overwriteExisting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferReportDTO importBundleAuthorized(AssetBundle bundle, boolean overwriteExisting) {
        return doImport(bundle, overwriteExisting);
    }

    private TransferReportDTO doImport(AssetBundle bundle, boolean overwriteExisting) {
        if (demoModeGuard.isDemoMode()) {
            throw new FlowException("DEMO_RESTRICTED", "演示模式下不允许导入资产包");
        }
        // 先整包预检，有冲突就一条都不写
        TransferReportDTO dryRun = analyze(bundle, overwriteExisting, true);
        if (dryRun.isBlocked()) {
            throw new FlowException("TRANSFER_CONFLICT", "存在冲突项，请先在预检结果中处理后再导入");
        }
        TransferReportDTO report = analyze(bundle, overwriteExisting, false);
        flowReferenceIndex.scheduleRebuildBroadcastAfterCommit();
        if (!nullSafe(bundle.getResponseTemplates()).isEmpty()) {
            AfterCommitExecutor.run("reload response templates", responseTemplateCacheManager::reloadAll);
        }
        configAssetTransfer.afterImport(bundle);
        auditLogService.record("ASSET_IMPORT", "TRANSFER", null,
                "{\"created\":" + report.getCreateCount() + ",\"updated\":" + report.getUpdateCount()
                        + ",\"skipped\":" + report.getSkipCount()
                        + ",\"from\":\"" + StrUtil.nullToEmpty(bundle.getSourceEnv()) + "\"}");
        return report;
    }

    /**
     * 预检与导入走同一段判定逻辑，保证「预检说会怎样」与「导入实际怎样」不会漂移。
     */
    private TransferReportDTO analyze(AssetBundle bundle, boolean overwriteExisting, boolean dryRun) {
        validateBundle(bundle);
        TransferReportDTO report = new TransferReportDTO();
        report.setDryRun(dryRun);
        report.setExportedAt(bundle.getExportedAt());
        report.setExportedBy(bundle.getExportedBy());
        report.setSourceEnv(bundle.getSourceEnv());
        report.setContentSource(bundle.getContentSource());
        if (bundle.getWarnings() != null) {
            report.getWarnings().addAll(bundle.getWarnings());
        }
        LocalDateTime now = LocalDateTime.now();

        for (FlowDirectoryDO dir : sortDirectoriesByDepth(bundle.getDirectories())) {
            if (StrUtil.isBlank(dir.getId())) {
                continue;
            }
            if (flowDirectoryRepository.existsById(dir.getId())) {
                report.getItems().add(TransferItemDTO.of(TYPE_DIRECTORY, dir.getId(), dir.getName(),
                        TransferItemDTO.ACTION_SKIP, "目标环境已存在同 ID 目录，保留目标环境配置"));
                continue;
            }
            report.getItems().add(TransferItemDTO.of(TYPE_DIRECTORY, dir.getId(), dir.getName(),
                    TransferItemDTO.ACTION_CREATE, null));
            if (!dryRun) {
                FlowDirectoryDO toCreate = BundleEntityCopier.detachedCopy(dir, FlowDirectoryDO.class);
                // 父目录不在包内且目标环境也没有时，挂到根目录，避免出现孤儿节点
                if (StrUtil.isNotBlank(toCreate.getParentId())
                        && !flowDirectoryRepository.existsById(toCreate.getParentId())) {
                    toCreate.setParentId(null);
                }
                flowDirectoryService.create(toCreate);
            }
        }

        for (ResponseTemplateDO template : nullSafe(bundle.getResponseTemplates())) {
            handleTemplate(template, report, overwriteExisting, dryRun, now);
        }
        for (FlowApiDO api : nullSafe(bundle.getApis())) {
            handleApi(api, report, overwriteExisting, dryRun, now);
        }
        for (BundleExcelTemplate excel : nullSafe(bundle.getExcelTemplates())) {
            handleExcelTemplate(excel, bundle, report, overwriteExisting, dryRun, now);
        }
        for (FlowServiceFlowDO service : nullSafe(bundle.getServices())) {
            handleService(service, report, overwriteExisting, dryRun, now);
        }
        for (FlowTaskDO task : nullSafe(bundle.getTasks())) {
            handleTask(task, report, overwriteExisting, dryRun, now);
        }
        for (FlowMqTaskDO mqTask : nullSafe(bundle.getMqTasks())) {
            handleMqTask(mqTask, report, overwriteExisting, dryRun, now);
        }
        configAssetTransfer.analyze(bundle, report, overwriteExisting, dryRun, now, this::resolveDirectoryId);
        for (BundleRegressionSuite suite : nullSafe(bundle.getRegressionSuites())) {
            handleRegression(suite, report, overwriteExisting, dryRun, now);
        }

        report.setRequirements(checkRequirements(bundle));
        for (TransferItemDTO item : report.getItems()) {
            switch (item.getAction()) {
                case TransferItemDTO.ACTION_CREATE -> report.setCreateCount(report.getCreateCount() + 1);
                case TransferItemDTO.ACTION_UPDATE -> report.setUpdateCount(report.getUpdateCount() + 1);
                case TransferItemDTO.ACTION_SKIP -> report.setSkipCount(report.getSkipCount() + 1);
                case TransferItemDTO.ACTION_OFFLINE -> report.setOfflineCount(report.getOfflineCount() + 1);
                default -> report.setConflictCount(report.getConflictCount() + 1);
            }
        }
        report.setBlocked(report.getConflictCount() > 0);
        return report;
    }

    private void handleApi(FlowApiDO incoming, TransferReportDTO report, boolean overwriteExisting,
                           boolean dryRun, LocalDateTime now) {
        String id = incoming.getId();
        String name = incoming.getName();
        if (StrUtil.isBlank(id)) {
            report.getItems().add(TransferItemDTO.of(TYPE_API, null, name,
                    TransferItemDTO.ACTION_CONFLICT, "缺少 ID，包体不完整"));
            return;
        }
        if (HostCatalogReserved.isReservedId(id)) {
            report.getItems().add(TransferItemDTO.of(TYPE_API, id, name,
                    TransferItemDTO.ACTION_SKIP, "系统保留接口不参与迁移"));
            return;
        }
        if (StrUtil.isBlank(incoming.getUrl()) || StrUtil.isBlank(incoming.getMethod())) {
            report.getItems().add(TransferItemDTO.of(TYPE_API, id, name,
                    TransferItemDTO.ACTION_CONFLICT, "缺少 path 或 method"));
            return;
        }
        // 同 path+method 被目标环境「其它接口」占用时无法自动决断，必须人工处理
        if (flowApiRepository.existsDraftByUrlAndMethod(incoming.getUrl(), incoming.getMethod(), id)) {
            report.getItems().add(TransferItemDTO.of(TYPE_API, id, name, TransferItemDTO.ACTION_CONFLICT,
                    "目标环境已有其它接口占用 " + incoming.getMethod().toUpperCase() + " " + incoming.getUrl()));
            return;
        }
        FlowApiDO existing = flowApiRepository.findById(id).orElse(null);
        boolean softDeleted = existing == null && flowApiRepository.countAnyById(id) > 0;
        boolean occupied = existing != null || softDeleted;
        if (occupied && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(TYPE_API, id, name, TransferItemDTO.ACTION_SKIP,
                    softDeleted ? "目标环境存在同 ID 的已删除接口，未开启覆盖" : "目标环境已存在，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(TYPE_API, id, name,
                occupied ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE,
                softDeleted ? "恢复目标环境已删除的同 ID 接口并覆盖草稿"
                        : (existing != null ? "仅覆盖草稿，线上快照不变，需重新发布才生效" : null)));
        if (dryRun) {
            return;
        }
        if (softDeleted) {
            flowApiRepository.restoreDeletedById(id);
            existing = flowApiRepository.findById(id).orElse(null);
        }
        FlowApiDO target = existing != null ? existing : newApi(id, now);
        target.setName(incoming.getName());
        target.setUrl(incoming.getUrl());
        target.setMethod(incoming.getMethod());
        target.setDirectoryId(resolveDirectoryId(incoming.getDirectoryId()));
        target.setResponseType(incoming.getResponseType());
        target.setVersion(incoming.getVersion());
        target.setServiceType(incoming.getServiceType());
        target.setInterceptMode(incoming.getInterceptMode());
        target.setHostBinding(incoming.getHostBinding());
        target.setDslContent(incoming.getDslContent());
        target.setSqlContent(incoming.getSqlContent());
        target.setJsonContent(incoming.getJsonContent());
        target.setTextContent(incoming.getTextContent());
        target.setDatasource(incoming.getDatasource());
        target.setContract(incoming.getContract());
        target.setCacheConfig(incoming.getCacheConfig());
        target.setSecurityConfig(incoming.getSecurityConfig());
        target.setViewExportConfig(incoming.getViewExportConfig());
        target.setTemplateId(incoming.getTemplateId());
        target.setCustomSuccessWrapper(incoming.getCustomSuccessWrapper());
        target.setCustomPageWrapper(incoming.getCustomPageWrapper());
        target.setCustomFailWrapper(incoming.getCustomFailWrapper());
        target.setLevel(incoming.getLevel());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setLogRetentionDays(incoming.getLogRetentionDays());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
        flowApiRepository.save(target);
    }

    private FlowApiDO newApi(String id, LocalDateTime now) {
        FlowApiDO api = new FlowApiDO();
        api.setId(id);
        api.setPublishStatus(0);
        api.setDeleted(0);
        api.setCreateTime(now);
        return api;
    }

    private void handleService(FlowServiceFlowDO incoming, TransferReportDTO report, boolean overwriteExisting,
                               boolean dryRun, LocalDateTime now) {
        String id = incoming.getId();
        if (StrUtil.isBlank(id)) {
            report.getItems().add(TransferItemDTO.of(TYPE_SERVICE, null, incoming.getName(),
                    TransferItemDTO.ACTION_CONFLICT, "缺少 ID，包体不完整"));
            return;
        }
        FlowServiceFlowDO existing = flowServiceFlowRepository.findById(id).orElse(null);
        boolean softDeleted = existing == null && flowServiceFlowRepository.countAnyById(id) > 0;
        boolean occupied = existing != null || softDeleted;
        if (occupied && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(TYPE_SERVICE, id, incoming.getName(),
                    TransferItemDTO.ACTION_SKIP,
                    softDeleted ? "目标环境存在同 ID 的已删除服务，未开启覆盖" : "目标环境已存在，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(TYPE_SERVICE, id, incoming.getName(),
                occupied ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE,
                softDeleted ? "恢复目标环境已删除的同 ID 服务并覆盖草稿"
                        : (existing != null ? "仅覆盖草稿，线上快照不变，需重新发布才生效" : null)));
        if (dryRun) {
            return;
        }
        if (softDeleted) {
            flowServiceFlowRepository.restoreDeletedById(id);
            existing = flowServiceFlowRepository.findById(id).orElse(null);
        }
        FlowServiceFlowDO target = existing;
        if (target == null) {
            target = new FlowServiceFlowDO();
            target.setId(id);
            target.setPublishStatus(0);
            target.setDeleted(0);
            target.setCreateTime(now);
            // 启停是目标环境的运维状态，仅新建时取包内值
            target.setEnabled(incoming.getEnabled() != null ? incoming.getEnabled() : Boolean.TRUE);
        }
        target.setName(incoming.getName());
        target.setDirectoryId(resolveDirectoryId(incoming.getDirectoryId()));
        target.setDslContent(incoming.getDslContent());
        target.setContract(incoming.getContract());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
        flowServiceFlowRepository.save(target);
    }

    private void handleTask(FlowTaskDO incoming, TransferReportDTO report, boolean overwriteExisting,
                            boolean dryRun, LocalDateTime now) {
        String id = incoming.getId();
        if (StrUtil.isBlank(id)) {
            report.getItems().add(TransferItemDTO.of(TYPE_TASK, null, incoming.getName(),
                    TransferItemDTO.ACTION_CONFLICT, "缺少 ID，包体不完整"));
            return;
        }
        if (StrUtil.isBlank(incoming.getCron()) || !CronExpression.isValidExpression(incoming.getCron().trim())) {
            report.getItems().add(TransferItemDTO.of(TYPE_TASK, id, incoming.getName(),
                    TransferItemDTO.ACTION_CONFLICT, "Cron 表达式不合法: " + StrUtil.nullToEmpty(incoming.getCron())));
            return;
        }
        FlowTaskDO existing = flowTaskRepository.findById(id).orElse(null);
        boolean softDeleted = existing == null && flowTaskRepository.countAnyById(id) > 0;
        boolean occupied = existing != null || softDeleted;
        if (occupied && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(TYPE_TASK, id, incoming.getName(),
                    TransferItemDTO.ACTION_SKIP,
                    softDeleted ? "目标环境存在同 ID 的已删除任务，未开启覆盖" : "目标环境已存在，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(TYPE_TASK, id, incoming.getName(),
                occupied ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE,
                softDeleted ? "恢复目标环境已删除的同 ID 任务并覆盖草稿"
                        : (existing != null ? "仅覆盖草稿，线上调度按已发布快照运行，需重新发布才生效" : null)));
        if (dryRun) {
            return;
        }
        if (softDeleted) {
            flowTaskRepository.restoreDeletedById(id);
            existing = flowTaskRepository.findById(id).orElse(null);
        }
        FlowTaskDO target = existing;
        if (target == null) {
            target = new FlowTaskDO();
            target.setId(id);
            target.setPublishStatus(0);
            target.setDeleted(0);
            target.setCreateTime(now);
            // 新建即为未发布草稿，不会被调度器注册
            target.setEnabled(incoming.getEnabled() != null ? incoming.getEnabled() : Boolean.TRUE);
        }
        target.setName(incoming.getName());
        target.setDirectoryId(resolveDirectoryId(incoming.getDirectoryId()));
        target.setCron(incoming.getCron().trim());
        target.setDslContent(incoming.getDslContent());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setLogRetentionDays(incoming.getLogRetentionDays());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
        flowTaskRepository.save(target);
    }

    private void handleMqTask(FlowMqTaskDO incoming, TransferReportDTO report, boolean overwriteExisting,
                              boolean dryRun, LocalDateTime now) {
        String id = incoming.getId();
        if (StrUtil.isBlank(id)) {
            report.getItems().add(TransferItemDTO.of(TYPE_MQ_TASK, null, incoming.getName(),
                    TransferItemDTO.ACTION_CONFLICT, "缺少 ID，包体不完整"));
            return;
        }
        if (StrUtil.isBlank(incoming.getConnectionCode()) || StrUtil.isBlank(incoming.getTopic())) {
            report.getItems().add(TransferItemDTO.of(TYPE_MQ_TASK, id, incoming.getName(),
                    TransferItemDTO.ACTION_CONFLICT, "缺少连接编码或 topic"));
            return;
        }
        FlowMqTaskDO existing = flowMqTaskRepository.findById(id).orElse(null);
        boolean softDeleted = existing == null && flowMqTaskRepository.countAnyById(id) > 0;
        boolean occupied = existing != null || softDeleted;
        if (occupied && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(TYPE_MQ_TASK, id, incoming.getName(),
                    TransferItemDTO.ACTION_SKIP,
                    softDeleted ? "目标环境存在同 ID 的已删除 MQ 任务，未开启覆盖" : "目标环境已存在，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(TYPE_MQ_TASK, id, incoming.getName(),
                occupied ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE,
                softDeleted ? "恢复目标环境已删除的同 ID MQ 任务并覆盖草稿"
                        : (existing != null ? "仅覆盖草稿，线上订阅按已发布快照运行，需重新发布才生效" : null)));
        if (dryRun) {
            return;
        }
        if (softDeleted) {
            flowMqTaskRepository.restoreDeletedById(id);
            existing = flowMqTaskRepository.findById(id).orElse(null);
        }
        FlowMqTaskDO target = existing;
        if (target == null) {
            target = new FlowMqTaskDO();
            target.setId(id);
            target.setPublishStatus(0);
            target.setDeleted(0);
            target.setCreateTime(now);
            // 启停是目标环境的运维状态，仅新建时取包内值
            target.setEnabled(incoming.getEnabled() != null ? incoming.getEnabled() : Boolean.TRUE);
        }
        target.setName(incoming.getName());
        target.setDirectoryId(resolveDirectoryId(incoming.getDirectoryId()));
        target.setConnectionCode(incoming.getConnectionCode());
        target.setTopic(incoming.getTopic());
        target.setConsumerGroup(incoming.getConsumerGroup());
        target.setConcurrency(incoming.getConcurrency());
        target.setRetryMax(incoming.getRetryMax());
        target.setRetryBackoffMs(incoming.getRetryBackoffMs());
        target.setDeadLetterTopic(incoming.getDeadLetterTopic());
        target.setDslContent(incoming.getDslContent());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setLogPayloadMode(incoming.getLogPayloadMode());
        target.setLogRetentionDays(incoming.getLogRetentionDays());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
        flowMqTaskRepository.save(target);
    }

    /** 响应模板没有草稿/发布之分，导入即生效；目标环境的「默认模板」标记保持不变 */
    private void handleTemplate(ResponseTemplateDO incoming, TransferReportDTO report, boolean overwriteExisting,
                                boolean dryRun, LocalDateTime now) {
        String id = incoming.getId();
        String name = incoming.getTemplateName();
        if (StrUtil.isBlank(id) || StrUtil.isBlank(name)) {
            report.getItems().add(TransferItemDTO.of(TYPE_TEMPLATE, id, name,
                    TransferItemDTO.ACTION_CONFLICT, "缺少 ID 或模板名称，包体不完整"));
            return;
        }
        if (responseTemplateRepository.existsByTemplateNameAndIdNot(name, id)) {
            report.getItems().add(TransferItemDTO.of(TYPE_TEMPLATE, id, name, TransferItemDTO.ACTION_CONFLICT,
                    "目标环境已有其它模板使用名称「" + name + "」"));
            return;
        }
        ResponseTemplateDO existing = responseTemplateRepository.findById(id).orElse(null);
        if (existing != null && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(TYPE_TEMPLATE, id, name,
                    TransferItemDTO.ACTION_SKIP, "目标环境已存在，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(TYPE_TEMPLATE, id, name,
                existing != null ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE,
                existing != null ? "导入即生效" : null));
        if (dryRun) {
            return;
        }
        ResponseTemplateDO target = existing;
        if (target == null) {
            target = new ResponseTemplateDO();
            target.setId(id);
            target.setIsDefault(0);
            target.setCreateTime(now);
        }
        target.setTemplateName(name);
        target.setSuccessWrapper(incoming.getSuccessWrapper());
        target.setPageWrapper(incoming.getPageWrapper());
        target.setFailWrapper(incoming.getFailWrapper());
        target.setRemark(incoming.getRemark());
        target.setUpdateTime(now);
        responseTemplateRepository.save(target);
    }

    private void handleExcelTemplate(BundleExcelTemplate incoming, AssetBundle bundle, TransferReportDTO report,
                                     boolean overwriteExisting, boolean dryRun, LocalDateTime now) {
        String apiId = incoming.getApiId();
        String name = incoming.getFileName();
        boolean apiInBundle = nullSafe(bundle.getApis()).stream().anyMatch(a -> apiId != null && apiId.equals(a.getId()));
        if (StrUtil.isBlank(apiId) || StrUtil.isBlank(incoming.getContentBase64()) || !apiInBundle) {
            report.getItems().add(TransferItemDTO.of(TYPE_EXCEL, apiId, name,
                    TransferItemDTO.ACTION_SKIP, "所属接口不在包内，模板未导入"));
            return;
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(incoming.getContentBase64());
        } catch (IllegalArgumentException e) {
            report.getItems().add(TransferItemDTO.of(TYPE_EXCEL, apiId, name,
                    TransferItemDTO.ACTION_CONFLICT, "模板内容损坏"));
            return;
        }
        if (content.length > MAX_EXCEL_BYTES) {
            report.getItems().add(TransferItemDTO.of(TYPE_EXCEL, apiId, name,
                    TransferItemDTO.ACTION_CONFLICT, "模板超过 " + (MAX_EXCEL_BYTES / 1024 / 1024) + "MB"));
            return;
        }
        FlowApiExcelTemplateDO existing = flowApiExcelTemplateRepository.findByApiId(apiId).orElse(null);
        if (existing != null && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(TYPE_EXCEL, apiId, name,
                    TransferItemDTO.ACTION_SKIP, "目标环境已有模板，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(TYPE_EXCEL, apiId, name,
                existing != null ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE, null));
        if (dryRun) {
            return;
        }
        FlowApiExcelTemplateDO target = existing != null ? existing : new FlowApiExcelTemplateDO();
        if (existing == null) {
            target.setApiId(apiId);
            target.setCreateTime(now);
        }
        target.setFileName(name);
        target.setContentType(incoming.getContentType());
        target.setContent(content);
        target.setFileSize(content.length);
        target.setUpdateTime(now);
        FlowApiExcelTemplateDO saved = flowApiExcelTemplateRepository.save(target);
        syncTemplateFileId(apiId, saved.getId());
    }

    /**
     * 接口导出配置里记着模板行 ID；模板在目标环境是另一行，这里改成目标环境的 ID。
     * 运行时按接口 ID 取模板，不改也能用，但配置里留着来源环境的 ID 容易误导排查。
     */
    private void syncTemplateFileId(String apiId, String templateId) {
        flowApiRepository.findById(apiId).ifPresent(api -> {
            if (StrUtil.isBlank(api.getViewExportConfig()) || StrUtil.isBlank(templateId)) {
                return;
            }
            try {
                JsonNode node = FlowObjectMapperUtil.flowObjectMapper().readTree(api.getViewExportConfig());
                if (node instanceof ObjectNode obj && obj.hasNonNull("templateFileId")
                        && !templateId.equals(obj.get("templateFileId").asText())) {
                    obj.put("templateFileId", templateId);
                    api.setViewExportConfig(FlowObjectMapperUtil.flowObjectMapper().writeValueAsString(obj));
                    flowApiRepository.save(api);
                }
            } catch (Exception e) {
                log.warn("[AssetTransfer] 改写接口 {} 的 Excel 模板 ID 失败: {}", apiId, e.getMessage());
            }
        });
    }

    private void handleRegression(BundleRegressionSuite item, TransferReportDTO report, boolean overwriteExisting,
                                  boolean dryRun, LocalDateTime now) {
        FlowRegressionSuiteDO suite = item == null ? null : item.getSuite();
        if (suite == null || StrUtil.isBlank(suite.getId()) || StrUtil.isBlank(suite.getAssetId())) {
            return;
        }
        if (!assetExists(suite.getAssetType(), suite.getAssetId())) {
            report.getItems().add(TransferItemDTO.of(TYPE_REGRESSION, suite.getId(), suite.getName(),
                    TransferItemDTO.ACTION_SKIP, "关联资产不在目标环境，套件未导入"));
            return;
        }
        boolean exists = flowRegressionSuiteRepository.existsById(suite.getId());
        if (exists && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(TYPE_REGRESSION, suite.getId(), suite.getName(),
                    TransferItemDTO.ACTION_SKIP, "目标环境已存在，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(TYPE_REGRESSION, suite.getId(), suite.getName(),
                exists ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE,
                "用例 " + item.getCases().size() + " 条"));
        if (dryRun) {
            return;
        }
        FlowRegressionSuiteDO target = BundleEntityCopier.detachedCopy(suite, FlowRegressionSuiteDO.class);
        target.setEnabled(target.getEnabled() == null ? 1 : target.getEnabled());
        target.setCreateTime(now);
        target.setUpdateTime(now);
        flowRegressionSuiteRepository.save(target);
        // 用例整体以包内为准，避免目标环境残留已删除的旧用例；
        // 同 ID 用例原地覆盖而不是先删后建，Hibernate 不允许在同一事务里 merge 已删除的实例
        Set<String> incomingCaseIds = new LinkedHashSet<>();
        item.getCases().forEach(c -> addIfNotBlank(incomingCaseIds, c.getId()));
        flowRegressionCaseRepository.deleteAll(flowRegressionCaseRepository
                .findBySuiteIdOrderBySortOrderAsc(target.getId()).stream()
                .filter(c -> !incomingCaseIds.contains(c.getId()))
                .toList());
        int order = 0;
        for (FlowRegressionCaseDO source : item.getCases()) {
            FlowRegressionCaseDO copy = BundleEntityCopier.detachedCopy(source, FlowRegressionCaseDO.class);
            copy.setSuiteId(target.getId());
            copy.setSortOrder(copy.getSortOrder() == null ? order : copy.getSortOrder());
            copy.setEnabled(copy.getEnabled() == null ? 1 : copy.getEnabled());
            copy.setTimeoutMs(copy.getTimeoutMs() == null ? 10000 : copy.getTimeoutMs());
            copy.setCreateTime(now);
            copy.setUpdateTime(now);
            flowRegressionCaseRepository.save(copy);
            order++;
        }
    }

    private boolean assetExists(String assetType, String assetId) {
        String type = StrUtil.nullToEmpty(assetType).toUpperCase();
        return switch (type) {
            case TYPE_API -> flowApiRepository.existsById(assetId);
            case TYPE_SERVICE -> flowServiceFlowRepository.existsById(assetId);
            case TYPE_TASK -> flowTaskRepository.existsById(assetId);
            default -> false;
        };
    }

    /** 目录不存在时置空，让资产落到根目录，而不是指向一个不存在的目录 */
    private String resolveDirectoryId(String directoryId) {
        if (StrUtil.isBlank(directoryId) || !flowDirectoryRepository.existsById(directoryId)) {
            return null;
        }
        return directoryId;
    }

    private List<TransferRequirementDTO> checkRequirements(AssetBundle bundle) {
        List<TransferRequirementDTO> result = new ArrayList<>();
        Map<String, javax.sql.DataSource> loaded = dynamicDataSourceService.getAllDataSources();
        Set<String> bundledTemplates = new LinkedHashSet<>();
        nullSafe(bundle.getResponseTemplates()).forEach(t -> addIfNotBlank(bundledTemplates, t.getId()));
        for (TransferRequirementDTO source : nullSafe(bundle.getRequirements())) {
            TransferRequirementDTO req = new TransferRequirementDTO();
            req.setKind(source.getKind());
            req.setKey(source.getKey());
            req.setRemark(source.getRemark());
            req.setAttributes(source.getAttributes());
            req.setUsedBy(source.getUsedBy());
            req.setSatisfied(switch (StrUtil.nullToEmpty(source.getKind())) {
                // 只有启用且已补填的资源才算具备：导入即发布，占位未补填时接口一上线就会报错
                case TransferRequirementDTO.KIND_DATASOURCE -> loaded.containsKey(source.getKey());
                case TransferRequirementDTO.KIND_MQ -> mqConnectionRepository.findByCode(source.getKey())
                        .map(c -> Boolean.TRUE.equals(c.getEnabled())).orElse(false);
                case TransferRequirementDTO.KIND_OSS -> ossConnectionRepository.findByCode(source.getKey())
                        .map(c -> Boolean.TRUE.equals(c.getEnabled())).orElse(false);
                case TransferRequirementDTO.KIND_TEMPLATE -> bundledTemplates.contains(source.getKey())
                        || responseTemplateRepository.existsById(source.getKey());
                case TransferRequirementDTO.KIND_ENV_VAR -> envVariableCacheManager.get(source.getKey())
                        .map(v -> StrUtil.isNotEmpty(v.value())).orElse(false);
                case TransferRequirementDTO.KIND_ALERT_CHANNEL -> configAssetTransfer.alertChannelReady(source.getKey());
                default -> true;
            });
            result.add(req);
        }
        return result;
    }

    private void validateBundle(AssetBundle bundle) {
        if (bundle == null) {
            throw new FlowException("TRANSFER_BAD_BUNDLE", "资产包不能为空");
        }
        if (StrUtil.isNotBlank(bundle.getKind()) && !AssetBundle.KIND.equals(bundle.getKind())) {
            throw new FlowException("TRANSFER_BAD_BUNDLE", "文件不是 Yu Flow 资产包");
        }
        if (bundle.getSchemaVersion() != null && bundle.getSchemaVersion() > AssetBundle.SCHEMA_VERSION) {
            throw new FlowException("TRANSFER_BAD_BUNDLE",
                    "资产包版本(" + bundle.getSchemaVersion() + ")高于当前系统支持的版本("
                            + AssetBundle.SCHEMA_VERSION + ")，请升级目标环境");
        }
        if (nullSafe(bundle.getApis()).isEmpty()
                && nullSafe(bundle.getServices()).isEmpty()
                && nullSafe(bundle.getTasks()).isEmpty()
                && nullSafe(bundle.getMqTasks()).isEmpty()
                && nullSafe(bundle.getResponseTemplates()).isEmpty()
                && !configAssetTransfer.hasAny(bundle)) {
            throw new FlowException("TRANSFER_BAD_BUNDLE", "资产包内没有可导入的资产");
        }
    }

    /**
     * 导入等价于批量新建资产，逐类校验写权限，避免只有接口权限的账号顺带写入服务与定时任务。
     */
    private void assertImportPermissions(AssetBundle bundle) {
        if (bundle == null || !rbacService.isRbacEnabled()) {
            return;
        }
        String username = JwtTokenUtil.currentUsername();
        if (StrUtil.isBlank(username)) {
            throw new FlowException("RBAC_UNAUTHORIZED", "未登录或凭证无效");
        }
        if (!nullSafe(bundle.getApis()).isEmpty()) {
            requirePerm(username, "flow:api:write");
        }
        if (!nullSafe(bundle.getServices()).isEmpty()) {
            requirePerm(username, "flow:service:write");
        }
        if (!nullSafe(bundle.getTasks()).isEmpty()) {
            requirePerm(username, "flow:task:write");
        }
        if (!nullSafe(bundle.getMqTasks()).isEmpty()) {
            requirePerm(username, "flow:mq:write");
        }
        if (!nullSafe(bundle.getResponseTemplates()).isEmpty()) {
            requirePerm(username, "sys:template:write");
        }
        if (!nullSafe(bundle.getPages()).isEmpty()) {
            requirePerm(username, "flow:page:write");
        }
        if (!nullSafe(bundle.getModels()).isEmpty()) {
            requirePerm(username, "flow:model:write");
        }
        if (!nullSafe(bundle.getSysMacros()).isEmpty()) {
            requirePerm(username, "sys:macro:write");
        }
        if (!nullSafe(bundle.getSysConfigs()).isEmpty()) {
            requirePerm(username, "sys:config:write");
        }
        if (!nullSafe(bundle.getOpenPlatforms()).isEmpty()) {
            requirePerm(username, "flow:open:write");
        }
        if (!nullSafe(bundle.getAlertRules()).isEmpty()) {
            requirePerm(username, "flow:alert:edit");
        }
        if (!nullSafe(bundle.getRegressionSuites()).isEmpty()) {
            requirePerm(username, "flow:release:edit");
        }
    }

    private void requirePerm(String username, String code) {
        if (!rbacService.hasAnyPerm(username, code)) {
            throw new FlowException("RBAC_FORBIDDEN", "无权限: " + code);
        }
    }

    /** 父目录必须先于子目录创建 */
    private List<FlowDirectoryDO> sortDirectoriesByDepth(List<FlowDirectoryDO> directories) {
        List<FlowDirectoryDO> source = new ArrayList<>(nullSafe(directories));
        Map<String, FlowDirectoryDO> byId = new LinkedHashMap<>();
        source.forEach(d -> {
            if (StrUtil.isNotBlank(d.getId())) {
                byId.put(d.getId(), d);
            }
        });
        List<FlowDirectoryDO> ordered = new ArrayList<>(source.size());
        Set<String> placed = new LinkedHashSet<>();
        for (FlowDirectoryDO dir : source) {
            placeDirectory(dir, byId, placed, ordered, 0);
        }
        return ordered;
    }

    private void placeDirectory(FlowDirectoryDO dir, Map<String, FlowDirectoryDO> byId, Set<String> placed,
                                List<FlowDirectoryDO> ordered, int depth) {
        if (dir == null || StrUtil.isBlank(dir.getId()) || placed.contains(dir.getId()) || depth > MAX_DIR_DEPTH) {
            return;
        }
        FlowDirectoryDO parent = StrUtil.isBlank(dir.getParentId()) ? null : byId.get(dir.getParentId());
        if (parent != null) {
            placeDirectory(parent, byId, placed, ordered, depth + 1);
        }
        if (placed.add(dir.getId())) {
            ordered.add(dir);
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
