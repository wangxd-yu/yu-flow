package org.yu.flow.module.api.host;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.api.support.ApiInterceptMode;
import org.yu.flow.module.api.support.HostBindingConfig;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 扫描宿主 {@link RequestMappingHandlerMapping}，供批量纳管为 WRAP 草稿。
 */
@Slf4j
@Service
public class HostApiDiscoveryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowApiCrudService flowApiCrudService;

    public List<HostApiRouteDTO> listHostRoutes() {
        Map<String, ManagedRef> managed = loadManagedIndex();
        Map<String, HostApiRouteDTO> dedup = new LinkedHashMap<>();

        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : map.entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();
            Set<String> patterns = info.getPatternValues();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (patterns == null || patterns.isEmpty()) {
                continue;
            }
            List<String> methodNames = new ArrayList<>();
            if (methods == null || methods.isEmpty()) {
                methodNames.add("GET");
            } else {
                for (org.springframework.web.bind.annotation.RequestMethod m : methods) {
                    methodNames.add(m.name());
                }
            }
            for (String pattern : patterns) {
                if (HostApiProbeService.shouldSkipPath(pattern)) {
                    continue;
                }
                String path = HostApiProbeService.normalizePath(pattern);
                for (String method : methodNames) {
                    String key = method.toUpperCase(Locale.ROOT) + " " + path;
                    ManagedRef ref = managed.get(key);
                    HostApiRouteDTO dto = HostApiRouteDTO.builder()
                            .method(method.toUpperCase(Locale.ROOT))
                            .path(path)
                            .handlerClass(hm.getBeanType().getSimpleName())
                            .handlerMethod(hm.getMethod().getName())
                            .managed(ref != null)
                            .managedApiId(ref == null ? null : ref.id)
                            .managedApiName(ref == null ? null : ref.name)
                            .build();
                    dedup.putIfAbsent(key, dto);
                }
            }
        }

        List<HostApiRouteDTO> list = new ArrayList<>(dedup.values());
        list.sort(Comparator
                .comparing(HostApiRouteDTO::isManaged)
                .thenComparing(HostApiRouteDTO::getPath)
                .thenComparing(HostApiRouteDTO::getMethod));
        return list;
    }

    /**
     * 宿主是否存在同 method + path 的 MVC 路由（按 HTTP 接口身份，非 Yu Flow 名称字段）。
     */
    public boolean existsHostRoute(String method, String path) {
        if (StrUtil.isBlank(method) || StrUtil.isBlank(path)) {
            return false;
        }
        String wantMethod = method.trim().toUpperCase(Locale.ROOT);
        String wantPath = HostApiProbeService.normalizePath(path);
        if (HostApiProbeService.shouldSkipPath(wantPath)) {
            return false;
        }
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
        for (RequestMappingInfo info : map.keySet()) {
            Set<String> patterns = info.getPatternValues();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (patterns == null || patterns.isEmpty()) {
                continue;
            }
            boolean methodOk = methods == null || methods.isEmpty()
                    || methods.stream().anyMatch(m -> m.name().equalsIgnoreCase(wantMethod));
            if (!methodOk) {
                continue;
            }
            for (String pattern : patterns) {
                if (HostApiProbeService.shouldSkipPath(pattern)) {
                    continue;
                }
                String p = HostApiProbeService.normalizePath(pattern);
                if (wantPath.equals(p) || antPathMatch(p, wantPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 简单 Ant：段数相同，{var} / * 可匹配任意段 */
    private static boolean antPathMatch(String pattern, String path) {
        String[] pp = pattern.split("/");
        String[] vv = path.split("/");
        if (pp.length != vv.length) {
            return false;
        }
        for (int i = 0; i < pp.length; i++) {
            String a = pp[i];
            String b = vv[i];
            if (a.startsWith("{") && a.endsWith("}")) {
                continue;
            }
            if ("*".equals(a)) {
                continue;
            }
            if (!a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将选中宿主路由导入为 WRAP 草稿（未发布）。
     *
     * @return 新建条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int importAsWrapDrafts(List<HostApiRouteImportItem> items, String directoryId) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        Map<String, ManagedRef> managed = loadManagedIndex();
        int created = 0;
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (HostApiRouteImportItem item : items) {
            if (item == null || StrUtil.isBlank(item.getPath()) || StrUtil.isBlank(item.getMethod())) {
                continue;
            }
            String method = item.getMethod().trim().toUpperCase(Locale.ROOT);
            String path = HostApiProbeService.normalizePath(item.getPath());
            String key = method + " " + path;
            if (managed.containsKey(key)) {
                continue;
            }
            FlowApiDO api = new FlowApiDO();
            api.setName(buildDefaultName(method, path));
            api.setUrl(path);
            api.setMethod(method);
            api.setServiceType(ApiInterceptMode.SERVICE_TYPE_HOST);
            api.setInterceptMode(ApiInterceptMode.WRAP);
            HostBindingConfig binding = new HostBindingConfig();
            binding.setForward(HostBindingConfig.FORWARD_LOCAL);
            binding.setLogMode(HostBindingConfig.LOG_ERROR_ONLY);
            binding.setProbeEnabled(true);
            api.setHostBinding(binding.toJson());
            api.setLogEnabled(false);
            api.setPublishStatus(0);
            api.setDirectoryId(directoryId);
            api.setCreateTime(now);
            api.setUpdateTime(now);
            flowApiCrudService.save(api);
            managed.put(key, new ManagedRef(api.getId(), api.getName()));
            created++;
        }
        return created;
    }

    private Map<String, ManagedRef> loadManagedIndex() {
        Map<String, ManagedRef> map = new HashMap<>();
        for (FlowApiDO api : flowApiRepository.findAll()) {
            if (api == null || StrUtil.isBlank(api.getUrl()) || StrUtil.isBlank(api.getMethod())) {
                continue;
            }
            String key = api.getMethod().trim().toUpperCase(Locale.ROOT)
                    + " " + HostApiProbeService.normalizePath(api.getUrl());
            map.putIfAbsent(key, new ManagedRef(api.getId(), api.getName()));
        }
        return map;
    }

    private static String buildDefaultName(String method, String path) {
        String base = path.replace('/', '_').replaceAll("^_|_$", "");
        if (base.length() > 14) {
            base = base.substring(0, 14);
        }
        String name = method + "_" + (StrUtil.isBlank(base) ? "root" : base);
        return name.length() > 20 ? name.substring(0, 20) : name;
    }

    private record ManagedRef(String id, String name) {
    }
}
