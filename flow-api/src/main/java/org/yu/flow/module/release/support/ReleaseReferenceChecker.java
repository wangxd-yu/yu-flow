package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.assetref.FlowDslReferenceScanner;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发布前后的调用关系检查：下线的接口 / 内部服务是否仍被在线资产调用，包内资产调用的对象在目标环境是否可用。
 */
@Component
public class ReleaseReferenceChecker {

    @Resource
    private FlowReferenceIndex flowReferenceIndex;
    @Resource
    private ReleaseAssetResolver assetResolver;
    @Resource
    private FlowMqTaskRepository flowMqTaskRepository;
    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    /** 调用方 / 被调方，类型取 {@link ReleaseAssetResolver} 常量 */
    public record Conflict(AssetRef target, String targetName, List<String> callers) {
    }

    /**
     * 下线后仍会调用它的在线资产。
     *
     * @param offline     本次下线项
     * @param upcomingDsl 本次随包发布的资产（类型:ID → 将要发布的 DSL），这些资产按新版本判断；源环境检查传空
     */
    public List<Conflict> offlineConflicts(Collection<AssetRef> offline, Map<String, String> upcomingDsl, Map<AssetRef, String> names) {
        Set<String> offlineKeys = new LinkedHashSet<>();
        offline.forEach(r -> offlineKeys.add(key(r)));
        List<FlowMqTaskDO> mqTasks = null;
        List<Conflict> conflicts = new ArrayList<>();
        for (AssetRef target : offline) {
            if (!ReleaseAssetResolver.API.equals(target.type()) && !ReleaseAssetResolver.SERVICE.equals(target.type())) {
                continue;
            }
            Map<String, AssetRef> candidates = new LinkedHashMap<>();
            for (FlowReferenceIndex.SourceRef s : flowReferenceIndex.findReferrers(indexType(target.type()), target.id())) {
                AssetRef ref = new AssetRef(s.kind().name(), s.id());
                candidates.put(key(ref), ref);
            }
            if (mqTasks == null) {
                mqTasks = flowMqTaskRepository.findAll();
            }
            for (FlowMqTaskDO t : mqTasks) {
                AssetRef ref = new AssetRef(ReleaseAssetResolver.MQ_TASK, t.getId());
                candidates.put(key(ref), ref);
            }
            List<String> callers = new ArrayList<>();
            for (AssetRef caller : candidates.values()) {
                String callerKey = key(caller);
                if (offlineKeys.contains(callerKey)) {
                    continue;
                }
                if (upcomingDsl.containsKey(callerKey)) {
                    if (references(upcomingDsl.get(callerKey), target)) {
                        callers.add(label(caller.type(), names.getOrDefault(caller, caller.id())) + "（本次发布的版本）");
                    }
                    continue;
                }
                AssetInfo info = assetResolver.describe(caller.type(), caller.id());
                if (info != null && info.published() && info.dependencies().contains(target)) {
                    callers.add(label(caller.type(), info.name()));
                }
            }
            if (!callers.isEmpty()) {
                conflicts.add(new Conflict(target, names.getOrDefault(target, target.id()), callers));
            }
        }
        return conflicts;
    }

    /**
     * 包内资产调用的接口 / 内部服务：不在包内且目标环境没有、或没有发布、或会在本次下线的，都会在运行时调用失败，一律阻断。
     */
    public void checkOutbound(AssetBundle bundle, Set<String> offlineKeys, List<String> blocks) {
        Map<String, String> dsl = upcomingDsl(bundle);
        Map<AssetRef, String> names = bundleNames(bundle);
        Set<String> reported = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : dsl.entrySet()) {
            AssetRef caller = parse(e.getKey());
            String callerLabel = label(caller.type(), names.getOrDefault(caller, caller.id()));
            for (FlowDslReferenceScanner.OutboundRef out : FlowDslReferenceScanner.scan(e.getValue())) {
                AssetRef target = new AssetRef("service".equals(out.targetType())
                        ? ReleaseAssetResolver.SERVICE : ReleaseAssetResolver.API, out.targetId());
                String targetKey = key(target);
                if (dsl.containsKey(targetKey) || !reported.add(e.getKey() + "->" + targetKey)
                        || (ReleaseAssetResolver.API.equals(target.type()) && HostCatalogReserved.isReservedId(target.id()))) {
                    continue;
                }
                if (offlineKeys.contains(targetKey)) {
                    blocks.add(callerLabel + "调用的" + ReleaseAssetResolver.LABELS.get(target.type())
                            + "（" + target.id() + "）会在本次下线，请同时下线调用方或改为不再调用");
                    continue;
                }
                boolean exists = ReleaseAssetResolver.SERVICE.equals(target.type())
                        ? flowServiceFlowRepository.existsById(target.id())
                        : flowApiRepository.existsById(target.id());
                if (!exists) {
                    blocks.add(callerLabel + "调用的" + ReleaseAssetResolver.LABELS.get(target.type())
                            + "（" + target.id() + "）在本环境不存在，也不在发布包内，请把它加入版本单");
                } else if (!assetResolver.isOnline(target.type(), target.id())) {
                    AssetInfo info = assetResolver.describe(target.type(), target.id());
                    blocks.add(callerLabel + "调用的" + label(target.type(), info == null ? target.id() : info.name())
                            + "在本环境未发布，运行时调用会失败，请把它加入版本单");
                }
            }
        }
    }

    /** 包内可编排资产将要发布的 DSL（导出时已取线上版本） */
    public static Map<String, String> upcomingDsl(AssetBundle bundle) {
        Map<String, String> dsl = new LinkedHashMap<>();
        for (FlowApiDO a : nullSafe(bundle.getApis())) {
            dsl.put(ReleaseAssetResolver.API + ":" + a.getId(), StrUtil.nullToEmpty(a.getDslContent()));
        }
        for (FlowServiceFlowDO s : nullSafe(bundle.getServices())) {
            dsl.put(ReleaseAssetResolver.SERVICE + ":" + s.getId(), StrUtil.nullToEmpty(s.getDslContent()));
        }
        for (FlowTaskDO t : nullSafe(bundle.getTasks())) {
            dsl.put(ReleaseAssetResolver.TASK + ":" + t.getId(), StrUtil.nullToEmpty(t.getDslContent()));
        }
        for (FlowMqTaskDO t : nullSafe(bundle.getMqTasks())) {
            dsl.put(ReleaseAssetResolver.MQ_TASK + ":" + t.getId(), StrUtil.nullToEmpty(t.getDslContent()));
        }
        return dsl;
    }

    public static Map<AssetRef, String> bundleNames(AssetBundle bundle) {
        Map<AssetRef, String> names = new LinkedHashMap<>();
        nullSafe(bundle.getApis()).forEach(a -> names.put(new AssetRef(ReleaseAssetResolver.API, a.getId()), a.getName()));
        nullSafe(bundle.getServices()).forEach(s -> names.put(new AssetRef(ReleaseAssetResolver.SERVICE, s.getId()), s.getName()));
        nullSafe(bundle.getTasks()).forEach(t -> names.put(new AssetRef(ReleaseAssetResolver.TASK, t.getId()), t.getName()));
        nullSafe(bundle.getMqTasks()).forEach(t -> names.put(new AssetRef(ReleaseAssetResolver.MQ_TASK, t.getId()), t.getName()));
        return names;
    }

    private static boolean references(String dsl, AssetRef target) {
        String type = ReleaseAssetResolver.SERVICE.equals(target.type()) ? "service" : "api";
        return FlowDslReferenceScanner.scan(dsl).stream()
                .anyMatch(r -> type.equals(r.targetType()) && target.id().equals(r.targetId()));
    }

    private static String indexType(String type) {
        return ReleaseAssetResolver.SERVICE.equals(type) ? "service" : "api";
    }

    private static String label(String type, String name) {
        return ReleaseAssetResolver.LABELS.getOrDefault(type, type) + "「" + StrUtil.blankToDefault(name, "-") + "」";
    }

    private static String key(AssetRef ref) {
        return ref.type() + ":" + ref.id();
    }

    private static AssetRef parse(String key) {
        int i = key.indexOf(':');
        return new AssetRef(key.substring(0, i), key.substring(i + 1));
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
