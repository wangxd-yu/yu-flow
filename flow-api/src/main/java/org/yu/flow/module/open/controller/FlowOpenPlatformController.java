package org.yu.flow.module.open.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.log.open.dto.FlowOpenCallLogListDTO;
import org.yu.flow.log.open.query.FlowOpenCallLogQueryDTO;
import org.yu.flow.log.open.service.FlowOpenCallLogService;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.dto.FlowOpenCredentialDTO;
import org.yu.flow.module.open.dto.FlowOpenPlatformDTO;
import org.yu.flow.module.open.dto.OpenGrantItemDTO;
import org.yu.flow.module.open.dto.OpenGrantUpdateDTO;
import org.yu.flow.module.open.query.FlowOpenPlatformQueryDTO;
import org.yu.flow.module.open.service.FlowOpenPlatformService;
import org.yu.flow.module.open.service.OpenPlatformDocService;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/open-platforms")
@RequirePerm({"flow:open:view", "flow:open:write"})
public class FlowOpenPlatformController {

    @Resource
    private FlowOpenPlatformService flowOpenPlatformService;
    @Resource
    private OpenPlatformDocService openPlatformDocService;
    @Resource
    private FlowOpenCallLogService flowOpenCallLogService;
    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;

    @PostMapping
    @RequirePerm("flow:open:write")
    public R<FlowOpenPlatformDO> create(@RequestBody FlowOpenPlatformDO body) {
        return R.ok(flowOpenPlatformService.save(body));
    }

    @PutMapping("/{id}")
    @RequirePerm("flow:open:write")
    public R<FlowOpenPlatformDO> update(@PathVariable String id, @RequestBody FlowOpenPlatformDO body) {
        body.setId(id);
        return R.ok(flowOpenPlatformService.update(body));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("flow:open:write")
    public R<Void> delete(@PathVariable String id) {
        flowOpenPlatformService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<FlowOpenPlatformDTO> get(@PathVariable String id) {
        return R.ok(flowOpenPlatformService.getById(id));
    }

    @GetMapping("/page")
    public R<PageBean<FlowOpenPlatformDTO>> page(FlowOpenPlatformQueryDTO query) {
        return R.ok(flowOpenPlatformService.page(query));
    }

    @PostMapping("/{id}/credentials")
    @RequirePerm("flow:open:write")
    public R<FlowOpenCredentialDTO> createCredential(@PathVariable String id) {
        return R.ok(flowOpenPlatformService.createCredential(id));
    }

    @PostMapping("/{id}/credentials/{cid}/rotate")
    @RequirePerm("flow:open:write")
    public R<FlowOpenCredentialDTO> rotate(@PathVariable String id, @PathVariable String cid) {
        return R.ok(flowOpenPlatformService.rotateCredential(id, cid));
    }

    @PutMapping("/{id}/credentials/{cid}/disable")
    @RequirePerm("flow:open:write")
    public R<Void> disableCredential(@PathVariable String id, @PathVariable String cid) {
        flowOpenPlatformService.disableCredential(id, cid);
        return R.ok();
    }

    @GetMapping("/{id}/credentials")
    public R<List<FlowOpenCredentialDTO>> listCredentials(@PathVariable String id) {
        return R.ok(flowOpenPlatformService.listCredentials(id));
    }

    @GetMapping("/{id}/grants")
    public R<List<String>> listGrants(@PathVariable String id) {
        return R.ok(flowOpenPlatformService.listGrantedApiIds(id));
    }

    @GetMapping("/{id}/grants/detail")
    public R<List<OpenGrantItemDTO>> listGrantDetails(@PathVariable String id) {
        return R.ok(flowOpenPlatformService.listGrantDetails(id));
    }

    @PostMapping("/{id}/grants/purge-invalid")
    @RequirePerm("flow:open:write")
    public R<Map<String, Object>> purgeInvalidGrants(@PathVariable String id) {
        int removed = flowOpenPlatformService.purgeInvalidGrants(id);
        Map<String, Object> m = new HashMap<>();
        m.put("removed", removed);
        return R.ok(m);
    }

    @PutMapping("/{id}/grants")
    @RequirePerm("flow:open:write")
    public R<Void> replaceGrants(@PathVariable String id, @RequestBody OpenGrantUpdateDTO body) {
        if (body == null) {
            flowOpenPlatformService.replaceGrants(id, null, null);
        } else {
            flowOpenPlatformService.replaceGrants(id, body.getApiIds(), body.getAllowMethodsByApiId());
        }
        return R.ok();
    }

    @GetMapping("/{id}/call-logs/page")
    public R<PageBean<FlowOpenCallLogListDTO>> callLogs(@PathVariable String id, FlowOpenCallLogQueryDTO query) {
        if (query == null) {
            query = new FlowOpenCallLogQueryDTO();
        }
        query.setPlatformId(id);
        Page<FlowOpenCallLogListDTO> page = flowOpenCallLogService.pageList(query);
        return R.ok(new PageBean<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements()));
    }

    @GetMapping("/{id}/openapi")
    public R<Object> openapi(@PathVariable String id, HttpServletRequest request) {
        return R.ok(openPlatformDocService.buildOpenApi(id, request));
    }

    @GetMapping("/{id}/export/openapi.yaml")
    public R<String> openapiYaml(@PathVariable String id, HttpServletRequest request) {
        return R.ok(openPlatformDocService.buildOpenApiYaml(id, request));
    }

    @GetMapping("/{id}/export/postman")
    public R<Object> postman(@PathVariable String id, HttpServletRequest request) {
        return R.ok(openPlatformDocService.buildPostmanCollection(id, request));
    }

    @GetMapping("/{id}/export/markdown")
    public R<String> markdown(@PathVariable String id, HttpServletRequest request) {
        return R.ok(openPlatformDocService.buildMarkdown(id, request));
    }

    @GetMapping("/{id}/export/guide")
    public R<String> guide(@PathVariable String id, HttpServletRequest request) {
        return R.ok(openPlatformDocService.buildIntegrationGuide(id, request));
    }

    @GetMapping("/meta/entry")
    public R<Map<String, Object>> entryMeta() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", yuFlowRuntimeSettings.isOpenEnabled());
        m.put("entryPrefix", yuFlowRuntimeSettings.getOpenEntryPrefix());
        m.put("allowPlainSecret", yuFlowRuntimeSettings.isOpenAllowPlainSecret());
        m.put("allowDirectPath", yuFlowRuntimeSettings.isOpenAllowDirectPath());
        m.put("requireHostAuth", yuFlowRuntimeSettings.isOpenRequireHostAuth());
        m.put("skewSeconds", yuFlowRuntimeSettings.getOpenSkewSeconds());
        m.put("callLogEnabled", yuFlowRuntimeSettings.isOpenCallLogEnabled());
        m.put("includeBodyHash", yuFlowRuntimeSettings.isOpenIncludeBodyHash());
        m.put("rotateGraceHours", yuFlowRuntimeSettings.getOpenRotateGraceHours());
        m.put("nonceFailClosed", yuFlowRuntimeSettings.isOpenNonceFailClosed());
        m.put("ingressEnabled", yuFlowRuntimeSettings.isIngressEnabled());
        return R.ok(m);
    }
}
