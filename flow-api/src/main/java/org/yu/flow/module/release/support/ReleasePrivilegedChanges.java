package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.release.dto.ReleaseInspectResultDTO.PrivilegedChange;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.BundleOpenPlatform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 导入会带来的高权限变更：全局宏（SpEL，可读取 {@code @environment} 下的任意配置项）与开放平台授权。
 *
 * <p>持有导入权限即可写入这些内容，预检逐项列出，导入时要求运维显式确认。</p>
 */
@Component
public class ReleasePrivilegedChanges {

    private static final int PREVIEW = 300;
    private static final int NAME_LIMIT = 5;

    @Resource
    private SysMacroRepository sysMacroRepository;
    @Resource
    private FlowOpenPlatformRepository flowOpenPlatformRepository;
    @Resource
    private FlowOpenApiGrantRepository flowOpenApiGrantRepository;
    @Resource
    private FlowApiRepository flowApiRepository;

    public List<PrivilegedChange> collect(AssetBundle bundle) {
        List<PrivilegedChange> changes = new ArrayList<>();
        for (SysMacroDO in : nullSafe(bundle.getSysMacros())) {
            macroChange(in).ifPresent(changes::add);
        }
        Map<String, String> bundledApiNames = new LinkedHashMap<>();
        nullSafe(bundle.getApis()).forEach(a -> bundledApiNames.put(a.getId(), a.getName()));
        for (BundleOpenPlatform item : nullSafe(bundle.getOpenPlatforms())) {
            platformChange(item, bundledApiNames).ifPresent(changes::add);
        }
        return changes;
    }

    private Optional<PrivilegedChange> macroChange(SysMacroDO in) {
        if (StrUtil.isBlank(in.getMacroCode()) || StrUtil.isBlank(in.getExpression())) {
            return Optional.empty();
        }
        SysMacroDO existing = sysMacroRepository.findByMacroCode(in.getMacroCode()).orElse(null);
        String description;
        if (existing == null) {
            description = "新增全局宏，表达式：" + preview(in.getExpression());
        } else if (!Objects.equals(existing.getExpression(), in.getExpression())
                || !Objects.equals(existing.getMacroParams(), in.getMacroParams())) {
            description = "修改全局宏表达式：" + preview(existing.getExpression()) + " → " + preview(in.getExpression());
        } else if (!Integer.valueOf(1).equals(existing.getStatus()) && Integer.valueOf(1).equals(in.getStatus())) {
            description = "启用全局宏，表达式：" + preview(in.getExpression());
        } else {
            return Optional.empty();
        }
        return Optional.of(new PrivilegedChange(ReleaseAssetResolver.SYS_MACRO, in.getMacroCode(),
                in.getMacroName(), description));
    }

    /** 与导入一致：授权的接口既不在包内、目标环境也没有时会被忽略，不计入变更 */
    private Optional<PrivilegedChange> platformChange(BundleOpenPlatform item, Map<String, String> bundledApiNames) {
        FlowOpenPlatformDO in = item.getPlatform();
        if (in == null || StrUtil.isBlank(in.getCode())) {
            return Optional.empty();
        }
        Map<String, String> incoming = new LinkedHashMap<>();
        for (BundleOpenPlatform.Grant g : item.getGrants()) {
            if (bundledApiNames.containsKey(g.getApiId()) || flowApiRepository.existsById(g.getApiId())) {
                incoming.put(g.getApiId(), StrUtil.nullToEmpty(g.getAllowMethods()));
            }
        }
        FlowOpenPlatformDO existing = flowOpenPlatformRepository.findByCode(in.getCode()).orElse(null);
        if (existing == null) {
            String description = incoming.isEmpty() ? "新增开放平台（暂无接口授权），AppKey 由本环境签发"
                    : "新增开放平台，授权 " + incoming.size() + " 个接口：" + names(incoming.keySet(), bundledApiNames);
            return Optional.of(new PrivilegedChange(ReleaseAssetResolver.OPEN_PLATFORM, in.getCode(),
                    in.getName(), description));
        }
        Map<String, String> current = new LinkedHashMap<>();
        for (FlowOpenApiGrantDO g : flowOpenApiGrantRepository.findByPlatformId(existing.getId())) {
            current.put(g.getApiId(), StrUtil.nullToEmpty(g.getAllowMethods()));
        }
        List<String> added = incoming.keySet().stream().filter(id -> !current.containsKey(id)).toList();
        List<String> removed = current.keySet().stream().filter(id -> !incoming.containsKey(id)).toList();
        List<String> methodChanged = incoming.keySet().stream()
                .filter(id -> current.containsKey(id) && !current.get(id).equals(incoming.get(id))).toList();
        if (added.isEmpty() && removed.isEmpty() && methodChanged.isEmpty()) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>();
        if (!added.isEmpty()) {
            parts.add("新增授权 " + added.size() + " 个：" + names(added, bundledApiNames));
        }
        if (!removed.isEmpty()) {
            parts.add("撤销授权 " + removed.size() + " 个：" + names(removed, bundledApiNames));
        }
        if (!methodChanged.isEmpty()) {
            parts.add("调整允许的方法 " + methodChanged.size() + " 个：" + names(methodChanged, bundledApiNames));
        }
        return Optional.of(new PrivilegedChange(ReleaseAssetResolver.OPEN_PLATFORM, in.getCode(),
                in.getName(), String.join("；", parts)));
    }

    private String names(Iterable<String> apiIds, Map<String, String> bundledApiNames) {
        List<String> names = new ArrayList<>();
        int total = 0;
        for (String id : apiIds) {
            total++;
            if (names.size() < NAME_LIMIT) {
                String name = bundledApiNames.get(id);
                if (name == null) {
                    name = flowApiRepository.findById(id).map(FlowApiDO::getName).orElse(id);
                }
                names.add(name);
            }
        }
        return String.join("、", names) + (total > NAME_LIMIT ? " 等" : "");
    }

    private static String preview(String expression) {
        return StrUtil.maxLength(StrUtil.nullToEmpty(expression).replaceAll("\\s+", " "), PREVIEW);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
