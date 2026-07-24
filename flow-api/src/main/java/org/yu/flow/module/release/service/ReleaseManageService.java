package org.yu.flow.module.release.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.release.dto.*;

import java.util.List;

public interface ReleaseManageService {

    List<FlowEnvDTO> listEnvs();

    PublishGateResultDTO checkGate(String assetType, String assetId, String envCode);

    PageBean<RegressionSuiteDTO> pageSuites(String assetType, String assetId, int page, int size);

    RegressionSuiteDTO getSuite(String id, boolean withCases);

    RegressionSuiteDTO createSuite(SaveRegressionSuiteDTO dto);

    RegressionSuiteDTO updateSuite(String id, SaveRegressionSuiteDTO dto);

    void deleteSuite(String id);

    RegressionCaseDTO createCase(String suiteId, SaveRegressionCaseDTO dto);

    RegressionCaseDTO updateCase(String caseId, SaveRegressionCaseDTO dto);

    void deleteCase(String caseId);

    RegressionRunDTO runSuite(String suiteId, String envCode);

    /**
     * 按资产批量运行各自启用中的首个套件（定期巡检）。
     */
    BatchRunRegressionResultDTO batchRun(BatchRunRegressionRequestDTO request);

    RegressionRunDTO getRun(String runId);

    PageBean<RegressionRunDTO> pageRuns(String assetType, String assetId, String envCode, int page, int size);
}
