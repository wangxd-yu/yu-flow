package org.yu.flow.module.api.controller;

import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.api.host.HostApiDiscoveryService;
import org.yu.flow.module.api.host.HostApiProbeResult;
import org.yu.flow.module.api.host.HostApiProbeService;
import org.yu.flow.module.api.host.HostApiRouteDTO;
import org.yu.flow.module.api.host.HostApiRouteImportItem;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主 API 发现 / 探活管理端点。
 */
@YuFlowApi
@RestController
@RequestMapping("flow-api/api/host")
@RequirePerm({"flow:api:view", "flow:api:write"})
public class HostApiController {

    @Resource
    private HostApiDiscoveryService hostApiDiscoveryService;

    @Resource
    private HostApiProbeService hostApiProbeService;

    @GetMapping("/routes")
    public R<List<HostApiRouteDTO>> listRoutes() {
        return R.ok(hostApiDiscoveryService.listHostRoutes());
    }

    /**
     * 查询宿主是否已有同 method + path 的接口（用于「同名拦截」提示是否展示）。
     */
    @GetMapping("/routes/exists")
    public R<Map<String, Object>> routeExists(@RequestParam String method,
                                              @RequestParam String path) {
        boolean exists = hostApiDiscoveryService.existsHostRoute(method, path);
        Map<String, Object> res = new HashMap<>();
        res.put("exists", exists);
        return R.ok(res);
    }

    @PostMapping("/routes/import")
    @RequirePerm("flow:api:write")
    public R<Map<String, Object>> importRoutes(@RequestBody Map<String, Object> body) {
        String directoryId = body == null ? null : (String) body.get("directoryId");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rawItems = body == null ? null : (List<Map<String, String>>) body.get("items");
        List<HostApiRouteImportItem> items = new java.util.ArrayList<>();
        if (rawItems != null) {
            for (Map<String, String> m : rawItems) {
                if (m == null) continue;
                HostApiRouteImportItem it = new HostApiRouteImportItem();
                it.setMethod(m.get("method"));
                it.setPath(m.get("path"));
                items.add(it);
            }
        }
        int created = hostApiDiscoveryService.importAsWrapDrafts(items, directoryId);
        Map<String, Object> res = new HashMap<>();
        res.put("created", created);
        return R.ok(res);
    }

    @PostMapping("/probe/batch")
    public R<List<HostApiProbeResult>> probeBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = body == null ? null : (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        return R.ok(hostApiProbeService.batchGet(ids));
    }

    @PostMapping("/probe/{id}")
    @RequirePerm("flow:api:write")
    public R<HostApiProbeResult> probeOne(@PathVariable String id) {
        return R.ok(hostApiProbeService.probeNow(id));
    }

    @PostMapping("/probe/run-all")
    @RequirePerm("flow:api:write")
    public R<Void> probeRunAll() {
        hostApiProbeService.probeAllPublishedWrap();
        return R.ok();
    }
}
