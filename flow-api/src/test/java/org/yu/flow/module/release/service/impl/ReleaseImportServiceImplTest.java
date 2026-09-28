package org.yu.flow.module.release.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.service.FlowMqTaskService;
import org.yu.flow.module.release.domain.FlowEnvDO;
import org.yu.flow.module.release.domain.FlowReleaseImportLogDO;
import org.yu.flow.module.release.dto.ReleaseImportLogDTO;
import org.yu.flow.module.release.dto.ReleaseInspectResultDTO;
import org.yu.flow.module.release.repository.FlowEnvRepository;
import org.yu.flow.module.release.repository.FlowReleaseImportLogRepository;
import org.yu.flow.module.release.support.RegressionEvidenceContext;
import org.yu.flow.module.release.support.ReleaseEnvironment;
import org.yu.flow.module.release.support.ReleaseImportBackup;
import org.yu.flow.module.release.support.ReleaseImportBackupManager;
import org.yu.flow.module.release.support.ReleasePackageFormat;
import org.yu.flow.module.release.support.ReleasePackageWriter;
import org.yu.flow.module.release.support.ReleaseSigning;
import org.yu.flow.module.release.support.ReleaseDiffCalculator;
import org.yu.flow.module.release.support.ModelTableInspector;
import org.yu.flow.module.release.support.ReleasePlaceholderCreator;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleasePrivilegedChanges;
import org.yu.flow.module.release.support.ReleaseReferenceChecker;
import org.yu.flow.module.release.support.ReleaseRuntimeVerifier;
import org.yu.flow.module.page.repository.PageInfoRepository;
import org.yu.flow.module.alert.repository.AlertRuleRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowService;
import org.yu.flow.module.task.service.FlowTaskService;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;
import org.yu.flow.module.transfer.service.AssetTransferService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleaseImportServiceImplTest {

    @Mock private AssetTransferService assetTransferService;
    @Mock private ReleaseImportBackupManager backupManager;
    @Mock private FlowReleaseImportLogRepository importLogRepository;
    @Mock private FlowEnvRepository flowEnvRepository;
    @Mock private ReleaseEnvironment releaseEnvironment;
    @Mock private FlowApiCrudService flowApiCrudService;
    @Mock private FlowServiceFlowService flowServiceFlowService;
    @Mock private FlowTaskService flowTaskService;
    @Mock private FlowMqTaskService flowMqTaskService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private DemoModeGuard demoModeGuard;
    @Mock private AuditLogService auditLogService;
    @Mock private ReleaseSigning releaseSigning;
    @Mock private ReleaseDiffCalculator diffCalculator;
    @Mock private ModelTableInspector modelTableInspector;
    @Mock private ReleasePlaceholderCreator placeholderCreator;
    @Mock private ReleaseAssetResolver assetResolver;
    @Mock private PageInfoRepository pageInfoRepository;
    @Mock private AlertRuleRepository alertRuleRepository;
    @Mock private FlowOpenPlatformRepository flowOpenPlatformRepository;
    @Mock private OpenPlatformCache openPlatformCache;
    @Mock private ReleasePrivilegedChanges privilegedChanges;
    @Mock private ReleaseReferenceChecker referenceChecker;
    @Mock private ReleaseRuntimeVerifier runtimeVerifier;

    @InjectMocks
    private ReleaseImportServiceImpl service;

    private final List<FlowReleaseImportLogDO> savedLogs = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(service, "yuFlowProperties", new YuFlowProperties());
        when(releaseEnvironment.current()).thenReturn("PROD");
        when(flowEnvRepository.findByCode("PROD")).thenReturn(Optional.of(FlowEnvDO.builder()
                .code("PROD").name("生产").enabled(1).requireSuitePass(1).passTtlHours(24).sortOrder(0).build()));
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(transactionTemplate.getTransactionManager()).thenReturn(mock(PlatformTransactionManager.class));
        when(assetTransferService.preflight(any(), eq(true))).thenReturn(new TransferReportDTO());
        when(assetTransferService.importBundleAuthorized(any(), eq(true))).thenReturn(new TransferReportDTO());
        when(backupManager.snapshot(any(), any())).thenReturn(new ReleaseImportBackup());
        when(releaseSigning.status(any(), any())).thenReturn(ReleaseSigning.DISABLED);
        when(importLogRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(importLogRepository.findFirstByPackageDigestAndStatusOrderByImportedTimeDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(importLogRepository.save(any())).thenAnswer(inv -> {
            FlowReleaseImportLogDO row = inv.getArgument(0);
            savedLogs.add(row);
            return row;
        });
    }

    private static byte[] pkg(boolean withEvidence) throws Exception {
        return pkg(withEvidence ? LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusHours(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
    }

    private static byte[] pkg(String regressionPassedAt) throws Exception {
        ReleasePackageFormat.ReleaseInfo info = new ReleasePackageFormat.ReleaseInfo();
        info.setCode("v2026.10");
        ReleasePackageFormat.ReleaseEntry entry = new ReleasePackageFormat.ReleaseEntry();
        entry.setAssetType("API");
        entry.setAssetId("api-1");
        entry.setAssetName("下单");
        if (regressionPassedAt != null) {
            entry.setRegressionEnv("DEV");
            entry.setRegressionPassedAt(regressionPassedAt);
        }
        info.getItems().add(entry);

        AssetBundle bundle = new AssetBundle();
        bundle.setKind(AssetBundle.KIND);
        bundle.setSchemaVersion(AssetBundle.SCHEMA_VERSION);
        bundle.setSourceEnv("DEV");
        FlowApiDO api = new FlowApiDO();
        api.setId("api-1");
        api.setName("下单");
        bundle.getApis().add(api);
        FlowMqTaskDO mq = new FlowMqTaskDO();
        mq.setId("mq-1");
        mq.setName("订单消息");
        bundle.getMqTasks().add(mq);
        return ReleasePackageWriter.write(info, bundle).bytes();
    }

    @Test
    void confirmCodeMustMatch() throws Exception {
        FlowException ex = assertThrows(FlowException.class, () -> service.execute(pkg(true), "v2026.09"));
        assertEquals("RELEASE_CONFIRM_MISMATCH", ex.getErrorCode());
        verifyNoInteractions(assetTransferService);
    }

    @Test
    void inspectBlocksWithoutRegressionEvidenceOrMissingRequirement() throws Exception {
        TransferReportDTO report = new TransferReportDTO();
        TransferRequirementDTO req = new TransferRequirementDTO();
        req.setKind(TransferRequirementDTO.KIND_ENV_VAR);
        req.setKey("PAY_URL");
        req.setSatisfied(false);
        report.getRequirements().add(req);
        when(assetTransferService.preflight(any(), eq(true))).thenReturn(report);

        ReleaseInspectResultDTO result = service.inspect(pkg(false));

        assertTrue(result.isBlocked());
        assertEquals(2, result.getBlockReasons().size());
        assertTrue(result.getBlockReasons().get(0).contains("PAY_URL"));
        assertFalse(result.getGates().get(0).isPassed());
    }

    @Test
    void executePublishesEverythingWithEvidenceAndRecordsSuccess() throws Exception {
        List<String> evidenceSeen = new ArrayList<>();
        when(flowApiCrudService.publish(eq("api-1"), eq("PROD"))).thenAnswer(inv -> {
            evidenceSeen.add(RegressionEvidenceContext.find("API", "api-1"));
            return null;
        });

        ReleaseImportLogDTO result = service.execute(pkg("2026-09-27 10:00:00"), " v2026.10 ");

        assertEquals(FlowReleaseImportLogDO.STATUS_SUCCESS, result.getStatus());
        assertEquals(List.of("DEV 2026-09-27 10:00:00"), evidenceSeen);
        verify(flowMqTaskService).publish("mq-1");
        verify(backupManager).snapshot(any(), any());
        assertNotNull(savedLogs.get(0).getBackupJson());
        assertNull(RegressionEvidenceContext.find("API", "api-1"));
    }

    @Test
    void publishFailureIsRecordedAndRethrown() throws Exception {
        when(flowApiCrudService.publish(any(), any())).thenThrow(new RuntimeException("路径冲突"));

        assertThrows(RuntimeException.class, () -> service.execute(pkg(true), "v2026.10"));

        ArgumentCaptor<FlowReleaseImportLogDO> saved = ArgumentCaptor.forClass(FlowReleaseImportLogDO.class);
        verify(importLogRepository).save(saved.capture());
        assertEquals(FlowReleaseImportLogDO.STATUS_FAILED, saved.getValue().getStatus());
        assertTrue(saved.getValue().getErrorMessage().contains("路径冲突"));
        verify(flowMqTaskService, never()).publish(any());
    }

    @Test
    void privilegedChangesNeedExplicitConfirmation() throws Exception {
        when(privilegedChanges.collect(any())).thenReturn(List.of(new ReleaseInspectResultDTO.PrivilegedChange(
                "SYS_MACRO", "GET_SECRET", "读配置", "新增全局宏，表达式：@environment.getProperty('x')")));

        FlowException ex = assertThrows(FlowException.class, () -> service.execute(pkg(true), "v2026.10", false));

        assertEquals("RELEASE_PRIVILEGED_UNCONFIRMED", ex.getErrorCode());
        verify(assetTransferService, never()).importBundleAuthorized(any(), anyBoolean());
        assertEquals(FlowReleaseImportLogDO.STATUS_SUCCESS, service.execute(pkg(true), "v2026.10", true).getStatus());
    }

    @Test
    void unsignedPackageBlockedWhereSignatureRequired() throws Exception {
        when(releaseSigning.required()).thenReturn(true);

        ReleaseInspectResultDTO result = service.inspect(pkg(true));

        assertTrue(result.isBlocked());
        assertTrue(result.isSignatureRequired());
        assertTrue(result.getBlockReasons().stream().anyMatch(r -> r.contains("签名")));
    }

    @Test
    void staleEvidenceOnlyWarns() throws Exception {
        assertTrue(service.inspect(pkg(true)).getWarnings().stream().noneMatch(w -> w.contains("时效")));

        ReleaseInspectResultDTO result = service.inspect(pkg("2020-01-01 00:00:00"));

        assertFalse(result.isBlocked());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("时效")));
    }

    @Test
    void runtimeIssuesAreRecordedAndOldBackupsPruned() throws Exception {
        when(runtimeVerifier.verify(any())).thenReturn(List.of("MQ 任务「订单消息」已启用但订阅没有成功"));
        when(importLogRepository.findIdsWithBackup()).thenReturn(new ArrayList<>(
                java.util.stream.IntStream.range(0, 12).mapToObj(i -> "log-" + i).toList()));
        doAnswer(inv -> {
            inv.<java.util.function.Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ReleaseImportLogDTO result = service.execute(pkg(true), "v2026.10");

        assertEquals(List.of("MQ 任务「订单消息」已启用但订阅没有成功"), result.getRuntimeIssues());
        verify(importLogRepository).clearBackups(List.of("log-10", "log-11"));
    }

    @Test
    void rollbackOnlyForLatestSuccessfulImport() {
        FlowReleaseImportLogDO row = FlowReleaseImportLogDO.builder().id("log-1").status("SUCCESS")
                .backupJson("{}").importedTime(LocalDateTime.now().minusHours(1)).build();
        when(importLogRepository.findById("log-1")).thenReturn(Optional.of(row));
        when(importLogRepository.existsByStatusAndImportedTimeAfter(eq("SUCCESS"), any())).thenReturn(true);

        FlowException ex = assertThrows(FlowException.class, () -> service.rollback("log-1"));

        assertEquals("RELEASE_ROLLBACK_DENIED", ex.getErrorCode());
        verify(backupManager, never()).restore(any(), any());
    }
}
