package org.yu.flow.module.oss.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostAuthSupport;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostDomains;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.dto.OssObjectDTO;
import org.yu.flow.module.oss.dto.OssObjectRefBindRequest;
import org.yu.flow.module.oss.dto.OssObjectRefDTO;
import org.yu.flow.module.oss.dto.OssPackDownloadRequest;
import org.yu.flow.module.oss.dto.OssPresignUrlDTO;
import org.yu.flow.module.oss.query.OssObjectQueryDTO;
import org.yu.flow.module.oss.service.OssObjectRefService;
import org.yu.flow.module.oss.service.OssObjectService;
import org.yu.flow.module.oss.service.OssThumbnailService;

import java.util.List;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/oss/objects")
public class OssObjectController {

    @Resource
    private OssObjectService ossObjectService;

    @Resource
    private OssObjectRefService ossObjectRefService;

    @Resource
    private FlowHostAuthSupport flowHostAuthSupport;

    @Resource
    private OssThumbnailService ossThumbnailService;

    @GetMapping("/page")
    public R<PageBean<OssObjectDTO>> getPage(OssObjectQueryDTO queryDTO, HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        return R.ok(ossObjectService.findPage(queryDTO, scope, principal));
    }

    @GetMapping("/{id}")
    public R<OssObjectDTO> getById(@PathVariable String id, HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        return R.ok(ossObjectService.findById(id, scope, principal));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id,
                          @RequestParam(value = "force", defaultValue = "false") boolean force,
                          HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        ossObjectService.delete(id, force, scope, principal);
        return R.ok();
    }

    @PostMapping("/{id}/refs")
    public R<OssObjectRefDTO> bindRef(@PathVariable String id,
                                      @RequestBody OssObjectRefBindRequest body,
                                      HttpServletRequest request) {
        flowHostAuthSupport.requirePrincipal(request);
        return R.ok(ossObjectRefService.bind(id,
                body != null ? body.getBizType() : null,
                body != null ? body.getBizId() : null));
    }

    @DeleteMapping("/{id}/refs")
    public R<Void> unbindRef(@PathVariable String id,
                             @RequestParam String bizType,
                             @RequestParam String bizId,
                             HttpServletRequest request) {
        flowHostAuthSupport.requirePrincipal(request);
        ossObjectRefService.unbind(id, bizType, bizId);
        return R.ok();
    }

    @GetMapping("/{id}/refs")
    public R<List<OssObjectRefDTO>> listRefs(@PathVariable String id, HttpServletRequest request) {
        flowHostAuthSupport.requirePrincipal(request);
        return R.ok(ossObjectRefService.listByObjectId(id));
    }

    @GetMapping("/{id}/content")
    public void downloadContent(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        ossObjectService.downloadOrPresign(id, principal, scope, request, response);
    }

    @GetMapping("/{id}/thumbnail")
    public void downloadThumbnail(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        ossThumbnailService.streamThumbnail(id, principal, scope, response);
    }

    @PostMapping("/{id}/thumbnail/rebuild")
    public R<Void> rebuildThumbnail(@PathVariable String id, HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        ossThumbnailService.rebuild(id, principal, scope);
        return R.ok();
    }

    @GetMapping("/{id}/presign")
    public R<OssPresignUrlDTO> presign(@PathVariable String id, HttpServletRequest request) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        return R.ok(ossObjectService.presignUrl(id, principal, scope, request));
    }

    @PostMapping("/pack")
    public void pack(@RequestBody OssPackDownloadRequest body, HttpServletRequest request,
                     HttpServletResponse response) {
        FlowHostPrincipal principal = flowHostAuthSupport.requirePrincipal(request);
        FlowHostDataScope scope = flowHostAuthSupport.resolveScope(request, FlowHostDomains.OSS_OBJECT);
        ossObjectService.packDownload(body != null ? body.getIds() : null, principal, scope, request, response);
    }
}
