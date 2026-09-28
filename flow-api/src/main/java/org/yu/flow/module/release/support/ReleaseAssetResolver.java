package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.page.domain.PageInfoDO;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;
import org.yu.flow.module.transfer.support.ConfigAssetTransfer;
import org.yu.flow.module.transfer.support.ConfigTransferPolicy;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.assetref.FlowDslReferenceScanner;
import org.yu.flow.module.assetversion.UnpublishedChangeDetector;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.util.ContentHashUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 版本单可纳入的资产：查询、发布状态、内容指纹、直接依赖。
 */
@Component
public class ReleaseAssetResolver {

    public static final String API = "API";
    public static final String SERVICE = "SERVICE";
    public static final String TASK = "TASK";
    public static final String MQ_TASK = "MQ_TASK";
    public static final String RESPONSE_TEMPLATE = "RESPONSE_TEMPLATE";
    public static final String PAGE = ConfigAssetTransfer.PAGE;
    public static final String MODEL = ConfigAssetTransfer.MODEL;
    public static final String SYS_MACRO = ConfigAssetTransfer.SYS_MACRO;
    public static final String SYS_CONFIG = ConfigAssetTransfer.SYS_CONFIG;
    public static final String OPEN_PLATFORM = ConfigAssetTransfer.OPEN_PLATFORM;
    public static final String ALERT_RULE = ConfigAssetTransfer.ALERT_RULE;

    public static final Set<String> TYPES = Set.of(API, SERVICE, TASK, MQ_TASK, RESPONSE_TEMPLATE,
            PAGE, MODEL, SYS_MACRO, SYS_CONFIG, OPEN_PLATFORM, ALERT_RULE);

    /** 展示与扫描顺序 */
    public static final List<String> ORDERED_TYPES = List.of(API, SERVICE, TASK, MQ_TASK, RESPONSE_TEMPLATE,
            PAGE, MODEL, SYS_MACRO, SYS_CONFIG, OPEN_PLATFORM, ALERT_RULE);

    /** 可以放进下线清单的类型（其余类型没有「在线」概念） */
    public static final Set<String> OFFLINE_TYPES = Set.of(API, SERVICE, TASK, MQ_TASK, PAGE, OPEN_PLATFORM, ALERT_RULE);

    /** 在目标环境按编码而非 ID 匹配的类型 */
    public static final Set<String> KEYED_TYPES = Set.of(SYS_MACRO, SYS_CONFIG, OPEN_PLATFORM);

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(API, "接口"),
            Map.entry(SERVICE, "内部服务"),
            Map.entry(TASK, "定时任务"),
            Map.entry(MQ_TASK, "MQ 任务"),
            Map.entry(RESPONSE_TEMPLATE, "响应模板"),
            Map.entry(PAGE, "页面"),
            Map.entry(MODEL, "数据模型"),
            Map.entry(SYS_MACRO, "全局宏"),
            Map.entry(SYS_CONFIG, "系统配置"),
            Map.entry(OPEN_PLATFORM, "开放平台"),
            Map.entry(ALERT_RULE, "告警规则"));

    private static final int SEARCH_LIMIT = 50;

    public record AssetRef(String type, String id) {
    }

    /**
     * @param publishable  是否有发布概念（响应模板没有）
     * @param contentHash  将被导出的内容的指纹：可发布资产取线上快照，未发布为 null
     */
    public record AssetInfo(String type, String id, String name, String detail, boolean publishable,
                            boolean published, boolean unpublishedChanges, String contentHash,
                            List<AssetRef> dependencies) {
    }

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

    public static String normalizeType(String type) {
        String t = StrUtil.trimToEmpty(type).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new IllegalArgumentException("不支持的资产类型: " + type);
        }
        return t;
    }

    /** 资产不存在（含已删除）返回 null */
    public AssetInfo describe(String type, String id) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        return switch (normalizeType(type)) {
            case API -> flowApiRepository.findById(id)
                    .filter(a -> !HostCatalogReserved.isReservedId(a.getId()))
                    .map(this::ofApi).orElse(null);
            case SERVICE -> flowServiceFlowRepository.findById(id).map(this::ofService).orElse(null);
            case TASK -> flowTaskRepository.findById(id).map(this::ofTask).orElse(null);
            case MQ_TASK -> flowMqTaskRepository.findById(id).map(this::ofMqTask).orElse(null);
            case RESPONSE_TEMPLATE -> responseTemplateRepository.findById(id).map(this::ofTemplate).orElse(null);
            case PAGE -> pageInfoRepository.findById(id).map(this::ofPage).orElse(null);
            case MODEL -> flowModelInfoRepository.findById(id).map(this::ofModel).orElse(null);
            case SYS_MACRO -> sysMacroRepository.findById(id).map(this::ofMacro).orElse(null);
            case SYS_CONFIG -> sysConfigRepository.findById(id).map(this::ofConfig).orElse(null);
            case OPEN_PLATFORM -> flowOpenPlatformRepository.findById(id).map(this::ofOpenPlatform).orElse(null);
            case ALERT_RULE -> alertRuleRepository.findById(id).map(this::ofAlertRule).orElse(null);
            default -> null;
        };
    }

    /** 按编码匹配的类型返回编码（宏编码 / 配置键 / 平台编码），其余返回 null */
    public String keyOf(String type, String id) {
        return switch (normalizeType(type)) {
            case SYS_MACRO -> sysMacroRepository.findById(id).map(SysMacroDO::getMacroCode).orElse(null);
            case SYS_CONFIG -> sysConfigRepository.findById(id).map(SysConfigDO::getConfigKey).orElse(null);
            case OPEN_PLATFORM -> flowOpenPlatformRepository.findById(id).map(FlowOpenPlatformDO::getCode).orElse(null);
            default -> null;
        };
    }

    /** 资产当前是否「在线」：已发布 / 页面已发布 / 规则或平台已启用 */
    public boolean isOnline(String type, String id) {
        return switch (normalizeType(type)) {
            case API, SERVICE, TASK, MQ_TASK -> {
                AssetInfo info = describe(type, id);
                yield info != null && info.published();
            }
            case PAGE -> pageInfoRepository.findById(id).map(p -> Integer.valueOf(1).equals(p.getStatus())).orElse(false);
            case OPEN_PLATFORM -> flowOpenPlatformRepository.findById(id)
                    .map(p -> Integer.valueOf(1).equals(p.getStatus())).orElse(false);
            case ALERT_RULE -> alertRuleRepository.findById(id).map(r -> Integer.valueOf(1).equals(r.getEnabled())).orElse(false);
            default -> false;
        };
    }

    /**
     * 自某时间点以来有变化的资产：可发布资产看发布时间，其余看更新时间。
     * 用于版本单「扫描变更」。
     */
    public List<AssetInfo> changedSince(String type, LocalDateTime since, int limit) {
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updateTime"));
        return switch (normalizeType(type)) {
            case API -> flowApiRepository.findAll(this.<FlowApiDO>publishedAfter(since), page).stream()
                    .filter(a -> !HostCatalogReserved.isReservedId(a.getId())).map(this::ofApi).toList();
            case SERVICE -> flowServiceFlowRepository.findAll(this.<FlowServiceFlowDO>publishedAfter(since), page)
                    .stream().map(this::ofService).toList();
            case TASK -> flowTaskRepository.findAll(this.<FlowTaskDO>publishedAfter(since), page)
                    .stream().map(this::ofTask).toList();
            case MQ_TASK -> flowMqTaskRepository.findAll(this.<FlowMqTaskDO>publishedAfter(since), page)
                    .stream().map(this::ofMqTask).toList();
            case RESPONSE_TEMPLATE -> responseTemplateRepository.findAll(this.<ResponseTemplateDO>updatedAfter(since), page)
                    .stream().map(this::ofTemplate).toList();
            case PAGE -> pageInfoRepository.findAll(this.<PageInfoDO>updatedAfter(since), page)
                    .stream().map(this::ofPage).toList();
            case MODEL -> flowModelInfoRepository.findAll(this.<FlowModelInfoDO>updatedAfter(since), page)
                    .stream().map(this::ofModel).toList();
            case SYS_MACRO -> sysMacroRepository.findAll(this.<SysMacroDO>updatedAfter(since), page)
                    .stream().map(this::ofMacro).toList();
            case SYS_CONFIG -> sysConfigRepository.findAll(this.<SysConfigDO>updatedAfter(since), page)
                    .stream().map(this::ofConfig).toList();
            case OPEN_PLATFORM -> flowOpenPlatformRepository.findAll(this.<FlowOpenPlatformDO>updatedAfter(since), page)
                    .stream().map(this::ofOpenPlatform).toList();
            case ALERT_RULE -> alertRuleRepository.findAll(this.<AlertRuleDO>updatedAfter(since), page)
                    .stream().map(this::ofAlertRule).toList();
            default -> List.of();
        };
    }

    /** 可发布资产的最近发布时间；未发布或不适用返回 null */
    public LocalDateTime publishTime(String type, String id) {
        return switch (normalizeType(type)) {
            case API -> flowApiRepository.findById(id).map(FlowApiDO::getPublishTime).orElse(null);
            case SERVICE -> flowServiceFlowRepository.findById(id).map(FlowServiceFlowDO::getPublishTime).orElse(null);
            case TASK -> flowTaskRepository.findById(id).map(FlowTaskDO::getPublishTime).orElse(null);
            case MQ_TASK -> flowMqTaskRepository.findById(id).map(FlowMqTaskDO::getPublishTime).orElse(null);
            default -> null;
        };
    }

    public List<AssetInfo> search(String type, String keyword) {
        String kw = StrUtil.trimToNull(keyword);
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT, Sort.by(Sort.Direction.DESC, "updateTime"));
        return switch (normalizeType(type)) {
            case API -> flowApiRepository.findAll(likeAny(kw, "name", "url"), page).stream()
                    .filter(a -> !HostCatalogReserved.isReservedId(a.getId()))
                    .map(this::ofApi).toList();
            case SERVICE -> flowServiceFlowRepository.findAll(this.<FlowServiceFlowDO>likeAny(kw, "name"), page)
                    .stream().map(this::ofService).toList();
            case TASK -> flowTaskRepository.findAll(this.<FlowTaskDO>likeAny(kw, "name"), page)
                    .stream().map(this::ofTask).toList();
            case MQ_TASK -> flowMqTaskRepository.findAll(this.<FlowMqTaskDO>likeAny(kw, "name", "topic"), page)
                    .stream().map(this::ofMqTask).toList();
            case RESPONSE_TEMPLATE -> responseTemplateRepository
                    .findAll(this.<ResponseTemplateDO>likeAny(kw, "templateName"), page)
                    .stream().map(this::ofTemplate).toList();
            case PAGE -> pageInfoRepository.findAll(this.<PageInfoDO>likeAny(kw, "name", "routePath"), page)
                    .stream().map(this::ofPage).toList();
            case MODEL -> flowModelInfoRepository.findAll(this.<FlowModelInfoDO>likeAny(kw, "name", "tableName"), page)
                    .stream().map(this::ofModel).toList();
            case SYS_MACRO -> sysMacroRepository.findAll(this.<SysMacroDO>likeAny(kw, "macroCode", "macroName"), page)
                    .stream().map(this::ofMacro).toList();
            case SYS_CONFIG -> sysConfigRepository.findAll(this.<SysConfigDO>likeAny(kw, "configKey", "remark"), page)
                    .stream().map(this::ofConfig).toList();
            case OPEN_PLATFORM -> flowOpenPlatformRepository.findAll(this.<FlowOpenPlatformDO>likeAny(kw, "name", "code"), page)
                    .stream().map(this::ofOpenPlatform).toList();
            case ALERT_RULE -> alertRuleRepository.findAll(this.<AlertRuleDO>likeAny(kw, "name"), page)
                    .stream().map(this::ofAlertRule).toList();
            default -> List.of();
        };
    }

    private <T> Specification<T> publishedAfter(LocalDateTime since) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("publishStatus"), 1),
                cb.greaterThan(root.get("publishTime"), since));
    }

    private <T> Specification<T> updatedAfter(LocalDateTime since) {
        return (root, query, cb) -> cb.greaterThan(root.get("updateTime"), since);
    }

    private <T> Specification<T> likeAny(String keyword, String... fields) {
        return (root, query, cb) -> {
            if (keyword == null) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            List<Predicate> ors = new ArrayList<>();
            for (String f : fields) {
                ors.add(cb.like(cb.lower(root.get(f)), pattern));
            }
            return cb.or(ors.toArray(new Predicate[0]));
        };
    }

    private AssetInfo ofApi(FlowApiDO a) {
        boolean published = isPublished(a.getPublishStatus(), a.getPublishedSnapshot());
        List<AssetRef> deps = dslDeps(published ? a.getPublishedSnapshot() : a.getDslContent());
        if (StrUtil.isNotBlank(a.getTemplateId())) {
            deps.add(new AssetRef(RESPONSE_TEMPLATE, a.getTemplateId()));
        }
        String detail = StrUtil.nullToEmpty(a.getMethod()).toUpperCase(Locale.ROOT) + " " + StrUtil.nullToEmpty(a.getUrl());
        return new AssetInfo(API, a.getId(), a.getName(), detail.trim(), true, published,
                UnpublishedChangeDetector.apiHasUnpublishedChanges(a), hashIf(published, a.getPublishedSnapshot()), deps);
    }

    private AssetInfo ofService(FlowServiceFlowDO s) {
        boolean published = isPublished(s.getPublishStatus(), s.getPublishedSnapshot());
        return new AssetInfo(SERVICE, s.getId(), s.getName(), null, true, published,
                UnpublishedChangeDetector.serviceHasUnpublishedChanges(s), hashIf(published, s.getPublishedSnapshot()),
                dslDeps(published ? s.getPublishedSnapshot() : s.getDslContent()));
    }

    private AssetInfo ofTask(FlowTaskDO t) {
        boolean published = isPublished(t.getPublishStatus(), t.getPublishedSnapshot());
        return new AssetInfo(TASK, t.getId(), t.getName(), t.getCron(), true, published,
                UnpublishedChangeDetector.taskHasUnpublishedChanges(t), hashIf(published, t.getPublishedSnapshot()),
                dslDeps(published ? t.getPublishedSnapshot() : t.getDslContent()));
    }

    private AssetInfo ofMqTask(FlowMqTaskDO t) {
        boolean published = isPublished(t.getPublishStatus(), t.getPublishedSnapshot());
        return new AssetInfo(MQ_TASK, t.getId(), t.getName(), t.getConnectionCode() + " / " + t.getTopic(), true,
                published, UnpublishedChangeDetector.mqTaskHasUnpublishedChanges(t),
                hashIf(published, t.getPublishedSnapshot()),
                dslDeps(published ? t.getPublishedSnapshot() : t.getDslContent()));
    }

    private AssetInfo ofTemplate(ResponseTemplateDO t) {
        String hash = ContentHashUtil.sha256Hex(StrUtil.nullToEmpty(t.getSuccessWrapper()) + "\u0000"
                + StrUtil.nullToEmpty(t.getPageWrapper()) + "\u0000" + StrUtil.nullToEmpty(t.getFailWrapper()));
        return new AssetInfo(RESPONSE_TEMPLATE, t.getId(), t.getTemplateName(), t.getRemark(), false, false, false,
                hash, new ArrayList<>());
    }

    private AssetInfo ofPage(PageInfoDO p) {
        return configInfo(PAGE, p.getId(), p.getName(), p.getRoutePath(), p.getName(), p.getRoutePath(), p.getJson(),
                String.valueOf(p.getStatus()));
    }

    private AssetInfo ofModel(FlowModelInfoDO m) {
        return configInfo(MODEL, m.getId(), m.getName(), m.getDatasource() + " / " + m.getTableName(),
                m.getName(), m.getTableName(), m.getDatasource(), m.getFieldsSchema(), String.valueOf(m.getStatus()));
    }

    private AssetInfo ofMacro(SysMacroDO m) {
        return configInfo(SYS_MACRO, m.getId(), m.getMacroCode() + " · " + StrUtil.nullToEmpty(m.getMacroName()),
                m.getMacroType(), m.getMacroName(), m.getMacroType(), m.getExpression(), m.getScope(),
                m.getReturnType(), m.getMacroParams(), String.valueOf(m.getStatus()));
    }

    /** 列表只展示分组与说明，不回显配置值：只读角色也能搜索资产 */
    private AssetInfo ofConfig(SysConfigDO c) {
        String detail = StrUtil.nullToEmpty(c.getConfigGroup())
                + (StrUtil.isBlank(c.getRemark()) ? "" : " · " + StrUtil.maxLength(c.getRemark(), 40))
                + (ConfigTransferPolicy.rejectReason(c.getConfigKey(), c.getConfigGroup()) != null ? "（不随包迁移）" : "");
        return configInfo(SYS_CONFIG, c.getId(), c.getConfigKey(), detail.trim(), c.getConfigValue(), c.getValueType(),
                c.getConfigGroup(), String.valueOf(c.getStatus()));
    }

    /** 系统配置不能随包迁移的原因；允许迁移或配置不存在返回 null */
    public String configRejectReason(String id) {
        return sysConfigRepository.findById(id)
                .map(c -> ConfigTransferPolicy.rejectReason(c.getConfigKey(), c.getConfigGroup()))
                .orElse(null);
    }

    private AssetInfo ofOpenPlatform(FlowOpenPlatformDO p) {
        StringBuilder grants = new StringBuilder();
        flowOpenApiGrantRepository.findByPlatformId(p.getId()).stream()
                .map(g -> g.getApiId() + "=" + StrUtil.nullToEmpty(g.getAllowMethods()))
                .sorted().forEach(g -> grants.append(g).append(';'));
        return configInfo(OPEN_PLATFORM, p.getId(), p.getName(), p.getCode(), p.getName(), p.getContact(),
                p.getRemark(), String.valueOf(p.getOpenCallLogEnabled()), String.valueOf(p.getRateLimitQps()), grants.toString());
    }

    private AssetInfo ofAlertRule(AlertRuleDO r) {
        return configInfo(ALERT_RULE, r.getId(), r.getName(), r.getWindow() + " / " + r.getMinHealth(), r.getName(),
                r.getScopeAssetTypes(), r.getWindow(), r.getMinHealth(), String.valueOf(r.getTopN()), r.getChannelIds(),
                String.valueOf(r.getIntervalMinutes()), String.valueOf(r.getDedupMinutes()));
    }

    /** 无发布概念的资产：指纹取业务字段拼接 */
    private static AssetInfo configInfo(String type, String id, String name, String detail, String... hashParts) {
        StringBuilder sb = new StringBuilder();
        for (String part : hashParts) {
            sb.append(StrUtil.nullToEmpty(part)).append('\u0000');
        }
        return new AssetInfo(type, id, name, detail, false, false, false, ContentHashUtil.sha256Hex(sb.toString()),
                new ArrayList<>());
    }

    private static List<AssetRef> dslDeps(String content) {
        List<AssetRef> deps = new ArrayList<>();
        for (FlowDslReferenceScanner.OutboundRef ref : FlowDslReferenceScanner.scan(content)) {
            deps.add(new AssetRef("service".equals(ref.targetType()) ? SERVICE : API, ref.targetId()));
        }
        return deps;
    }

    private static boolean isPublished(Integer status, String snapshot) {
        return status != null && status == 1 && StrUtil.isNotBlank(snapshot);
    }

    private static String hashIf(boolean published, String snapshot) {
        return published ? ContentHashUtil.sha256Hex(snapshot) : null;
    }
}
