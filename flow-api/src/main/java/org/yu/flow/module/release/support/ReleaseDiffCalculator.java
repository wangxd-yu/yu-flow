package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.model.repository.FlowModelInfoRepository;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.sysconfig.repository.SysConfigRepository;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.BundleOpenPlatform;
import org.yu.flow.module.transfer.support.BundleEntityCopier;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 发布包资产与目标环境当前内容的逐字段差异。
 *
 * <p>可发布资产比较的是目标环境线上生效的版本（发布快照），不是草稿；
 * 导入时保留目标环境值的字段（启停、默认模板标记、平台 IP 白名单等）不参与比较。</p>
 */
@Component
public class ReleaseDiffCalculator {

    public record FieldDiff(String field, String before, String after) {
    }

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    private static final Set<String> COMMON_IGNORED = Set.of("id", "createTime", "updateTime", "createBy", "updateBy",
            "publishStatus", "publishedSnapshot", "publishTime", "deleted");

    private static final Map<String, Set<String>> TYPE_IGNORED = Map.of(
            ReleaseAssetResolver.SERVICE, Set.of("enabled"),
            ReleaseAssetResolver.TASK, Set.of("enabled"),
            ReleaseAssetResolver.MQ_TASK, Set.of("enabled"),
            ReleaseAssetResolver.RESPONSE_TEMPLATE, Set.of("isDefault"),
            ReleaseAssetResolver.SYS_CONFIG, Set.of("isBuiltin"),
            ReleaseAssetResolver.OPEN_PLATFORM, Set.of("status", "ipAllowlist", "expireAt"),
            ReleaseAssetResolver.ALERT_RULE, Set.of("enabled", "channelIds"));

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

    public List<String> changedFields(String type, String key, AssetBundle bundle) {
        return diff(type, key, bundle).stream().map(FieldDiff::field).toList();
    }

    /** 目标环境不存在或类型不支持时返回空列表 */
    public List<FieldDiff> diff(String type, String key, AssetBundle bundle) {
        Map<String, String> before = currentView(type, key);
        Map<String, String> after = incomingView(type, key, bundle);
        if (before == null || after == null) {
            return List.of();
        }
        List<FieldDiff> diffs = new ArrayList<>();
        Set<String> fields = new TreeSet<>(before.keySet());
        fields.addAll(after.keySet());
        for (String f : fields) {
            String b = before.get(f);
            String a = after.get(f);
            if (!Objects.equals(StrUtil.emptyToNull(b), StrUtil.emptyToNull(a))) {
                diffs.add(new FieldDiff(f, b, a));
            }
        }
        return diffs;
    }

    private Map<String, String> currentView(String type, String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        return switch (type) {
            case ReleaseAssetResolver.API -> flowApiRepository.findById(key)
                    .map(a -> view(type, live(a, FlowApiDO.class, a.getPublishStatus(), a.getPublishedSnapshot()))).orElse(null);
            case ReleaseAssetResolver.SERVICE -> flowServiceFlowRepository.findById(key)
                    .map(s -> view(type, live(s, FlowServiceFlowDO.class, s.getPublishStatus(), s.getPublishedSnapshot()))).orElse(null);
            case ReleaseAssetResolver.TASK -> flowTaskRepository.findById(key)
                    .map(t -> view(type, live(t, FlowTaskDO.class, t.getPublishStatus(), t.getPublishedSnapshot()))).orElse(null);
            case ReleaseAssetResolver.MQ_TASK -> flowMqTaskRepository.findById(key)
                    .map(t -> view(type, live(t, FlowMqTaskDO.class, t.getPublishStatus(), t.getPublishedSnapshot()))).orElse(null);
            case ReleaseAssetResolver.RESPONSE_TEMPLATE -> responseTemplateRepository.findById(key).map(t -> view(type, t)).orElse(null);
            case ReleaseAssetResolver.PAGE -> pageInfoRepository.findById(key).map(p -> view(type, p)).orElse(null);
            case ReleaseAssetResolver.MODEL -> flowModelInfoRepository.findById(key).map(m -> view(type, m)).orElse(null);
            case ReleaseAssetResolver.SYS_MACRO -> sysMacroRepository.findByMacroCode(key).map(m -> view(type, m)).orElse(null);
            case ReleaseAssetResolver.SYS_CONFIG -> sysConfigRepository.findByConfigKey(key).map(c -> view(type, c)).orElse(null);
            case ReleaseAssetResolver.OPEN_PLATFORM -> flowOpenPlatformRepository.findByCode(key).map(p -> {
                Map<String, String> v = view(type, p);
                v.put("grants", grantsText(flowOpenApiGrantRepository.findByPlatformId(p.getId()).stream()
                        .map(g -> new BundleOpenPlatform.Grant(g.getApiId(), g.getAllowMethods())).toList()));
                return v;
            }).orElse(null);
            case ReleaseAssetResolver.ALERT_RULE -> alertRuleRepository.findById(key).map(r -> view(type, r)).orElse(null);
            default -> null;
        };
    }

    private Map<String, String> incomingView(String type, String key, AssetBundle b) {
        Object entity = switch (type) {
            case ReleaseAssetResolver.API -> find(b.getApis(), a -> key.equals(a.getId()));
            case ReleaseAssetResolver.SERVICE -> find(b.getServices(), s -> key.equals(s.getId()));
            case ReleaseAssetResolver.TASK -> find(b.getTasks(), t -> key.equals(t.getId()));
            case ReleaseAssetResolver.MQ_TASK -> find(b.getMqTasks(), t -> key.equals(t.getId()));
            case ReleaseAssetResolver.RESPONSE_TEMPLATE -> find(b.getResponseTemplates(), t -> key.equals(t.getId()));
            case ReleaseAssetResolver.PAGE -> find(b.getPages(), p -> key.equals(p.getId()));
            case ReleaseAssetResolver.MODEL -> find(b.getModels(), m -> key.equals(m.getId()));
            case ReleaseAssetResolver.SYS_MACRO -> find(b.getSysMacros(), m -> key.equals(m.getMacroCode()));
            case ReleaseAssetResolver.SYS_CONFIG -> find(b.getSysConfigs(), c -> key.equals(c.getConfigKey()));
            case ReleaseAssetResolver.ALERT_RULE -> {
                var r = find(b.getAlertRules(), x -> x.getRule() != null && key.equals(x.getRule().getId()));
                yield r == null ? null : r.getRule();
            }
            case ReleaseAssetResolver.OPEN_PLATFORM -> {
                BundleOpenPlatform p = find(b.getOpenPlatforms(), x -> x.getPlatform() != null && key.equals(x.getPlatform().getCode()));
                if (p == null) {
                    yield null;
                }
                Map<String, String> v = view(type, p.getPlatform());
                v.put("grants", grantsText(p.getGrants()));
                yield v;
            }
            default -> null;
        };
        if (entity instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, String> v = (Map<String, String>) m;
            return v;
        }
        return entity == null ? null : view(type, entity);
    }

    /** 已发布的资产取线上快照内容 */
    private static <T> T live(T entity, Class<T> cls, Integer publishStatus, String snapshot) {
        T copy = BundleEntityCopier.detachedCopy(entity, cls);
        if (publishStatus != null && publishStatus == 1 && StrUtil.isNotBlank(snapshot)) {
            BundleEntityCopier.applySnapshot(copy, snapshot);
        }
        return copy;
    }

    private static Map<String, String> view(String type, Object entity) {
        Map<String, Object> raw = MAPPER.convertValue(entity, new TypeReference<Map<String, Object>>() {
        });
        Set<String> ignored = TYPE_IGNORED.getOrDefault(type, Set.of());
        Map<String, String> out = new TreeMap<>();
        raw.forEach((k, v) -> {
            if (COMMON_IGNORED.contains(k) || ignored.contains(k)) {
                return;
            }
            out.put(k, stringify(v));
        });
        return out;
    }

    private static String stringify(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    private static String grantsText(List<BundleOpenPlatform.Grant> grants) {
        return grants.stream().map(g -> g.getApiId() + "=" + StrUtil.nullToEmpty(g.getAllowMethods()))
                .sorted().reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static <T> T find(List<T> list, java.util.function.Predicate<T> p) {
        return list == null ? null : list.stream().filter(p).findFirst().orElse(null);
    }
}
