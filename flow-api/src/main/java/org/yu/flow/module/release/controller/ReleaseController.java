package org.yu.flow.module.release.controller;

import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.yu.flow.module.release.dto.*;
import org.yu.flow.module.release.service.ReleaseManageService;

import jakarta.annotation.Resource;
import java.util.List;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/release")
public class ReleaseController {

    @Resource
    private ReleaseManageService releaseManageService;

    @GetMapping("/envs")
    @RequirePerm("flow:release:view")
    public R<List<FlowEnvDTO>> listEnvs() {
        return R.ok(releaseManageService.listEnvs());
    }

    @GetMapping("/gate/check")
    @RequirePerm("flow:release:view")
    public R<PublishGateResultDTO> checkGate(@RequestParam String assetType,
                                             @RequestParam String assetId,
                                             @RequestParam(required = false, defaultValue = "DEV") String envCode) {
        return R.ok(releaseManageService.checkGate(assetType, assetId, envCode));
    }

    @GetMapping("/suites/page")
    @RequirePerm("flow:release:view")
    public R<PageBean<RegressionSuiteDTO>> pageSuites(@RequestParam(required = false) String assetType,
                                                      @RequestParam(required = false) String assetId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(releaseManageService.pageSuites(assetType, assetId, page, size));
    }

    @GetMapping("/suites/{id}")
    @RequirePerm("flow:release:view")
    public R<RegressionSuiteDTO> getSuite(@PathVariable String id,
                                          @RequestParam(defaultValue = "true") boolean withCases) {
        return R.ok(releaseManageService.getSuite(id, withCases));
    }

    @PostMapping("/suites")
    @RequirePerm("flow:release:edit")
    public R<RegressionSuiteDTO> createSuite(@RequestBody SaveRegressionSuiteDTO dto) {
        return R.ok(releaseManageService.createSuite(dto));
    }

    @PutMapping("/suites/{id}")
    @RequirePerm("flow:release:edit")
    public R<RegressionSuiteDTO> updateSuite(@PathVariable String id, @RequestBody SaveRegressionSuiteDTO dto) {
        return R.ok(releaseManageService.updateSuite(id, dto));
    }

    @DeleteMapping("/suites/{id}")
    @RequirePerm("flow:release:edit")
    public R<Void> deleteSuite(@PathVariable String id) {
        releaseManageService.deleteSuite(id);
        return R.ok();
    }

    @PostMapping("/suites/{suiteId}/cases")
    @RequirePerm("flow:release:edit")
    public R<RegressionCaseDTO> createCase(@PathVariable String suiteId, @RequestBody SaveRegressionCaseDTO dto) {
        return R.ok(releaseManageService.createCase(suiteId, dto));
    }

    @PutMapping("/cases/{caseId}")
    @RequirePerm("flow:release:edit")
    public R<RegressionCaseDTO> updateCase(@PathVariable String caseId, @RequestBody SaveRegressionCaseDTO dto) {
        return R.ok(releaseManageService.updateCase(caseId, dto));
    }

    @DeleteMapping("/cases/{caseId}")
    @RequirePerm("flow:release:edit")
    public R<Void> deleteCase(@PathVariable String caseId) {
        releaseManageService.deleteCase(caseId);
        return R.ok();
    }

    @PostMapping("/suites/{suiteId}/run")
    @RequirePerm("flow:release:edit")
    public R<RegressionRunDTO> runSuite(@PathVariable String suiteId,
                                        @RequestBody(required = false) RunRegressionRequestDTO body) {
        String env = body != null ? body.getEnvCode() : "DEV";
        return R.ok(releaseManageService.runSuite(suiteId, env));
    }

    /**
     * 列表批量回归：按资产依次跑启用中的首个套件（单次最多 20 个）。
     */
    @PostMapping("/batch-run")
    @RequirePerm("flow:release:edit")
    public R<BatchRunRegressionResultDTO> batchRun(@RequestBody BatchRunRegressionRequestDTO body) {
        return R.ok(releaseManageService.batchRun(body));
    }

    @GetMapping("/runs/{runId}")
    @RequirePerm("flow:release:view")
    public R<RegressionRunDTO> getRun(@PathVariable String runId) {
        return R.ok(releaseManageService.getRun(runId));
    }

    @GetMapping("/runs/page")
    @RequirePerm("flow:release:view")
    public R<PageBean<RegressionRunDTO>> pageRuns(@RequestParam(required = false) String assetType,
                                                  @RequestParam(required = false) String assetId,
                                                  @RequestParam(required = false) String envCode,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return R.ok(releaseManageService.pageRuns(assetType, assetId, envCode, page, size));
    }
}
