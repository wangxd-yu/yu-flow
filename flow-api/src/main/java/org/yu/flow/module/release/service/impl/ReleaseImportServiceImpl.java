package org.yu.flow.module.release.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.yu.flow.log.audit.AuditDetail;
import org.yu.flow.module.release.support.AssetEditLock;
import org.yu.flow.module.release.support.ModelTableInspector;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;
import org.yu.flow.module.release.support.ReleaseDiffCalculator;
import org.yu.flow.module.release.support.ReleasePlaceholderCreator;
import org.yu.flow.module.release.support.ReleasePrivilegedChanges;
import org.yu.flow.module.release.support.ReleaseReferenceChecker;
import org.yu.flow.module.release.support.ReleaseRuntimeVerifier;
import org.yu.flow.module.release.support.ReleaseSigning;
import org.yu.flow.module.transfer.dto.TransferItemDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.util.JwtTokenUtil;
import org.yu.flow.cache.FlowRedisUtil;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.security.ApiSecurityConfigGuard;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.service.FlowMqTaskService;
import org.yu.flow.module.release.domain.FlowEnvDO;
import org.yu.flow.module.release.domain.FlowReleaseImportLogDO;
import org.yu.flow.module.release.dto.ReleaseImportLogDTO;
import org.yu.flow.module.release.dto.ReleaseInspectResultDTO;
import org.yu.flow.module.release.repository.FlowEnvRepository;
import org.yu.flow.module.release.repository.FlowReleaseImportLogRepository;
import org.yu.flow.module.release.service.ReleaseImportService;
import org.yu.flow.module.release.support.RegressionEvidenceContext;
import org.yu.flow.module.release.support.ReleaseEnvironment;
import org.yu.flow.module.release.support.ReleaseImportBackup;
import org.yu.flow.module.release.support.ReleaseImportBackupManager;
import org.yu.flow.module.release.support.ReleasePackageFormat;
import org.yu.flow.module.release.support.ReleasePackageReader;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowService;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.service.FlowTaskService;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;
import org.yu.flow.module.transfer.service.AssetTransferService;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class ReleaseImportServiceImpl implements ReleaseImportService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String LOCK_KEY = "flow:release:import:lock";
    private static final long LOCK_TTL_MINUTES = 30;

    private static final Map<String, String> REQUIREMENT_LABELS = Map.of(
            TransferRequirementDTO.KIND_DATASOURCE, "数据源",
            TransferRequirementDTO.KIND_MQ, "MQ 连接",
            TransferRequirementDTO.KIND_OSS, "OSS 连接",
            TransferRequirementDTO.KIND_TEMPLATE, "响应模板",
            TransferRequirementDTO.KIND_ENV_VAR, "环境变量");

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper().copy()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 预检缓存按包解压后大小计额 */
    private static final long INSPECT_CACHE_BYTES = 128L * 1024 * 1024;

    /** Redis 不可用时的单节点兜底，保证同一节点不会并发导入 */
    private final AtomicBoolean localRunning = new AtomicBoolean(false);

    /** 同时进行的预检数：每次预检都要把整包解压进内存 */
    private final Semaphore inspectPermits = new Semaphore(2);

    /** 预检过的发布包，按摘要缓存，查看逐字段差异时不必重新上传 */
    private final Cache<String, ReleasePackageReader.Parsed> inspected = Caffeine.newBuilder()
            .maximumWeight(INSPECT_CACHE_BYTES)
            .weigher((String digest, ReleasePackageReader.Parsed pkg) -> (int) Math.min(Integer.MAX_VALUE, pkg.size()))
            .expireAfterWrite(30, TimeUnit.MINUTES).build();

    @Resource
    private ReleaseSigning releaseSigning;
    @Resource
    private ReleasePrivilegedChanges privilegedChanges;
    @Resource
    private ReleaseReferenceChecker referenceChecker;
    @Resource
    private ReleaseRuntimeVerifier runtimeVerifier;
    @Resource
    private ReleaseDiffCalculator diffCalculator;
    @Resource
    private ModelTableInspector modelTableInspector;
    @Resource
    private ReleasePlaceholderCreator placeholderCreator;
    @Resource
    private ReleaseAssetResolver assetResolver;
    @Resource
    private PageInfoRepository pageInfoRepository;
    @Resource
    private AlertRuleRepository alertRuleRepository;
    @Resource
    private FlowOpenPlatformRepository flowOpenPlatformRepository;
    @Resource
    private OpenPlatformCache openPlatformCache;

    @Resource
    private AssetTransferService assetTransferService;
    @Resource
    private ReleaseImportBackupManager backupManager;
    @Resource
    private FlowReleaseImportLogRepository importLogRepository;
    @Resource
    private FlowEnvRepository flowEnvRepository;
    @Resource
    private ReleaseEnvironment releaseEnvironment;
    @Resource
    private FlowApiCrudService flowApiCrudService;
    @Resource
    private FlowServiceFlowService flowServiceFlowService;
    @Resource
    private FlowTaskService flowTaskService;
    @Resource
    private FlowMqTaskService flowMqTaskService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private DemoModeGuard demoModeGuard;
    @Resource
    private AuditLogService auditLogService;

    // ─────────────────────────────────────────────────────────────────────────
    // 预检
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ReleaseInspectResultDTO inspect(byte[] packageContent) {
        if (!inspectPermits.tryAcquire()) {
            throw new FlowException("RELEASE_INSPECT_BUSY", "当前正在预检的发布包较多，请稍后再试");
        }
        try {
            return inspect(ReleasePackageReader.read(packageContent));
        } finally {
            inspectPermits.release();
        }
    }

    private ReleaseInspectResultDTO inspect(ReleasePackageReader.Parsed pkg) {
        ReleasePackageFormat.Manifest manifest = pkg.manifest();
        String targetEnv = releaseEnvironment.current();
        ReleaseInspectResultDTO result = new ReleaseInspectResultDTO();
        result.setReleaseCode(pkg.release().getCode());
        result.setReleaseName(pkg.release().getName());
        result.setReleaseRemark(pkg.release().getRemark());
        result.setSourceEnv(manifest.getSourceEnv());
        result.setTargetEnv(targetEnv);
        result.setExportedBy(manifest.getExportedBy());
        result.setExportedAt(manifest.getExportedAt());
        result.setPackageDigest(pkg.digest());
        result.setChangelog(pkg.changelog());
        result.setItemCount(pkg.release().getItems().size());

        TransferReportDTO report = hasAssets(pkg.bundle())
                ? transactionTemplate.execute(tx -> assetTransferService.preflight(pkg.bundle(), true))
                : new TransferReportDTO();
        report.setDryRun(true);
        result.setReport(report);
        appendOfflineItems(pkg, report);
        annotateUpdates(pkg, report, result);
        result.setModelTables(modelTableInspector.inspect(pkg.bundle().getModels()));
        result.getModelTables().stream().filter(t -> Boolean.FALSE.equals(t.exists())).forEach(t -> result.getWarnings().add(
                "数据模型「" + t.modelName() + "」的业务表 " + t.tableName() + " 在数据源 " + t.datasource()
                        + " 中不存在，请 DBA 参考「模型建表」中的 DDL 建表"));

        checkSignature(pkg, result);
        result.setPrivilegedChanges(privilegedChanges.collect(pkg.bundle()));
        checkReferences(pkg, result);

        if (report.isBlocked()) {
            result.getBlockReasons().add("有 " + report.getConflictCount() + " 项冲突无法自动处理，见逐项结果");
        }
        for (TransferRequirementDTO req : report.getRequirements()) {
            if (Boolean.FALSE.equals(req.getSatisfied())) {
                result.getBlockReasons().add("缺少" + REQUIREMENT_LABELS.getOrDefault(req.getKind(), req.getKind())
                        + "「" + req.getKey() + "」" + (StrUtil.isNotBlank(req.getRemark()) ? "（" + req.getRemark() + "）" : "")
                        + "，请先在本环境配置");
            }
        }
        previewGates(pkg, targetEnv, result);
        result.getGates().stream().filter(g -> !g.isPassed()).forEach(g -> result.getBlockReasons().add(
                "发布门禁未通过：" + g.getAssetName() + " —— " + g.getMessage()));

        if (StrUtil.isNotBlank(manifest.getSourceEnv()) && manifest.getSourceEnv().equalsIgnoreCase(targetEnv)) {
            result.getWarnings().add("发布包来源环境与本环境相同（" + targetEnv + "），请确认没有导错环境");
        }
        importLogRepository.findFirstByPackageDigestAndStatusOrderByImportedTimeDesc(
                        pkg.digest(), FlowReleaseImportLogDO.STATUS_SUCCESS)
                .ifPresent(prev -> result.getWarnings().add("该发布包已于 " + prev.getImportedTime()
                        + " 由 " + StrUtil.nullToEmpty(prev.getImportedBy()) + " 导入过，重复导入会再次覆盖并发布"));
        result.setBlocked(!result.getBlockReasons().isEmpty());
        inspected.put(pkg.digest(), pkg);
        return result;
    }

    /**
     * 未签名的包只能靠摘要发现损坏，发现不了有意篡改（任何人都能重算摘要），
     * 因此要求签名的环境（PROD / 开启编辑锁 / 显式配置）缺签名或缺密钥时一律阻断。
     */
    private void checkSignature(ReleasePackageReader.Parsed pkg, ReleaseInspectResultDTO result) {
        String signature = releaseSigning.status(pkg.manifestBytes(), pkg.signature());
        boolean required = releaseSigning.required();
        result.setSignatureStatus(signature);
        result.setSignatureRequired(required);
        switch (signature) {
            case ReleaseSigning.INVALID -> result.getBlockReasons().add("发布包签名校验失败，文件可能被修改，或两套环境的签名密钥不一致");
            case ReleaseSigning.UNSIGNED -> result.getBlockReasons().add("本环境要求发布包签名，但该包未签名（来源环境未配置签名密钥）");
            case ReleaseSigning.UNVERIFIABLE -> {
                if (required) {
                    result.getBlockReasons().add("本环境要求校验发布包签名，但没有配置签名密钥（YU_FLOW_RELEASE_SIGNING_KEY），无法校验");
                } else {
                    result.getWarnings().add("发布包带有签名，但本环境未配置签名密钥，无法校验");
                }
            }
            case ReleaseSigning.DISABLED -> {
                if (required) {
                    result.getBlockReasons().add("本环境要求发布包签名：请在两套环境配置相同的签名密钥（YU_FLOW_RELEASE_SIGNING_KEY）后重新导出");
                }
            }
            default -> {
            }
        }
    }

    /** 下线后仍被在线资产调用的阻断；包内资产调用的接口 / 服务缺失的阻断 */
    private void checkReferences(ReleasePackageReader.Parsed pkg, ReleaseInspectResultDTO result) {
        Set<String> offlineKeys = new LinkedHashSet<>();
        List<AssetRef> offlineOnline = new ArrayList<>();
        Map<AssetRef, String> names = ReleaseReferenceChecker.bundleNames(pkg.bundle());
        for (ReleasePackageFormat.ReleaseEntry e : offlineEntries(pkg)) {
            offlineKeys.add(e.getAssetType() + ":" + e.getAssetId());
            AssetRef ref = new AssetRef(e.getAssetType(), e.getAssetId());
            names.put(ref, e.getAssetName());
            if (isOnlineHere(e)) {
                offlineOnline.add(ref);
            }
        }
        referenceChecker.checkOutbound(pkg.bundle(), offlineKeys, result.getBlockReasons());
        for (ReleaseReferenceChecker.Conflict c : referenceChecker.offlineConflicts(offlineOnline,
                ReleaseReferenceChecker.upcomingDsl(pkg.bundle()), names)) {
            result.getBlockReasons().add("下线" + ReleaseAssetResolver.LABELS.get(c.target().type()) + "「" + c.targetName()
                    + "」后，以下在线资产仍会调用它：" + String.join("、", c.callers())
                    + "；请把调用方一起下线，或先发布不再调用它的版本");
        }
    }

    /** 下线项并入逐项结果：目标环境不存在或已下线的标为跳过 */
    private void appendOfflineItems(ReleasePackageReader.Parsed pkg, TransferReportDTO report) {
        for (ReleasePackageFormat.ReleaseEntry e : offlineEntries(pkg)) {
            boolean online = isOnlineHere(e);
            report.getItems().add(TransferItemDTO.of(e.getAssetType(),
                    StrUtil.blankToDefault(e.getAssetKey(), e.getAssetId()), e.getAssetName(),
                    online ? TransferItemDTO.ACTION_OFFLINE : TransferItemDTO.ACTION_SKIP,
                    online ? "撤销发布 / 停用，不删除数据" : "本环境不存在或已下线"));
            if (online) {
                report.setOfflineCount(report.getOfflineCount() + 1);
            } else {
                report.setSkipCount(report.getSkipCount() + 1);
            }
        }
    }

    private boolean isOnlineHere(ReleasePackageFormat.ReleaseEntry e) {
        if (ReleaseAssetResolver.OPEN_PLATFORM.equals(e.getAssetType())) {
            return flowOpenPlatformRepository.findByCode(e.getAssetKey())
                    .map(p -> Integer.valueOf(1).equals(p.getStatus())).orElse(false);
        }
        return ReleaseAssetResolver.OFFLINE_TYPES.contains(e.getAssetType())
                && assetResolver.isOnline(e.getAssetType(), e.getAssetId());
    }

    /**
     * 更新项：列出变化字段；发现生产在上次导入后被直接修改、或有未发布的草稿时给出提示（导入会覆盖）。
     */
    private void annotateUpdates(ReleasePackageReader.Parsed pkg, TransferReportDTO report, ReleaseInspectResultDTO result) {
        Map<String, String> recorded = recordedHashes();
        for (TransferItemDTO item : report.getItems()) {
            if (!TransferItemDTO.ACTION_UPDATE.equals(item.getAction())
                    || !ReleaseAssetResolver.TYPES.contains(item.getAssetType())) {
                continue;
            }
            item.setChangedFields(diffCalculator.changedFields(item.getAssetType(), item.getId(), pkg.bundle()));
            if (ReleaseAssetResolver.KEYED_TYPES.contains(item.getAssetType())) {
                continue;
            }
            ReleaseAssetResolver.AssetInfo current = assetResolver.describe(item.getAssetType(), item.getId());
            if (current == null) {
                continue;
            }
            String label = ReleaseAssetResolver.LABELS.get(item.getAssetType()) + "「" + item.getName() + "」";
            if (current.unpublishedChanges()) {
                result.getWarnings().add(label + "在本环境有未发布的草稿修改，导入会覆盖");
            }
            String last = recorded.get(item.getAssetType() + ":" + item.getId());
            if (last != null && !last.equals(current.contentHash())) {
                result.getWarnings().add(label + "在上次导入后被直接修改过，导入会覆盖这些修改");
            }
        }
    }

    /** 最近若干次成功导入记录的资产指纹，同一资产取最近一次 */
    private Map<String, String> recordedHashes() {
        Map<String, String> hashes = new HashMap<>();
        List<FlowReleaseImportLogDO> recent = importLogRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("status"), FlowReleaseImportLogDO.STATUS_SUCCESS),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "importedTime"))).getContent();
        for (FlowReleaseImportLogDO log : recent) {
            if (StrUtil.isBlank(log.getAssetHashes())) {
                continue;
            }
            try {
                Map<String, String> m = MAPPER.readValue(log.getAssetHashes(), new TypeReference<Map<String, String>>() {
                });
                m.forEach(hashes::putIfAbsent);
            } catch (Exception ignored) {
                // 旧记录格式不对时跳过，不影响预检
            }
        }
        return hashes;
    }

    /** 只有下线项的发布包没有资产可导入 */
    private static boolean hasAssets(AssetBundle b) {
        return !nullSafe(b.getApis()).isEmpty() || !nullSafe(b.getServices()).isEmpty() || !nullSafe(b.getTasks()).isEmpty()
                || !nullSafe(b.getMqTasks()).isEmpty() || !nullSafe(b.getResponseTemplates()).isEmpty()
                || !nullSafe(b.getPages()).isEmpty() || !nullSafe(b.getModels()).isEmpty()
                || !nullSafe(b.getSysMacros()).isEmpty() || !nullSafe(b.getSysConfigs()).isEmpty()
                || !nullSafe(b.getOpenPlatforms()).isEmpty() || !nullSafe(b.getAlertRules()).isEmpty();
    }

    private static List<ReleasePackageFormat.ReleaseEntry> offlineEntries(ReleasePackageReader.Parsed pkg) {
        return pkg.release().getItems().stream().filter(e -> "OFFLINE".equals(e.getAction())).toList();
    }

    /**
     * 目标环境门禁预演。资产可能还不存在，不能直接调用 PublishGateService（它要求资产已落库），
     * 这里按同样的规则就包内内容判断；真正发布时仍会走一遍正式门禁。
     */
    private void previewGates(ReleasePackageReader.Parsed pkg, String env, ReleaseInspectResultDTO result) {
        Optional<FlowEnvDO> envDO = flowEnvRepository.findByCode(env).filter(e -> Integer.valueOf(1).equals(e.getEnabled()));
        boolean requireSuite = envDO.map(e -> Integer.valueOf(1).equals(e.getRequireSuitePass())).orElse(false);
        boolean allowNone = yuFlowProperties.getSecurity() != null && yuFlowProperties.getSecurity().isAllowIngressAuthNone();
        Map<String, String> evidence = evidenceOf(pkg);
        AssetBundle bundle = pkg.bundle();

        List<ReleaseInspectResultDTO.GateItem> gates = result.getGates();
        for (FlowApiDO api : nullSafe(bundle.getApis())) {
            if (HostCatalogReserved.isReservedId(api.getId())) {
                continue;
            }
            String problem = envProblem(envDO.isPresent(), env);
            if (problem == null) {
                problem = regressionProblem(requireSuite, evidence, "API", api.getId(), env);
            }
            if (problem == null && !allowNone && ApiSecurityConfigGuard.isExplicitNone(api.getSecurityConfig())) {
                problem = "接口 authMode=NONE（匿名可调）在本环境被禁止";
            }
            gates.add(gate("API", api.getId(), api.getName(), problem, evidence));
        }
        for (FlowServiceFlowDO s : nullSafe(bundle.getServices())) {
            String problem = envProblem(envDO.isPresent(), env);
            gates.add(gate("SERVICE", s.getId(), s.getName(),
                    problem != null ? problem : regressionProblem(requireSuite, evidence, "SERVICE", s.getId(), env), evidence));
        }
        for (FlowTaskDO t : nullSafe(bundle.getTasks())) {
            String problem = envProblem(envDO.isPresent(), env);
            gates.add(gate("TASK", t.getId(), t.getName(),
                    problem != null ? problem : regressionProblem(requireSuite, evidence, "TASK", t.getId(), env), evidence));
        }
        if (requireSuite) {
            warnStaleEvidence(pkg, envDO.get(), result);
        }
    }

    /** 按用户选定的策略认可来源环境证据，但证据早于本环境的回归时效时提示运维 */
    private static void warnStaleEvidence(ReleasePackageReader.Parsed pkg, FlowEnvDO env, ReleaseInspectResultDTO result) {
        Integer ttl = env.getPassTtlHours();
        if (ttl == null || ttl <= 0) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now(ZONE).minusHours(ttl);
        for (ReleasePackageFormat.ReleaseEntry e : pkg.release().getItems()) {
            if (StrUtil.isBlank(e.getRegressionPassedAt())) {
                continue;
            }
            LocalDateTime passedAt = LocalDateTime.parse(e.getRegressionPassedAt(), DATE_TIME);
            if (passedAt.isBefore(deadline)) {
                result.getWarnings().add(ReleaseAssetResolver.LABELS.getOrDefault(e.getAssetType(), e.getAssetType())
                        + "「" + e.getAssetName() + "」的来源环境回归通过于 " + e.getRegressionPassedAt()
                        + "，早于本环境要求的 " + ttl + " 小时时效；仍按来源证据放行，请确认这段时间依赖的外部系统没有变化");
            }
        }
    }

    private static String envProblem(boolean envOk, String env) {
        return envOk ? null : "本环境（" + env + "）在「环境」配置中不存在或已停用";
    }

    private static String regressionProblem(boolean requireSuite, Map<String, String> evidence,
                                            String type, String id, String env) {
        if (!requireSuite || evidence.containsKey(type + ":" + id)) {
            return null;
        }
        return "本环境（" + env + "）要求回归通过，但发布包没有该资产在来源环境的回归通过记录；"
                + "请在来源环境对当前线上版本运行回归后重新导出";
    }

    private static ReleaseInspectResultDTO.GateItem gate(String type, String id, String name, String problem,
                                                         Map<String, String> evidence) {
        String ok = evidence.containsKey(type + ":" + id) ? "通过（来源环境回归：" + evidence.get(type + ":" + id) + "）" : "通过";
        return new ReleaseInspectResultDTO.GateItem(type, id, name, problem == null, problem == null ? ok : problem);
    }

    private static Map<String, String> evidenceOf(ReleasePackageReader.Parsed pkg) {
        Map<String, String> evidence = new HashMap<>();
        for (ReleasePackageFormat.ReleaseEntry e : pkg.release().getItems()) {
            if (StrUtil.isNotBlank(e.getRegressionPassedAt())) {
                evidence.put(e.getAssetType() + ":" + e.getAssetId(),
                        e.getRegressionEnv() + " " + e.getRegressionPassedAt());
            }
        }
        return evidence;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 导入
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ReleaseImportLogDTO execute(byte[] packageContent, String confirmCode, boolean privilegedConfirmed) {
        assertWritable();
        ReleasePackageReader.Parsed pkg = ReleasePackageReader.read(packageContent);
        if (!StrUtil.equals(StrUtil.trim(confirmCode), pkg.release().getCode())) {
            throw new FlowException("RELEASE_CONFIRM_MISMATCH", "输入的版本号与发布包不一致，请核对后重试");
        }
        String lockValue = acquireLock();
        try {
            ReleaseInspectResultDTO inspect = inspect(pkg);
            if (inspect.isBlocked()) {
                throw new FlowException("RELEASE_IMPORT_BLOCKED", "预检未通过：" + String.join("；", inspect.getBlockReasons()));
            }
            if (!inspect.getPrivilegedChanges().isEmpty() && !privilegedConfirmed) {
                throw new FlowException("RELEASE_PRIVILEGED_UNCONFIRMED", "发布包包含 " + inspect.getPrivilegedChanges().size()
                        + " 项高权限变更（全局宏 / 开放平台授权），请在预检结果中逐项核对并勾选确认后再导入");
            }
            FlowReleaseImportLogDO saved;
            try {
                // 编辑锁只放行导入本身：写入资产、建目录、发布、下线都在这一个事务里
                saved = transactionTemplate.execute(tx -> AssetEditLock.runUnlocked(() -> doImport(pkg)));
            } catch (RuntimeException e) {
                recordFailure(pkg, e);
                throw e;
            }
            saved = afterImport(saved, pkg.bundle());
            auditLogService.record("RELEASE_IMPORT", "RELEASE_IMPORT", saved.getId(), AuditDetail.of(
                    "code", pkg.release().getCode(), "digest", pkg.digest(), "signature", inspect.getSignatureStatus(),
                    "privilegedChanges", inspect.getPrivilegedChanges().size()));
            return ReleaseImportLogDTO.fromDO(saved);
        } finally {
            releaseLock(lockValue);
        }
    }

    /**
     * 事务已提交、调度与订阅已在提交回调里执行：自检运行时状态写回导入记录，再清理过期备份。
     * 这两步失败都不影响已完成的导入。
     */
    private FlowReleaseImportLogDO afterImport(FlowReleaseImportLogDO saved, AssetBundle bundle) {
        try {
            List<String> issues = runtimeVerifier.verify(bundle);
            if (!issues.isEmpty()) {
                saved.setRuntimeIssues(toJson(issues));
                saved = importLogRepository.save(saved);
            }
        } catch (Exception e) {
            log.warn("[ReleaseImport] 运行时自检失败: {}", e.getMessage());
        }
        try {
            pruneBackups();
        } catch (Exception e) {
            log.warn("[ReleaseImport] 清理过期备份失败: {}", e.getMessage());
        }
        return saved;
    }

    /** 只保留最近若干条记录的导入前备份：只有最近一次导入能回滚，更早的备份只占空间 */
    private void pruneBackups() {
        int keep = yuFlowProperties.getRelease() == null ? 10 : yuFlowProperties.getRelease().getBackupRetention();
        if (keep <= 0) {
            return;
        }
        List<String> ids = importLogRepository.findIdsWithBackup();
        if (ids.size() <= keep) {
            return;
        }
        List<String> expired = new ArrayList<>(ids.subList(keep, ids.size()));
        for (int from = 0; from < expired.size(); from += 500) {
            List<String> chunk = expired.subList(from, Math.min(from + 500, expired.size()));
            transactionTemplate.executeWithoutResult(tx -> importLogRepository.clearBackups(chunk));
        }
        log.info("[ReleaseImport] 已清理 {} 条过期的导入前备份", expired.size());
    }

    private FlowReleaseImportLogDO doImport(ReleasePackageReader.Parsed pkg) {
        AssetBundle bundle = pkg.bundle();
        List<ReleasePackageFormat.ReleaseEntry> offline = offlineEntries(pkg);
        ReleaseImportBackup backup = backupManager.snapshot(bundle, offline);
        TransferReportDTO report = hasAssets(bundle)
                ? assetTransferService.importBundleAuthorized(bundle, true) : new TransferReportDTO();
        // 先按执行前的状态记下线项，执行后它们就都已下线了
        appendOfflineItems(pkg, report);
        String env = releaseEnvironment.current();
        int[] counts = new int[]{
                RegressionEvidenceContext.runWith(evidenceOf(pkg), () -> publishAll(bundle, env)),
                offlineAll(offline)};

        FlowReleaseImportLogDO row = baseLog(pkg);
        row.setStatus(FlowReleaseImportLogDO.STATUS_SUCCESS);
        row.setSummary("新增 " + report.getCreateCount() + " / 更新 " + report.getUpdateCount()
                + " / 跳过 " + report.getSkipCount() + " / 发布 " + counts[0] + " / 下线 " + counts[1]);
        row.setReportJson(toJson(report));
        row.setBackupJson(toJson(backup));
        row.setAssetHashes(toJson(currentHashes(bundle)));
        return importLogRepository.save(row);
    }

    /**
     * 执行下线项：可发布资产撤销发布，页面置草稿，告警规则与开放平台停用。
     * <p>在导入同一事务内执行，失败时连同导入一起回滚。</p>
     */
    private int offlineAll(List<ReleasePackageFormat.ReleaseEntry> offline) {
        int count = 0;
        for (ReleasePackageFormat.ReleaseEntry e : offline) {
            if (!isOnlineHere(e)) {
                continue;
            }
            switch (e.getAssetType()) {
                case ReleaseAssetResolver.API -> flowApiCrudService.unpublish(e.getAssetId());
                case ReleaseAssetResolver.SERVICE -> flowServiceFlowService.unpublish(e.getAssetId());
                case ReleaseAssetResolver.TASK -> flowTaskService.unpublish(e.getAssetId());
                case ReleaseAssetResolver.MQ_TASK -> flowMqTaskService.unpublish(e.getAssetId());
                case ReleaseAssetResolver.PAGE -> pageInfoRepository.findById(e.getAssetId()).ifPresent(p -> {
                    p.setStatus(0);
                    pageInfoRepository.save(p);
                });
                case ReleaseAssetResolver.ALERT_RULE -> alertRuleRepository.findById(e.getAssetId()).ifPresent(r -> {
                    r.setEnabled(0);
                    alertRuleRepository.save(r);
                });
                case ReleaseAssetResolver.OPEN_PLATFORM -> flowOpenPlatformRepository.findByCode(e.getAssetKey()).ifPresent(p -> {
                    p.setStatus(0);
                    flowOpenPlatformRepository.save(p);
                    openPlatformCache.publishRefresh();
                });
                default -> {
                    continue;
                }
            }
            count++;
        }
        return count;
    }

    /** 导入后各资产（按 ID 匹配的类型）的内容指纹，供下次预检发现生产被直接修改 */
    private Map<String, String> currentHashes(AssetBundle bundle) {
        Map<String, String> hashes = new HashMap<>();
        Map<String, List<String>> idsByType = new HashMap<>();
        idsByType.put(ReleaseAssetResolver.API, nullSafe(bundle.getApis()).stream().map(FlowApiDO::getId).toList());
        idsByType.put(ReleaseAssetResolver.SERVICE, nullSafe(bundle.getServices()).stream().map(FlowServiceFlowDO::getId).toList());
        idsByType.put(ReleaseAssetResolver.TASK, nullSafe(bundle.getTasks()).stream().map(FlowTaskDO::getId).toList());
        idsByType.put(ReleaseAssetResolver.MQ_TASK, nullSafe(bundle.getMqTasks()).stream().map(FlowMqTaskDO::getId).toList());
        idsByType.put(ReleaseAssetResolver.RESPONSE_TEMPLATE,
                nullSafe(bundle.getResponseTemplates()).stream().map(t -> t.getId()).toList());
        idsByType.put(ReleaseAssetResolver.PAGE, nullSafe(bundle.getPages()).stream().map(p -> p.getId()).toList());
        idsByType.put(ReleaseAssetResolver.MODEL, nullSafe(bundle.getModels()).stream().map(m -> m.getId()).toList());
        idsByType.put(ReleaseAssetResolver.ALERT_RULE,
                nullSafe(bundle.getAlertRules()).stream().map(r -> r.getRule().getId()).toList());
        idsByType.forEach((type, ids) -> ids.forEach(id -> {
            ReleaseAssetResolver.AssetInfo info = assetResolver.describe(type, id);
            if (info != null && info.contentHash() != null) {
                hashes.put(type + ":" + id, info.contentHash());
            }
        }));
        return hashes;
    }

    @Override
    public boolean createPlaceholder(String kind, String key, Map<String, String> attributes, String remark) {
        assertWritable();
        boolean created = placeholderCreator.create(kind, key, attributes, remark);
        if (created) {
            auditLogService.record("RELEASE_PLACEHOLDER", kind, key, AuditDetail.of("kind", kind, "key", key));
        }
        return created;
    }

    @Override
    public List<ReleaseDiffCalculator.FieldDiff> diff(String digest, String assetType, String key) {
        ReleasePackageReader.Parsed pkg = digest == null ? null : inspected.getIfPresent(digest);
        if (pkg == null) {
            throw new FlowException("RELEASE_INSPECT_EXPIRED", "预检结果已过期，请重新上传发布包");
        }
        return diffCalculator.diff(assetType, key, pkg.bundle());
    }

    /** 导入只写草稿，这里逐个走正式发布（含门禁），任一失败整批回滚 */
    private int publishAll(AssetBundle bundle, String env) {
        int count = 0;
        for (FlowApiDO api : nullSafe(bundle.getApis())) {
            if (!HostCatalogReserved.isReservedId(api.getId())) {
                flowApiCrudService.publish(api.getId(), env);
                count++;
            }
        }
        for (FlowServiceFlowDO s : nullSafe(bundle.getServices())) {
            flowServiceFlowService.publish(s.getId(), env);
            count++;
        }
        for (FlowTaskDO t : nullSafe(bundle.getTasks())) {
            flowTaskService.publish(t.getId(), env);
            count++;
        }
        for (FlowMqTaskDO t : nullSafe(bundle.getMqTasks())) {
            flowMqTaskService.publish(t.getId());
            count++;
        }
        return count;
    }

    private void recordFailure(ReleasePackageReader.Parsed pkg, RuntimeException e) {
        try {
            TransactionTemplate requiresNew = new TransactionTemplate(transactionTemplate.getTransactionManager());
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(tx -> {
                FlowReleaseImportLogDO row = baseLog(pkg);
                row.setStatus(FlowReleaseImportLogDO.STATUS_FAILED);
                row.setSummary("导入失败，已整体回滚，本环境未发生变化");
                row.setErrorMessage(StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()), 1900));
                importLogRepository.save(row);
            });
            auditLogService.record("RELEASE_IMPORT_FAILED", "RELEASE_IMPORT", null,
                    AuditDetail.of("code", pkg.release().getCode(), "digest", pkg.digest()));
        } catch (Exception ex) {
            log.error("[ReleaseImport] 记录失败日志出错", ex);
        }
    }

    private FlowReleaseImportLogDO baseLog(ReleasePackageReader.Parsed pkg) {
        return FlowReleaseImportLogDO.builder()
                .releaseCode(pkg.release().getCode())
                .releaseName(pkg.release().getName())
                .packageDigest(pkg.digest())
                .sourceEnv(pkg.manifest().getSourceEnv())
                .targetEnv(releaseEnvironment.current())
                .importedBy(JwtTokenUtil.currentUsername())
                .importedTime(LocalDateTime.now(ZONE))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 记录 / 回滚
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public PageBean<ReleaseImportLogDTO> page(String keyword, String status, int page, int size) {
        String kw = StrUtil.trimToNull(keyword);
        Specification<FlowReleaseImportLogDO> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (kw != null) {
                ps.add(cb.like(cb.lower(root.get("releaseCode")), "%" + kw.toLowerCase() + "%"));
            }
            if (StrUtil.isNotBlank(status)) {
                ps.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<FlowReleaseImportLogDO> p = importLogRepository.findAll(spec,
                PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "importedTime")));
        List<ReleaseImportLogDTO> items = p.getContent().stream().map(this::toDTO).toList();
        return new PageBean<>(items, p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements());
    }

    @Override
    public ReleaseImportLogDTO get(String id) {
        FlowReleaseImportLogDO row = require(id);
        ReleaseImportLogDTO dto = toDTO(row);
        if (StrUtil.isNotBlank(row.getReportJson())) {
            try {
                dto.setReport(MAPPER.readValue(row.getReportJson(), TransferReportDTO.class));
            } catch (Exception e) {
                log.warn("[ReleaseImport] 解析导入报告失败: {}", e.getMessage());
            }
        }
        return dto;
    }

    @Override
    public ReleaseImportLogDTO rollback(String id) {
        assertWritable();
        String lockValue = acquireLock();
        try {
            FlowReleaseImportLogDO saved = transactionTemplate.execute(tx -> {
                FlowReleaseImportLogDO row = require(id);
                if (!isRollbackable(row)) {
                    throw new FlowException("RELEASE_ROLLBACK_DENIED", FlowReleaseImportLogDO.STATUS_SUCCESS.equals(row.getStatus())
                            ? "之后还有更新的导入，只能先回滚最近一次导入"
                            : "只有导入成功且未回滚的记录可以回滚");
                }
                ReleaseImportBackup backup;
                try {
                    backup = MAPPER.readValue(row.getBackupJson(), ReleaseImportBackup.class);
                } catch (Exception e) {
                    throw new FlowException("RELEASE_BACKUP_BROKEN", "导入前备份无法解析，不能自动回滚: " + e.getMessage());
                }
                AssetEditLock.runUnlocked(() -> {
                    backupManager.restore(backup, row.getReleaseCode());
                    return null;
                });
                row.setStatus(FlowReleaseImportLogDO.STATUS_ROLLED_BACK);
                row.setRolledBackBy(JwtTokenUtil.currentUsername());
                row.setRolledBackTime(LocalDateTime.now(ZONE));
                return importLogRepository.save(row);
            });
            auditLogService.record("RELEASE_ROLLBACK", "RELEASE_IMPORT", id,
                    AuditDetail.of("code", saved.getReleaseCode()));
            return toDTO(saved);
        } finally {
            releaseLock(lockValue);
        }
    }

    private boolean isRollbackable(FlowReleaseImportLogDO row) {
        return FlowReleaseImportLogDO.STATUS_SUCCESS.equals(row.getStatus())
                && StrUtil.isNotBlank(row.getBackupJson())
                && !importLogRepository.existsByStatusAndImportedTimeAfter(
                FlowReleaseImportLogDO.STATUS_SUCCESS, row.getImportedTime());
    }

    private ReleaseImportLogDTO toDTO(FlowReleaseImportLogDO row) {
        ReleaseImportLogDTO dto = ReleaseImportLogDTO.fromDO(row);
        dto.setRollbackable(isRollbackable(row));
        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具
    // ─────────────────────────────────────────────────────────────────────────

    /** 导入与回滚互斥：两者都会整批改写资产，并发执行会让备份与现状对不上 */
    private String acquireLock() {
        if (!localRunning.compareAndSet(false, true)) {
            throw new FlowException("RELEASE_IMPORT_BUSY", "已有导入或回滚正在进行，请稍后再试");
        }
        String value = UUID.randomUUID().toString();
        try {
            if (!FlowRedisUtil.setIfAbsent(LOCK_KEY, value, LOCK_TTL_MINUTES, TimeUnit.MINUTES)) {
                localRunning.set(false);
                throw new FlowException("RELEASE_IMPORT_BUSY", "其它节点正在导入或回滚，请稍后再试");
            }
            return value;
        } catch (IllegalStateException redisDown) {
            log.warn("[ReleaseImport] Redis 不可用，仅按本节点互斥: {}", redisDown.getMessage());
            return null;
        }
    }

    private void releaseLock(String value) {
        try {
            if (value != null) {
                FlowRedisUtil.unlock(LOCK_KEY, value);
            }
        } finally {
            localRunning.set(false);
        }
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new FlowException("RELEASE_IMPORT_FAILED", "序列化导入记录失败: " + e.getMessage());
        }
    }

    private FlowReleaseImportLogDO require(String id) {
        return importLogRepository.findById(id)
                .orElseThrow(() -> new FlowException("RELEASE_IMPORT_NOT_FOUND", "导入记录不存在: " + id));
    }

    private void assertWritable() {
        if (demoModeGuard.isDemoMode()) {
            throw new FlowException("DEMO_RESTRICTED", "演示模式下不允许导入或回滚发布包");
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
