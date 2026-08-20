package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yu.flow.module.host.dto.HostIdentityCatalogDTO;
import org.yu.flow.module.host.dto.HostIdentityCatalogDimensionDTO;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 读取可选的 {@link FlowHostIdentityCatalogProvider}，规范化后给管理端。
 * <p>有 SPI Bean 时走宿主；否则走「宿主机配置」里已启用的保留接口。
 * 仅已启用维度出现在策略表单；已保存约束在维度停用后仍保留匹配，避免策略被静默放宽。</p>
 */
@Slf4j
@Component
public class FlowHostIdentityCatalogService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;
    public static final int TREE_LIMIT = 2000;

    static final String SOURCE_HOST = "HOST";
    static final String SOURCE_FLOW = "FLOW";

    private final ObjectProvider<FlowHostIdentityCatalogProvider> catalogProvider;
    private final HostCatalogApiExecutor apiExecutor;
    private final HostIdentityCatalogSettingsStore settingsStore;
    /**
     * 运行时调用方校验属于高频路径，不能每次重新执行宿主/保留目录接口。
     * 配置和 API 发布事件会主动失效；TTL 负责兜底 SPI 数据自行变化的场景。
     */
    private final Cache<String, HostIdentityCatalogDTO> snapshotCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
    private final Cache<String, List<HostIdentityCatalogItemDTO>> deptTreeCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
    private final Cache<CallerPolicy, CallerPolicy> effectivePolicyCache = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    public FlowHostIdentityCatalogService(ObjectProvider<FlowHostIdentityCatalogProvider> catalogProvider) {
        this(catalogProvider, null, null);
    }

    @Autowired
    public FlowHostIdentityCatalogService(ObjectProvider<FlowHostIdentityCatalogProvider> catalogProvider,
                                          HostCatalogApiExecutor apiExecutor,
                                          HostIdentityCatalogSettingsStore settingsStore) {
        this.catalogProvider = catalogProvider;
        this.apiExecutor = apiExecutor;
        this.settingsStore = settingsStore;
    }

    public Set<String> expandPrincipalDepts(FlowHostPrincipal principal) {
        LinkedHashSet<String> raw = new LinkedHashSet<>();
        if (principal != null) {
            if (StrUtil.isNotBlank(principal.getDeptId())) {
                raw.add(principal.getDeptId().trim());
            }
            if (principal.getDeptIds() != null) {
                for (String deptId : principal.getDeptIds()) {
                    if (StrUtil.isNotBlank(deptId)) {
                        raw.add(deptId.trim());
                    }
                }
            }
        }
        if (raw.isEmpty()) {
            return raw;
        }
        return HostDeptTree.expand(loadDeptItemsForTree(), raw);
    }

    public HostIdentityCatalogDTO snapshot() {
        return snapshotCache.get("snapshot", ignored -> loadSnapshot());
    }

    private HostIdentityCatalogDTO loadSnapshot() {
        FlowHostIdentityCatalogProvider provider = catalogProvider.getIfAvailable();
        HostIdentityCatalogDTO dto = new HostIdentityCatalogDTO();
        Map<String, HostIdentityCatalogDimensionDTO> dims = new LinkedHashMap<>();
        if (provider != null) {
            dto.setAvailable(true);
            for (FlowHostCatalogDimension d : FlowHostCatalogDimension.values()) {
                boolean supported = safeSupports(provider, d);
                boolean searchable = supported && safeSearchable(provider, d);
                HostIdentityCatalogDimensionDTO slice = emptySlice(supported, searchable);
                if (supported && (!searchable || d == FlowHostCatalogDimension.DEPT)) {
                    int cap = d == FlowHostCatalogDimension.DEPT ? TREE_LIMIT : DEFAULT_LIMIT;
                    slice.setItems(listItems(provider, d, null, cap));
                }
                dims.put(d.name(), slice);
            }
            dto.setDimensions(dims);
            return dto;
        }
        HostIdentityCatalogSettings settings = settingsStore != null
                ? settingsStore.load() : new HostIdentityCatalogSettings();
        boolean anyEnabled = false;
        for (FlowHostCatalogDimension d : FlowHostCatalogDimension.values()) {
            HostCatalogDimBinding binding = settings.get(d);
            boolean supported = binding.isEnabled();
            boolean searchable = supported && binding.isSearchable();
            HostIdentityCatalogDimensionDTO slice = emptySlice(supported, searchable);
            if (supported && (!searchable || d == FlowHostCatalogDimension.DEPT)) {
                int cap = d == FlowHostCatalogDimension.DEPT ? TREE_LIMIT : DEFAULT_LIMIT;
                slice.setItems(listReserved(d, null, cap));
            }
            if (supported) {
                anyEnabled = true;
            }
            dims.put(d.name(), slice);
        }
        dto.setAvailable(anyEnabled);
        dto.setDimensions(dims);
        return dto;
    }

    public List<HostIdentityCatalogItemDTO> search(FlowHostCatalogDimension dimension, String keyword, Integer limit) {
        FlowHostIdentityCatalogProvider provider = catalogProvider.getIfAvailable();
        if (provider != null) {
            if (!safeSupports(provider, dimension)) {
                return List.of();
            }
            return listItems(provider, dimension, keyword, clampLimit(limit));
        }
        HostIdentityCatalogSettings settings = settingsStore != null
                ? settingsStore.load() : new HostIdentityCatalogSettings();
        HostCatalogDimBinding binding = settings.get(dimension);
        if (!binding.isEnabled()) {
            return List.of();
        }
        return listReserved(dimension, keyword, clampLimit(limit));
    }

    /** 保留所有已保存约束；目录开关只影响管理端候选项，不能在运行时放宽权限。 */
    public CallerPolicy restrictToActiveDimensions(CallerPolicy policy) {
        return policy == null ? null : copyPolicy(policy);
    }

    /** 去掉未启用维度后，再按部门树展开「含下级」。 */
    public CallerPolicy effectivePolicy(CallerPolicy policy) {
        if (policy == null) {
            return null;
        }
        return effectivePolicyCache.get(policy, key ->
                applyDeptTree(restrictToActiveDimensions(key)));
    }

    CallerPolicy applyDeptTree(CallerPolicy policy) {
        if (policy == null) {
            return null;
        }
        boolean topNeed = policy.includeDeptChildren() && notEmptyIds(policy.getDeptIds());
        boolean ruleNeed = false;
        if (policy.getRules() != null) {
            for (CallerAccessRule rule : policy.getRules()) {
                if (rule != null && includeDeptChildren(rule) && notEmptyIds(rule.getDeptIds())) {
                    ruleNeed = true;
                    break;
                }
            }
        }
        if (!topNeed && !ruleNeed) {
            return policy;
        }
        List<HostIdentityCatalogItemDTO> tree = loadDeptItemsForTree();
        if (!HostDeptTree.hasTree(tree)) {
            return policy;
        }
        CallerPolicy out = copyPolicy(policy);
        if (topNeed) {
            out.setDeptIds(new ArrayList<>(HostDeptTree.expand(tree, policy.getDeptIds())));
        }
        if (ruleNeed && out.getRules() != null) {
            for (CallerAccessRule rule : out.getRules()) {
                if (rule != null && includeDeptChildren(rule) && notEmptyIds(rule.getDeptIds())) {
                    rule.setDeptIds(new ArrayList<>(HostDeptTree.expand(tree, rule.getDeptIds())));
                }
            }
        }
        return out;
    }

    private static boolean includeDeptChildren(PrincipalMatch match) {
        return match.getDeptIncludeChildren() == null || Boolean.TRUE.equals(match.getDeptIncludeChildren());
    }

    private static boolean notEmptyIds(List<String> ids) {
        return ids != null && ids.stream().anyMatch(StrUtil::isNotBlank);
    }

    private List<HostIdentityCatalogItemDTO> loadDeptItemsForTree() {
        return deptTreeCache.get("dept-tree", ignored -> fetchDeptItemsForTree());
    }

    private List<HostIdentityCatalogItemDTO> fetchDeptItemsForTree() {
        FlowHostIdentityCatalogProvider provider = catalogProvider.getIfAvailable();
        if (provider != null) {
            if (!safeSupports(provider, FlowHostCatalogDimension.DEPT)) {
                return List.of();
            }
            return listItems(provider, FlowHostCatalogDimension.DEPT, null, TREE_LIMIT);
        }
        HostIdentityCatalogSettings settings = settingsStore != null
                ? settingsStore.loadFreshRequired() : new HostIdentityCatalogSettings();
        if (!settings.get(FlowHostCatalogDimension.DEPT).isEnabled()) {
            return List.of();
        }
        return listReserved(FlowHostCatalogDimension.DEPT, null, TREE_LIMIT);
    }

    /** 配置保存、保留 API 发布后由消息监听器调用。 */
    public void invalidateRuntimeCache() {
        snapshotCache.invalidateAll();
        deptTreeCache.invalidateAll();
        effectivePolicyCache.invalidateAll();
    }

    private static CallerPolicy copyPolicy(CallerPolicy src) {
        CallerPolicy out = new CallerPolicy();
        out.setEnabled(src.isEnabled());
        out.setMatch(src.getMatch());
        out.setUserTypes(copyList(src.getUserTypes()));
        out.setRoles(copyList(src.getRoles()));
        out.setPermissions(copyList(src.getPermissions()));
        out.setDeptIds(copyList(src.getDeptIds()));
        out.setDeptIncludeChildren(src.getDeptIncludeChildren());
        out.setUserIds(copyList(src.getUserIds()));
        if (src.getRules() != null && !src.getRules().isEmpty()) {
            List<CallerAccessRule> copied = new ArrayList<>();
            for (CallerAccessRule rule : src.getRules()) {
                copied.add(copyAccessRule(rule));
            }
            out.setRules(copied);
        }
        return out;
    }

    private static CallerAccessRule copyAccessRule(CallerAccessRule src) {
        if (src == null) {
            return null;
        }
        CallerAccessRule out = new CallerAccessRule();
        out.setName(src.getName());
        out.setPrincipals(src.getPrincipals());
        out.setMatch(src.getMatch());
        out.setUserTypes(copyList(src.getUserTypes()));
        out.setRoles(copyList(src.getRoles()));
        out.setPermissions(copyList(src.getPermissions()));
        out.setUserIds(copyList(src.getUserIds()));
        out.setDeptIds(copyList(src.getDeptIds()));
        out.setDeptIncludeChildren(src.getDeptIncludeChildren());
        out.setEffect(src.getEffect());
        return out;
    }

    private static List<String> copyList(List<String> src) {
        return src == null ? new ArrayList<>() : new ArrayList<>(src);
    }

    private List<HostIdentityCatalogItemDTO> listReserved(FlowHostCatalogDimension dimension,
                                                          String keyword,
                                                          int limit) {
        if (apiExecutor == null) {
            return List.of();
        }
        List<HostIdentityCatalogItemDTO> loaded =
                apiExecutor.listPreferPublished(dimension, keyword, limit);
        List<HostIdentityCatalogItemDTO> out =
                loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        appendOpenAppIfNeeded(dimension, keyword, limit, out);
        return out;
    }

    static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    static int clampTreeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return TREE_LIMIT;
        }
        return Math.min(limit, TREE_LIMIT);
    }

    private static HostIdentityCatalogDimensionDTO emptySlice(boolean supported, boolean searchable) {
        return new HostIdentityCatalogDimensionDTO()
                .setSupported(supported)
                .setSearchable(searchable)
                .setItems(new ArrayList<>());
    }

    private boolean safeSupports(FlowHostIdentityCatalogProvider provider, FlowHostCatalogDimension d) {
        try {
            return provider.supports(d);
        } catch (Exception e) {
            log.warn("[HostCatalog] supports({}) failed: {}", d, e.getMessage());
            return false;
        }
    }

    private boolean safeSearchable(FlowHostIdentityCatalogProvider provider, FlowHostCatalogDimension d) {
        try {
            return provider.searchable(d);
        } catch (Exception e) {
            log.warn("[HostCatalog] searchable({}) failed: {}", d, e.getMessage());
            return d == FlowHostCatalogDimension.DEPT || d == FlowHostCatalogDimension.USER;
        }
    }

    private List<HostIdentityCatalogItemDTO> listItems(FlowHostIdentityCatalogProvider provider,
                                                       FlowHostCatalogDimension dimension,
                                                       String keyword,
                                                       int limit) {
        List<FlowHostCatalogItem> raw;
        try {
            List<FlowHostCatalogItem> listed = provider.list(dimension, StrUtil.trim(keyword), limit);
            raw = listed != null ? listed : List.of();
        } catch (Exception e) {
            log.warn("[HostCatalog] list({}) failed: {}", dimension, e.getMessage());
            raw = List.of();
        }
        List<HostIdentityCatalogItemDTO> out = new ArrayList<>();
        LinkedHashMap<String, HostIdentityCatalogItemDTO> seen = new LinkedHashMap<>();
        for (FlowHostCatalogItem item : raw) {
            HostIdentityCatalogItemDTO dto = toDto(item, SOURCE_HOST);
            if (dto == null) {
                continue;
            }
            String key = dto.getValue().toLowerCase(Locale.ROOT);
            seen.putIfAbsent(key, dto);
            if (seen.size() >= limit) {
                break;
            }
        }
        out.addAll(seen.values());
        appendOpenAppIfNeeded(dimension, keyword, limit, out);
        return out;
    }

    private static HostIdentityCatalogItemDTO toDto(FlowHostCatalogItem item, String source) {
        if (item == null || StrUtil.isBlank(item.getValue())) {
            return null;
        }
        String value = item.getValue().trim();
        return new HostIdentityCatalogItemDTO()
                .setValue(value)
                .setLabel(item.displayLabel())
                .setHint(StrUtil.trim(item.getHint()))
                .setParentId(StrUtil.trim(item.getParentId()))
                .setDisabled(Boolean.TRUE.equals(item.getDisabled()))
                .setSource(source);
    }

    /**
     * OPEN_APP 由网关合成，宿主目录往往不含。目录 SPI 已对接时补一条，便于策略勾选。
     */
    private static void appendOpenAppIfNeeded(FlowHostCatalogDimension dimension, String keyword,
                                             int limit, List<HostIdentityCatalogItemDTO> out) {
        if (dimension != FlowHostCatalogDimension.USER_TYPE) {
            return;
        }
        boolean exists = out.stream().anyMatch(i ->
                FlowHostPrincipal.TYPE_OPEN_APP.equalsIgnoreCase(i.getValue()));
        if (exists || out.size() >= limit) {
            return;
        }
        if (!keywordMatchesOpenApp(keyword)) {
            return;
        }
        out.add(new HostIdentityCatalogItemDTO()
                .setValue(FlowHostPrincipal.TYPE_OPEN_APP)
                .setLabel("开放应用 (OPEN_APP)")
                .setHint("由 Flow 开放入口合成，非宿主 Session")
                .setDisabled(false)
                .setSource(SOURCE_FLOW));
    }

    private static boolean keywordMatchesOpenApp(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return true;
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        return "open_app 开放应用".contains(k);
    }
}
