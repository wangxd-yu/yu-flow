package org.yu.flow.module.api.host;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.support.ApiInterceptMode;
import org.yu.flow.module.api.support.HostBindingConfig;
import org.yu.flow.module.api.support.PublishedApiSnapshot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WRAP 接口主动探活：检查宿主 MVC 是否仍注册对应 path（无 HTTP 回环，避免 SSRF/鉴权干扰）。
 * <p>自管理调度，不依赖宿主 {@code @EnableScheduling}。</p>
 */
@Slf4j
@Service
public class HostApiProbeService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private final ConcurrentHashMap<String, HostApiProbeResult> cache = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yu-flow-host-api-probe");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::probeAllPublishedWrap, 45, 60, TimeUnit.SECONDS);
        log.info("[HostApiProbe] 已启动，间隔 60s");
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public HostApiProbeResult get(String apiId) {
        if (StrUtil.isBlank(apiId)) {
            return null;
        }
        return cache.get(apiId);
    }

    public List<HostApiProbeResult> batchGet(Collection<String> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<HostApiProbeResult> list = new ArrayList<>();
        for (String id : apiIds) {
            HostApiProbeResult r = cache.get(id);
            if (r != null) {
                list.add(r);
            } else {
                list.add(HostApiProbeResult.builder()
                        .apiId(id)
                        .status("unknown")
                        .message("尚未探测")
                        .build());
            }
        }
        return list;
    }

    /** 管理端手动触发单条 */
    public HostApiProbeResult probeNow(String apiId) {
        Optional<FlowApiDO> opt = flowApiRepository.findById(apiId);
        if (opt.isEmpty()) {
            return HostApiProbeResult.builder()
                    .apiId(apiId)
                    .status("skip")
                    .message("接口不存在")
                    .checkedAt(LocalDateTime.now(ZONE))
                    .build();
        }
        HostApiProbeResult r = probeOne(opt.get());
        if (r != null) {
            cache.put(apiId, r);
        }
        return r;
    }

    public void probeAllPublishedWrap() {
        try {
            List<FlowApiDO> published = flowApiRepository.findByPublishStatus(1);
            if (published == null || published.isEmpty()) {
                return;
            }
            Set<String> hostPatterns = collectHostPatterns();
            for (FlowApiDO api : published) {
                if (!PublishedApiSnapshot.isWrap(api)) {
                    continue;
                }
                HostBindingConfig binding = HostBindingConfig.parse(PublishedApiSnapshot.resolveHostBinding(api));
                if (!binding.isProbeEnabled()) {
                    HostApiProbeResult skip = HostApiProbeResult.builder()
                            .apiId(api.getId())
                            .status("skip")
                            .path(PublishedApiSnapshot.resolveUrl(api))
                            .method(PublishedApiSnapshot.resolveMethod(api))
                            .message("探活未启用")
                            .checkedAt(LocalDateTime.now(ZONE))
                            .build();
                    cache.put(api.getId(), skip);
                    continue;
                }
                cache.put(api.getId(), probeOne(api, binding, hostPatterns));
            }
        } catch (Exception e) {
            log.warn("[HostApiProbe] 批量探活失败: {}", e.getMessage());
        }
    }

    private HostApiProbeResult probeOne(FlowApiDO api) {
        HostBindingConfig binding = HostBindingConfig.parse(PublishedApiSnapshot.resolveHostBinding(api));
        return probeOne(api, binding, collectHostPatterns());
    }

    private HostApiProbeResult probeOne(FlowApiDO api, HostBindingConfig binding, Set<String> hostPatterns) {
        String method = StrUtil.blankToDefault(PublishedApiSnapshot.resolveMethod(api), "GET").toUpperCase(Locale.ROOT);
        String url = PublishedApiSnapshot.resolveUrl(api);
        String probePath = binding.resolveProbePath(url);
        LocalDateTime now = LocalDateTime.now(ZONE);

        if (!PublishedApiSnapshot.isWrap(api)
                && !ApiInterceptMode.SERVICE_TYPE_HOST.equalsIgnoreCase(PublishedApiSnapshot.resolveServiceType(api))) {
            return HostApiProbeResult.builder()
                    .apiId(api.getId())
                    .status("skip")
                    .path(probePath)
                    .method(method)
                    .message("非 WRAP 资产")
                    .checkedAt(now)
                    .build();
        }
        if (StrUtil.isBlank(probePath)) {
            return HostApiProbeResult.builder()
                    .apiId(api.getId())
                    .status("fail")
                    .path(probePath)
                    .method(method)
                    .message("探活 path 为空")
                    .checkedAt(now)
                    .build();
        }

        boolean found = matchHostRoute(hostPatterns, method, probePath);
        return HostApiProbeResult.builder()
                .apiId(api.getId())
                .status(found ? "ok" : "fail")
                .path(probePath)
                .method(method)
                .message(found ? "宿主已注册该路由" : "宿主未注册该路由（可能已下线或 path 变更）")
                .checkedAt(now)
                .build();
    }

    private Set<String> collectHostPatterns() {
        Set<String> keys = ConcurrentHashMap.newKeySet();
        try {
            Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
            for (RequestMappingInfo info : map.keySet()) {
                Set<String> patterns = info.getPatternValues();
                Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
                if (patterns == null || patterns.isEmpty()) {
                    continue;
                }
                for (String p : patterns) {
                    if (shouldSkipPath(p)) {
                        continue;
                    }
                    if (methods == null || methods.isEmpty()) {
                        keys.add("*-" + normalizePath(p));
                    } else {
                        for (org.springframework.web.bind.annotation.RequestMethod m : methods) {
                            keys.add(m.name() + "-" + normalizePath(p));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[HostApiProbe] 扫描 HandlerMapping 失败: {}", e.getMessage());
        }
        return keys;
    }

    private static boolean matchHostRoute(Set<String> hostPatterns, String method, String path) {
        String norm = normalizePath(path);
        if (hostPatterns.contains(method + "-" + norm) || hostPatterns.contains("*-" + norm)) {
            return true;
        }
        // Ant 风格粗匹配：精确 path 对 {id} 模式
        for (String key : hostPatterns) {
            int dash = key.indexOf('-');
            if (dash <= 0) {
                continue;
            }
            String m = key.substring(0, dash);
            String pat = key.substring(dash + 1);
            if (!"*".equals(m) && !method.equalsIgnoreCase(m)) {
                continue;
            }
            if (antMatch(pat, norm)) {
                return true;
            }
        }
        return false;
    }

    private static boolean antMatch(String pattern, String path) {
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
            if (a.contains("*")) {
                continue;
            }
            if (!a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    static boolean shouldSkipPath(String path) {
        if (StrUtil.isBlank(path)) {
            return true;
        }
        String p = normalizePath(path);
        return p.startsWith("/flow-api")
                || p.startsWith("/flow-ui")
                || p.startsWith("/error")
                || p.startsWith("/actuator");
    }

    static String normalizePath(String path) {
        if (StrUtil.isBlank(path)) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
