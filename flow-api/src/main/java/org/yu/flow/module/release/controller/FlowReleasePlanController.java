package org.yu.flow.module.release.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.yu.flow.module.release.dto.AddOfflineItemsDTO;
import org.yu.flow.module.release.dto.AddReleaseItemsDTO;
import org.yu.flow.module.release.dto.ReleaseCompareDTO;
import org.yu.flow.module.release.dto.ReleaseScanResultDTO;
import org.yu.flow.module.release.dto.ReleaseAssetOptionDTO;
import org.yu.flow.module.release.dto.ReleaseCheckResultDTO;
import org.yu.flow.module.release.dto.ReleaseDTO;
import org.yu.flow.module.release.dto.SaveReleaseDTO;
import org.yu.flow.module.release.service.ReleasePlanService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 版本单：维护一轮上线的资产清单，冻结后导出发布包交给运维。
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/releases")
public class FlowReleasePlanController {

    private static final String VIEW = "flow:release:pkg:view";
    private static final String EDIT = "flow:release:pkg:edit";

    @Resource
    private ReleasePlanService releasePlanService;

    @GetMapping("/page")
    @RequirePerm({VIEW, EDIT})
    public R<PageBean<ReleaseDTO>> page(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(releasePlanService.page(keyword, status, page, size));
    }

    @GetMapping("/drafts")
    @RequirePerm({VIEW, EDIT})
    public R<List<ReleaseDTO>> drafts() {
        return R.ok(releasePlanService.listDrafts());
    }

    @GetMapping("/assets")
    @RequirePerm({VIEW, EDIT})
    public R<List<ReleaseAssetOptionDTO>> searchAssets(@RequestParam String assetType,
                                                       @RequestParam(required = false) String keyword) {
        return R.ok(releasePlanService.searchAssets(assetType, keyword));
    }

    @GetMapping("/{id}")
    @RequirePerm({VIEW, EDIT})
    public R<ReleaseDTO> get(@PathVariable String id) {
        return R.ok(releasePlanService.get(id));
    }

    @PostMapping
    @RequirePerm(EDIT)
    public R<ReleaseDTO> create(@RequestBody SaveReleaseDTO dto) {
        return R.ok(releasePlanService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePerm(EDIT)
    public R<ReleaseDTO> update(@PathVariable String id, @RequestBody SaveReleaseDTO dto) {
        return R.ok(releasePlanService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm(EDIT)
    public R<Void> delete(@PathVariable String id) {
        releasePlanService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/items")
    @RequirePerm(EDIT)
    public R<ReleaseDTO> addItems(@PathVariable String id, @RequestBody AddReleaseItemsDTO dto) {
        return R.ok(releasePlanService.addItems(id, dto));
    }

    @PostMapping("/{id}/offline-items")
    @RequirePerm(EDIT)
    public R<ReleaseDTO> addOfflineItems(@PathVariable String id, @RequestBody AddOfflineItemsDTO dto) {
        return R.ok(releasePlanService.addOfflineItems(id, dto));
    }

    /** 扫描自基线（最近已导出版本）或指定时间以来的变化 */
    @GetMapping("/{id}/scan")
    @RequirePerm({VIEW, EDIT})
    public R<ReleaseScanResultDTO> scan(@PathVariable String id, @RequestParam(required = false) String since) {
        return R.ok(releasePlanService.scanChanges(id, since));
    }

    @GetMapping("/compare")
    @RequirePerm({VIEW, EDIT})
    public R<ReleaseCompareDTO> compare(@RequestParam String baseId, @RequestParam String targetId) {
        return R.ok(releasePlanService.compare(baseId, targetId));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @RequirePerm(EDIT)
    public R<ReleaseDTO> removeItem(@PathVariable String id, @PathVariable String itemId) {
        return R.ok(releasePlanService.removeItem(id, itemId));
    }

    @GetMapping("/{id}/check")
    @RequirePerm({VIEW, EDIT})
    public R<ReleaseCheckResultDTO> check(@PathVariable String id) {
        return R.ok(releasePlanService.check(id));
    }

    @PostMapping("/{id}/freeze")
    @RequirePerm(EDIT)
    public R<ReleaseCheckResultDTO> freeze(@PathVariable String id) {
        return R.ok(releasePlanService.freeze(id));
    }

    @PostMapping("/{id}/unfreeze")
    @RequirePerm(EDIT)
    public R<ReleaseDTO> unfreeze(@PathVariable String id) {
        return R.ok(releasePlanService.unfreeze(id));
    }

    /** 生成并下载发布包；会把版本单标记为已导出，因此用 POST */
    @PostMapping("/{id}/export")
    @RequirePerm(EDIT)
    public void export(@PathVariable String id, HttpServletResponse response) throws IOException {
        ReleasePlanService.PackageFile file = releasePlanService.exportPackage(id);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setContentLength(file.content().length);
        response.getOutputStream().write(file.content());
        response.flushBuffer();
    }
}
