package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.yu.flow.module.transfer.support.ConfigAssetTransfer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 版本单可纳入的资产：查询、发布状态、内容指纹、直接依赖。
 *
 * <p>每种资产的查询与展示由所在模块的 {@link ReleaseAssetType} 实现提供，这里按类型分派。</p>
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

    /** 有草稿 / 发布快照之分的类型 */
    private static final Set<String> PUBLISHABLE_TYPES = Set.of(API, SERVICE, TASK, MQ_TASK);

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

    private final Map<String, ReleaseAssetType<?>> types = new HashMap<>();

    public ReleaseAssetResolver(List<ReleaseAssetType<?>> assetTypes) {
        for (ReleaseAssetType<?> t : assetTypes) {
            if (types.put(t.type(), t) != null) {
                throw new IllegalStateException("资产类型 " + t.type() + " 有多个实现");
            }
        }
        for (String type : TYPES) {
            if (!types.containsKey(type)) {
                throw new IllegalStateException("资产类型 " + type + " 缺少 ReleaseAssetType 实现");
            }
        }
    }

    public static String normalizeType(String type) {
        String t = StrUtil.trimToEmpty(type).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new IllegalArgumentException("不支持的资产类型: " + type);
        }
        return t;
    }

    /** 与 {@link #describeAll} 的结果键一致 */
    public static String refKey(String type, String id) {
        return normalizeType(type) + "\0" + id;
    }

    /** 该类型的实现；类型不认识时返回 null */
    public ReleaseAssetType<?> typeOf(String type) {
        return types.get(StrUtil.trimToEmpty(type).toUpperCase(Locale.ROOT));
    }

    private ReleaseAssetType<?> require(String type) {
        return types.get(normalizeType(type));
    }

    /**
     * 按类型各查一次，供版本单详情等批量展示。不存在的资产不出现在结果里。
     */
    public Map<String, AssetInfo> describeAll(Collection<AssetRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> idsByType = new LinkedHashMap<>();
        for (AssetRef ref : refs) {
            if (ref == null || StrUtil.isBlank(ref.id())) {
                continue;
            }
            idsByType.computeIfAbsent(normalizeType(ref.type()), key -> new ArrayList<>()).add(ref.id());
        }
        Map<String, AssetInfo> out = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : idsByType.entrySet()) {
            for (AssetInfo info : describeAll(types.get(entry.getKey()), entry.getValue().stream().distinct().toList())) {
                out.put(refKey(info.type(), info.id()), info);
            }
        }
        return out;
    }

    private static <T> List<AssetInfo> describeAll(ReleaseAssetType<T> t, List<String> ids) {
        return t.findAllById(ids).stream().filter(t::visible).map(t::describe).toList();
    }

    /** 资产不存在（含已删除）返回 null */
    public AssetInfo describe(String type, String id) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        return describe(require(type), id);
    }

    private static <T> AssetInfo describe(ReleaseAssetType<T> t, String id) {
        return t.findById(id).filter(t::visible).map(t::describe).orElse(null);
    }

    /** 资产行是否存在（不论是否参与版本管理） */
    public boolean exists(String type, String id) {
        return StrUtil.isNotBlank(id) && require(type).findById(id).isPresent();
    }

    /** 按编码匹配的类型返回编码（宏编码 / 配置键 / 平台编码），其余返回 null */
    public String keyOf(String type, String id) {
        String t = normalizeType(type);
        return KEYED_TYPES.contains(t) ? keyOf(types.get(t), id) : null;
    }

    private static <T> String keyOf(ReleaseAssetType<T> t, String id) {
        return t.findById(id).map(t::keyOf).orElse(null);
    }

    /** 资产当前是否「在线」：已发布 / 页面已发布 / 规则或平台已启用 */
    public boolean isOnline(String type, String id) {
        String t = normalizeType(type);
        return OFFLINE_TYPES.contains(t) && isOnline(types.get(t), id);
    }

    private static <T> boolean isOnline(ReleaseAssetType<T> t, String id) {
        return t.findById(id).filter(t::visible).map(t::online).orElse(false);
    }

    /** 导入目标环境里这一项当前是否在线：按编码匹配的类型用 key 找，其余用 id */
    public boolean isOnlineHere(String type, String id, String key) {
        String t = normalizeType(type);
        if (!OFFLINE_TYPES.contains(t)) {
            return false;
        }
        return KEYED_TYPES.contains(t) ? isOnlineByKey(types.get(t), key) : isOnline(types.get(t), id);
    }

    private static <T> boolean isOnlineByKey(ReleaseAssetType<T> t, String key) {
        return StrUtil.isNotBlank(key) && t.findByKey(key).filter(t::visible).map(t::online).orElse(false);
    }

    /**
     * 自某时间点以来有变化的资产：可发布资产看发布时间，其余看更新时间。
     * 用于版本单「扫描变更」。
     */
    public List<AssetInfo> changedSince(String type, LocalDateTime since, int limit) {
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updateTime"));
        return changedSince(require(type), since, page);
    }

    private static <T> List<AssetInfo> changedSince(ReleaseAssetType<T> t, LocalDateTime since, PageRequest page) {
        return t.changedSince(since, page).stream().filter(t::visible).map(t::describe).toList();
    }

    /** 可发布资产的最近发布时间；未发布或不适用返回 null */
    public LocalDateTime publishTime(String type, String id) {
        String t = normalizeType(type);
        return PUBLISHABLE_TYPES.contains(t) ? publishTime(types.get(t), id) : null;
    }

    private static <T> LocalDateTime publishTime(ReleaseAssetType<T> t, String id) {
        return t.findById(id).map(t::publishTime).orElse(null);
    }

    public List<AssetInfo> search(String type, String keyword) {
        String kw = StrUtil.trimToNull(keyword);
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT, Sort.by(Sort.Direction.DESC, "updateTime"));
        return search(require(type), kw, page);
    }

    private static <T> List<AssetInfo> search(ReleaseAssetType<T> t, String keyword, PageRequest page) {
        return t.search(keyword, page).stream().filter(t::visible).map(t::describe).toList();
    }

    /** 系统配置不能随包迁移的原因；允许迁移或配置不存在返回 null */
    public String configRejectReason(String id) {
        return rejectReason(require(SYS_CONFIG), id);
    }

    private static <T> String rejectReason(ReleaseAssetType<T> t, String id) {
        return t.findById(id).map(t::transferRejectReason).orElse(null);
    }

    /**
     * 下线一个资产（按 ID 匹配的类型用 id，按编码匹配的类型用 key）。
     *
     * @return 该类型不支持下线或资产不存在时返回 false
     */
    public boolean takeOffline(String type, String id, String key) {
        ReleaseAssetType<?> t = typeOf(type);
        return t != null && t.takeOffline(id, key);
    }
}
