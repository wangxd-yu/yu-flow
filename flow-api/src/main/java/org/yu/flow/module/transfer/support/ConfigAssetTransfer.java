package org.yu.flow.module.transfer.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.module.alert.domain.AlertChannelDO;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.repository.AlertChannelRepository;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.open.cache.OpenPlatformCache;
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
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.AssetExportRequestDTO;
import org.yu.flow.module.transfer.dto.BundleAlertRule;
import org.yu.flow.module.transfer.dto.BundleOpenPlatform;
import org.yu.flow.module.transfer.dto.TransferItemDTO;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 页面 / 数据模型 / 全局宏 / 系统配置 / 开放平台 / 告警规则的导出与导入。
 *
 * <p>这些资产没有草稿与发布之分，导入即生效。匹配键：页面、模型、告警规则按 ID；
 * 全局宏按 macroCode；系统配置按 configKey；开放平台按 code（各环境 ID 不同）。</p>
 */
@Component
public class ConfigAssetTransfer {

    public static final String PAGE = "PAGE";
    public static final String MODEL = "MODEL";
    public static final String SYS_MACRO = "SYS_MACRO";
    public static final String SYS_CONFIG = "SYS_CONFIG";
    public static final String OPEN_PLATFORM = "OPEN_PLATFORM";
    public static final String ALERT_RULE = "ALERT_RULE";

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
    private AlertChannelRepository alertChannelRepository;
    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private SysMacroCacheManager sysMacroCacheManager;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;
    @Resource
    private OpenPlatformCache openPlatformCache;

    // ─────────────────────────────────────────────────────────────────────────
    // 导出
    // ─────────────────────────────────────────────────────────────────────────

    public boolean hasSelection(AssetExportRequestDTO r) {
        return notEmpty(r.getPageIds()) || notEmpty(r.getModelIds()) || notEmpty(r.getSysMacroIds())
                || notEmpty(r.getSysConfigIds()) || notEmpty(r.getOpenPlatformIds()) || notEmpty(r.getAlertRuleIds());
    }

    public void export(AssetExportRequestDTO request, AssetBundle bundle, List<String> warnings) {
        for (String id : distinct(request.getPageIds())) {
            pageInfoRepository.findById(id).ifPresentOrElse(p -> {
                PageInfoDO copy = BundleEntityCopier.detachedCopy(p, PageInfoDO.class);
                copy.setCreateTime(null);
                copy.setUpdateTime(null);
                bundle.getPages().add(copy);
            }, () -> warnings.add("页面不存在或已删除，已跳过: " + id));
        }
        for (String id : distinct(request.getModelIds())) {
            flowModelInfoRepository.findById(id).ifPresentOrElse(m -> {
                FlowModelInfoDO copy = BundleEntityCopier.detachedCopy(m, FlowModelInfoDO.class);
                copy.setCreateTime(null);
                copy.setUpdateTime(null);
                bundle.getModels().add(copy);
            }, () -> warnings.add("数据模型不存在或已删除，已跳过: " + id));
        }
        for (String id : distinct(request.getSysMacroIds())) {
            sysMacroRepository.findById(id).ifPresentOrElse(m -> {
                SysMacroDO copy = BundleEntityCopier.detachedCopy(m, SysMacroDO.class);
                copy.setCreateTime(null);
                copy.setUpdateTime(null);
                bundle.getSysMacros().add(copy);
            }, () -> warnings.add("全局宏不存在或已删除，已跳过: " + id));
        }
        for (String id : distinct(request.getSysConfigIds())) {
            SysConfigDO cfg = sysConfigRepository.findById(id).orElse(null);
            String rejected = cfg == null ? null : ConfigTransferPolicy.rejectReason(cfg.getConfigKey(), cfg.getConfigGroup());
            if (cfg == null) {
                warnings.add("系统配置不存在或已删除，已跳过: " + id);
            } else if (rejected != null) {
                warnings.add("系统配置「" + cfg.getConfigKey() + "」未导出：" + rejected);
            } else {
                SysConfigDO copy = BundleEntityCopier.detachedCopy(cfg, SysConfigDO.class);
                copy.setCreateTime(null);
                copy.setUpdateTime(null);
                bundle.getSysConfigs().add(copy);
            }
        }
        for (String id : distinct(request.getOpenPlatformIds())) {
            FlowOpenPlatformDO platform = flowOpenPlatformRepository.findById(id).orElse(null);
            if (platform == null) {
                warnings.add("开放平台不存在或已删除，已跳过: " + id);
                continue;
            }
            BundleOpenPlatform item = new BundleOpenPlatform();
            FlowOpenPlatformDO copy = BundleEntityCopier.detachedCopy(platform, FlowOpenPlatformDO.class);
            copy.setCreateTime(null);
            copy.setUpdateTime(null);
            item.setPlatform(copy);
            for (FlowOpenApiGrantDO g : flowOpenApiGrantRepository.findByPlatformId(platform.getId())) {
                item.getGrants().add(new BundleOpenPlatform.Grant(g.getApiId(), g.getAllowMethods()));
            }
            bundle.getOpenPlatforms().add(item);
        }
        for (String id : distinct(request.getAlertRuleIds())) {
            AlertRuleDO rule = alertRuleRepository.findById(id).orElse(null);
            if (rule == null) {
                warnings.add("告警规则不存在或已删除，已跳过: " + id);
                continue;
            }
            BundleAlertRule item = new BundleAlertRule();
            AlertRuleDO copy = BundleEntityCopier.detachedCopy(rule, AlertRuleDO.class);
            copy.setCreateTime(null);
            copy.setUpdateTime(null);
            item.setRule(copy);
            for (AlertChannelDO ch : alertChannelRepository.findAllById(splitIds(rule.getChannelIds()))) {
                item.getChannelNames().add(ch.getName());
            }
            bundle.getAlertRules().add(item);
        }
    }

    /** 页面、模型所在目录，随包导出 */
    public Set<String> directoryIds(AssetBundle bundle) {
        Set<String> ids = new LinkedHashSet<>();
        nullSafe(bundle.getPages()).forEach(p -> addIfNotBlank(ids, p.getDirectoryId()));
        nullSafe(bundle.getModels()).forEach(m -> addIfNotBlank(ids, m.getDirectoryId()));
        return ids;
    }

    public void collectRequirements(AssetBundle bundle, Map<String, TransferRequirementDTO> acc) {
        for (FlowModelInfoDO m : nullSafe(bundle.getModels())) {
            BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_DATASOURCE, m.getDatasource(), m.getName());
        }
        Map<String, AlertChannelDO> channelsByName = channelsByName();
        for (BundleAlertRule r : nullSafe(bundle.getAlertRules())) {
            String ruleName = r.getRule() == null ? "" : r.getRule().getName();
            for (String channel : r.getChannelNames()) {
                BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_ALERT_CHANNEL, channel, ruleName);
                TransferRequirementDTO req = acc.get(TransferRequirementDTO.KIND_ALERT_CHANNEL + ":" + channel);
                AlertChannelDO source = channelsByName.get(channel);
                if (req != null && source != null) {
                    req.getAttributes().put("type", source.getType());
                }
            }
        }
    }

    public boolean alertChannelReady(String name) {
        AlertChannelDO ch = channelsByName().get(name);
        return ch != null && Integer.valueOf(1).equals(ch.getEnabled());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 导入
    // ─────────────────────────────────────────────────────────────────────────

    public boolean hasAny(AssetBundle b) {
        return !nullSafe(b.getPages()).isEmpty() || !nullSafe(b.getModels()).isEmpty()
                || !nullSafe(b.getSysMacros()).isEmpty() || !nullSafe(b.getSysConfigs()).isEmpty()
                || !nullSafe(b.getOpenPlatforms()).isEmpty() || !nullSafe(b.getAlertRules()).isEmpty();
    }

    public void analyze(AssetBundle bundle, TransferReportDTO report, boolean overwrite, boolean dryRun,
                        LocalDateTime now, Function<String, String> resolveDirectoryId) {
        for (PageInfoDO p : nullSafe(bundle.getPages())) {
            importPage(p, report, overwrite, dryRun, now, resolveDirectoryId);
        }
        for (FlowModelInfoDO m : nullSafe(bundle.getModels())) {
            importModel(m, report, overwrite, dryRun, now, resolveDirectoryId);
        }
        if (!nullSafe(bundle.getSysMacros()).isEmpty()) {
            report.getWarnings().add("发布包含全局宏（SpEL 表达式，可调用系统 Bean），请确认发布包来源可信");
        }
        for (SysMacroDO m : nullSafe(bundle.getSysMacros())) {
            importMacro(m, report, overwrite, dryRun, now);
        }
        for (SysConfigDO c : nullSafe(bundle.getSysConfigs())) {
            importConfig(c, report, overwrite, dryRun, now);
        }
        Set<String> bundledApiIds = new LinkedHashSet<>();
        nullSafe(bundle.getApis()).forEach(a -> addIfNotBlank(bundledApiIds, a.getId()));
        for (BundleOpenPlatform p : nullSafe(bundle.getOpenPlatforms())) {
            importOpenPlatform(p, bundledApiIds, report, overwrite, dryRun, now);
        }
        Map<String, AlertChannelDO> channels = channelsByName();
        for (BundleAlertRule r : nullSafe(bundle.getAlertRules())) {
            importAlertRule(r, channels, report, overwrite, dryRun, now);
        }
    }

    /** 导入后刷新相关缓存（均为事务提交后执行） */
    public void afterImport(AssetBundle bundle) {
        if (!nullSafe(bundle.getSysMacros()).isEmpty()) {
            sysMacroCacheManager.publishRefreshEvent();
        }
        if (!nullSafe(bundle.getSysConfigs()).isEmpty()) {
            sysConfigCacheManager.publishRefreshEvent();
        }
        if (!nullSafe(bundle.getOpenPlatforms()).isEmpty()) {
            openPlatformCache.publishRefresh();
        }
    }

    private void importPage(PageInfoDO in, TransferReportDTO report, boolean overwrite, boolean dryRun,
                            LocalDateTime now, Function<String, String> resolveDirectoryId) {
        String id = in.getId();
        if (StrUtil.isBlank(id) || StrUtil.isBlank(in.getRoutePath())) {
            report.getItems().add(TransferItemDTO.of(PAGE, id, in.getName(), TransferItemDTO.ACTION_CONFLICT, "缺少 ID 或访问路径"));
            return;
        }
        if (pageInfoRepository.existsByRoutePathAndIdNot(in.getRoutePath(), id)) {
            report.getItems().add(TransferItemDTO.of(PAGE, id, in.getName(), TransferItemDTO.ACTION_CONFLICT,
                    "目标环境已有其它页面使用路径 " + in.getRoutePath()));
            return;
        }
        PageInfoDO existing = pageInfoRepository.findById(id).orElse(null);
        if (!decide(PAGE, id, in.getName(), existing != null, overwrite, report, "导入即生效")) {
            return;
        }
        if (dryRun) {
            return;
        }
        PageInfoDO target = existing != null ? existing : PageInfoDO.builder().id(id).createTime(now).build();
        target.setDirectoryId(resolveDirectoryId.apply(in.getDirectoryId()));
        target.setName(in.getName());
        target.setRoutePath(in.getRoutePath());
        target.setJson(in.getJson());
        target.setStatus(in.getStatus());
        target.setUpdateTime(now);
        pageInfoRepository.save(target);
    }

    private void importModel(FlowModelInfoDO in, TransferReportDTO report, boolean overwrite, boolean dryRun,
                             LocalDateTime now, Function<String, String> resolveDirectoryId) {
        String id = in.getId();
        if (StrUtil.isBlank(id) || StrUtil.isBlank(in.getTableName())) {
            report.getItems().add(TransferItemDTO.of(MODEL, id, in.getName(), TransferItemDTO.ACTION_CONFLICT, "缺少 ID 或表名"));
            return;
        }
        if (flowModelInfoRepository.existsByTableNameAndIdNot(in.getTableName(), id)) {
            report.getItems().add(TransferItemDTO.of(MODEL, id, in.getName(), TransferItemDTO.ACTION_CONFLICT,
                    "目标环境已有其它模型绑定表 " + in.getTableName()));
            return;
        }
        FlowModelInfoDO existing = flowModelInfoRepository.findById(id).orElse(null);
        if (!decide(MODEL, id, in.getName(), existing != null, overwrite, report, "只更新模型元数据，业务表结构需 DBA 同步")) {
            return;
        }
        if (dryRun) {
            return;
        }
        FlowModelInfoDO target = existing != null ? existing : FlowModelInfoDO.builder().id(id).createTime(now).build();
        target.setDirectoryId(resolveDirectoryId.apply(in.getDirectoryId()));
        target.setName(in.getName());
        target.setTableName(in.getTableName());
        target.setDatasource(in.getDatasource());
        target.setFieldsSchema(in.getFieldsSchema());
        target.setStatus(in.getStatus());
        target.setUpdateTime(now);
        flowModelInfoRepository.save(target);
    }

    private void importMacro(SysMacroDO in, TransferReportDTO report, boolean overwrite, boolean dryRun, LocalDateTime now) {
        String code = in.getMacroCode();
        if (StrUtil.isBlank(code) || StrUtil.isBlank(in.getExpression())) {
            report.getItems().add(TransferItemDTO.of(SYS_MACRO, code, in.getMacroName(), TransferItemDTO.ACTION_CONFLICT, "缺少宏编码或表达式"));
            return;
        }
        SysMacroDO existing = sysMacroRepository.findByMacroCode(code).orElse(null);
        if (!decide(SYS_MACRO, code, in.getMacroName(), existing != null, overwrite, report, "导入即生效")) {
            return;
        }
        if (dryRun) {
            return;
        }
        SysMacroDO target = existing != null ? existing : SysMacroDO.builder().macroCode(code).createTime(now).build();
        target.setMacroName(in.getMacroName());
        target.setMacroType(in.getMacroType());
        target.setExpression(in.getExpression());
        target.setScope(in.getScope());
        target.setReturnType(in.getReturnType());
        target.setMacroParams(in.getMacroParams());
        target.setStatus(in.getStatus());
        target.setRemark(in.getRemark());
        target.setUpdateTime(now);
        sysMacroRepository.save(target);
    }

    private void importConfig(SysConfigDO in, TransferReportDTO report, boolean overwrite, boolean dryRun, LocalDateTime now) {
        String key = in.getConfigKey();
        if (StrUtil.isBlank(key)) {
            report.getItems().add(TransferItemDTO.of(SYS_CONFIG, null, null, TransferItemDTO.ACTION_CONFLICT, "缺少配置键"));
            return;
        }
        SysConfigDO existing = sysConfigRepository.findByConfigKey(key).orElse(null);
        String rejected = ConfigTransferPolicy.rejectReason(key, in.getConfigGroup(),
                existing == null ? null : existing.getConfigGroup());
        if (rejected != null) {
            report.getItems().add(TransferItemDTO.of(SYS_CONFIG, key, key, TransferItemDTO.ACTION_SKIP, rejected));
            return;
        }
        if (!decide(SYS_CONFIG, key, key, existing != null, overwrite, report, "导入即生效")) {
            return;
        }
        if (dryRun) {
            return;
        }
        SysConfigDO target = existing != null ? existing
                : SysConfigDO.builder().configKey(key).isBuiltin(in.getIsBuiltin() == null ? 0 : in.getIsBuiltin())
                .createTime(now).build();
        target.setConfigValue(in.getConfigValue());
        target.setValueType(StrUtil.blankToDefault(in.getValueType(), "STRING"));
        target.setConfigGroup(StrUtil.blankToDefault(in.getConfigGroup(), "GENERAL"));
        target.setRemark(in.getRemark());
        target.setStatus(in.getStatus() == null ? 1 : in.getStatus());
        target.setSortOrder(in.getSortOrder() == null ? 100 : in.getSortOrder());
        target.setUpdateTime(now);
        sysConfigRepository.save(target);
    }

    private void importOpenPlatform(BundleOpenPlatform item, Set<String> bundledApiIds, TransferReportDTO report,
                                    boolean overwrite, boolean dryRun, LocalDateTime now) {
        FlowOpenPlatformDO in = item.getPlatform();
        String code = in == null ? null : in.getCode();
        if (StrUtil.isBlank(code) || StrUtil.isBlank(in.getName())) {
            report.getItems().add(TransferItemDTO.of(OPEN_PLATFORM, code, in == null ? null : in.getName(),
                    TransferItemDTO.ACTION_CONFLICT, "缺少平台编码或名称"));
            return;
        }
        List<BundleOpenPlatform.Grant> usable = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (BundleOpenPlatform.Grant g : item.getGrants()) {
            if (bundledApiIds.contains(g.getApiId()) || flowApiRepository.existsById(g.getApiId())) {
                usable.add(g);
            } else {
                dropped.add(g.getApiId());
            }
        }
        FlowOpenPlatformDO existing = flowOpenPlatformRepository.findByCode(code).orElse(null);
        String note = "授权 " + usable.size() + " 个接口；AppKey 由本环境签发"
                + (dropped.isEmpty() ? "" : "；" + dropped.size() + " 个授权接口在本环境不存在，已忽略");
        if (!decide(OPEN_PLATFORM, code, in.getName(), existing != null, overwrite, report, note)) {
            return;
        }
        if (dryRun) {
            return;
        }
        FlowOpenPlatformDO target = existing;
        if (target == null) {
            target = FlowOpenPlatformDO.builder().code(code).status(in.getStatus() == null ? 1 : in.getStatus())
                    .ipAllowlist(in.getIpAllowlist()).expireAt(in.getExpireAt()).createTime(now).build();
        }
        target.setName(in.getName());
        target.setContact(in.getContact());
        target.setRemark(in.getRemark());
        target.setOpenCallLogEnabled(in.getOpenCallLogEnabled());
        target.setRateLimitQps(in.getRateLimitQps());
        target.setUpdateTime(now);
        FlowOpenPlatformDO saved = flowOpenPlatformRepository.save(target);
        flowOpenApiGrantRepository.deleteByPlatformId(saved.getId());
        for (BundleOpenPlatform.Grant g : usable) {
            flowOpenApiGrantRepository.save(FlowOpenApiGrantDO.builder()
                    .platformId(saved.getId()).apiId(g.getApiId()).allowMethods(g.getAllowMethods()).createTime(now).build());
        }
    }

    private void importAlertRule(BundleAlertRule item, Map<String, AlertChannelDO> channels, TransferReportDTO report,
                                 boolean overwrite, boolean dryRun, LocalDateTime now) {
        AlertRuleDO in = item.getRule();
        String id = in == null ? null : in.getId();
        if (StrUtil.isBlank(id)) {
            report.getItems().add(TransferItemDTO.of(ALERT_RULE, null, in == null ? null : in.getName(),
                    TransferItemDTO.ACTION_CONFLICT, "缺少 ID，包体不完整"));
            return;
        }
        List<String> channelIds = new ArrayList<>();
        for (String name : item.getChannelNames()) {
            AlertChannelDO ch = channels.get(name);
            if (ch != null) {
                channelIds.add(ch.getId());
            }
        }
        AlertRuleDO existing = alertRuleRepository.findById(id).orElse(null);
        if (!decide(ALERT_RULE, id, in.getName(), existing != null, overwrite, report,
                "通道按名称对应：" + channelIds.size() + "/" + item.getChannelNames().size())) {
            return;
        }
        if (dryRun) {
            return;
        }
        AlertRuleDO target = existing != null ? existing
                : AlertRuleDO.builder().id(id).enabled(in.getEnabled() == null ? 1 : in.getEnabled()).createTime(now).build();
        target.setName(in.getName());
        target.setScopeAssetTypes(in.getScopeAssetTypes());
        target.setWindow(in.getWindow());
        target.setMinHealth(in.getMinHealth());
        target.setTopN(in.getTopN());
        target.setChannelIds(String.join(",", channelIds));
        target.setIntervalMinutes(in.getIntervalMinutes());
        target.setDedupMinutes(in.getDedupMinutes());
        target.setUpdateTime(now);
        alertRuleRepository.save(target);
    }

    /** 统一的新增 / 更新 / 跳过判定，返回是否需要写入 */
    private static boolean decide(String type, String key, String name, boolean exists, boolean overwrite,
                                  TransferReportDTO report, String updateNote) {
        if (exists && !overwrite) {
            report.getItems().add(TransferItemDTO.of(type, key, name, TransferItemDTO.ACTION_SKIP, "目标环境已存在，未开启覆盖"));
            return false;
        }
        report.getItems().add(TransferItemDTO.of(type, key, name,
                exists ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE, updateNote));
        return true;
    }

    private Map<String, AlertChannelDO> channelsByName() {
        Map<String, AlertChannelDO> map = new LinkedHashMap<>();
        for (AlertChannelDO ch : alertChannelRepository.findAll()) {
            map.putIfAbsent(ch.getName(), ch);
        }
        return map;
    }

    private static List<String> splitIds(String csv) {
        if (StrUtil.isBlank(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(StrUtil::isNotBlank).toList();
    }

    private static List<String> distinct(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(StrUtil::isNotBlank).distinct().toList();
    }

    private static boolean notEmpty(List<String> ids) {
        return ids != null && !ids.isEmpty();
    }

    private static void addIfNotBlank(Set<String> target, String value) {
        if (StrUtil.isNotBlank(value)) {
            target.add(value);
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
