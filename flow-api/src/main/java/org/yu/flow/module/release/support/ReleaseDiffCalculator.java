package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.BundleOpenPlatform;
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
    private ReleaseAssetResolver assetResolver;

    public List<String> changedFields(String type, String key, AssetBundle bundle) {
        return diff(type, key, bundle).stream().map(FieldDiff::field).toList();
    }

    /** 目标环境不存在或类型不支持时返回空列表 */
    public List<FieldDiff> diff(String type, String key, AssetBundle bundle) {
        if (StrUtil.isBlank(key)) {
            return List.of();
        }
        ReleaseAssetType<?> assetType = assetResolver.typeOf(type);
        if (assetType == null) {
            return List.of();
        }
        Map<String, String> before = asView(type, assetType.diffCurrent(key));
        Map<String, String> after = asView(type, assetType.diffIncoming(bundle, key));
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

    private static Map<String, String> asView(String type, Object entity) {
        if (entity instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, String> v = (Map<String, String>) m;
            return v;
        }
        return entity == null ? null : view(type, entity);
    }

    /** 实体展开成参与比较的字段，忽略各类型导入时保留目标环境值的字段 */
    public static Map<String, String> view(String type, Object entity) {
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

    public static String grantsText(List<BundleOpenPlatform.Grant> grants) {
        return grants.stream().map(g -> g.getApiId() + "=" + StrUtil.nullToEmpty(g.getAllowMethods()))
                .sorted().reduce((a, b) -> a + "\n" + b).orElse("");
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
}
