package org.yu.flow.module.release.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.yu.flow.module.release.dto.ReleaseImportLogDTO;
import org.yu.flow.module.release.dto.ReleaseInspectResultDTO;
import org.yu.flow.module.release.service.ReleaseImportService;
import org.yu.flow.module.release.support.ReleaseDiffCalculator;
import org.yu.flow.module.release.support.ReleasePackageReader;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 目标环境导入发布包（导入即发布）与导入记录 / 回滚。
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/release-imports")
public class FlowReleaseImportController {

    private static final String IMPORT = "flow:release:import";
    private static final String ROLLBACK = "flow:release:rollback";

    @Resource
    private ReleaseImportService releaseImportService;

    @PostMapping("/inspect")
    @RequirePerm(IMPORT)
    public R<ReleaseInspectResultDTO> inspect(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(releaseImportService.inspect(read(file)));
    }

    @PostMapping("/execute")
    @RequirePerm(IMPORT)
    public R<ReleaseImportLogDTO> execute(@RequestParam("file") MultipartFile file,
                                          @RequestParam("confirmCode") String confirmCode,
                                          @RequestParam(value = "privilegedConfirmed", defaultValue = "false")
                                          boolean privilegedConfirmed) throws IOException {
        return R.ok(releaseImportService.execute(read(file), confirmCode, privilegedConfirmed));
    }

    @GetMapping("/page")
    @RequirePerm({IMPORT, ROLLBACK, "flow:release:pkg:view"})
    public R<PageBean<ReleaseImportLogDTO>> page(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return R.ok(releaseImportService.page(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @RequirePerm({IMPORT, ROLLBACK, "flow:release:pkg:view"})
    public R<ReleaseImportLogDTO> get(@PathVariable String id) {
        return R.ok(releaseImportService.get(id));
    }

    @PostMapping("/{id}/rollback")
    @RequirePerm(ROLLBACK)
    public R<ReleaseImportLogDTO> rollback(@PathVariable String id) {
        return R.ok(releaseImportService.rollback(id));
    }

    /** 为缺失依赖创建停用状态的占位（数据源 / MQ / OSS / 告警通道 / 环境变量） */
    @PostMapping("/placeholders")
    @RequirePerm(IMPORT)
    public R<Boolean> createPlaceholder(@RequestBody PlaceholderRequest body) {
        return R.ok(releaseImportService.createPlaceholder(body.kind(), body.key(), body.attributes(), body.remark()));
    }

    /** 预检过的发布包中某资产的逐字段差异 */
    @GetMapping("/diff")
    @RequirePerm(IMPORT)
    public R<List<ReleaseDiffCalculator.FieldDiff>> diff(@RequestParam String digest, @RequestParam String assetType,
                                                        @RequestParam String key) {
        return R.ok(releaseImportService.diff(digest, assetType, key));
    }

    public record PlaceholderRequest(String kind, String key, Map<String, String> attributes, String remark) {
    }

    private static byte[] read(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new FlowException("RELEASE_BAD_PACKAGE", "请选择发布包文件");
        }
        if (file.getSize() > ReleasePackageReader.MAX_PACKAGE_BYTES) {
            throw new FlowException("RELEASE_BAD_PACKAGE", "发布包超过 50MB");
        }
        return file.getBytes();
    }
}
