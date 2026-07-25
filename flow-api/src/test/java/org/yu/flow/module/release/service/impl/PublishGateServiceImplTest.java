package org.yu.flow.module.release.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.release.domain.FlowEnvDO;
import org.yu.flow.module.release.domain.FlowRegressionRunDO;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;
import org.yu.flow.module.release.dto.PublishGateCheckItemDTO;
import org.yu.flow.module.release.dto.PublishGateResultDTO;
import org.yu.flow.module.release.repository.FlowEnvRepository;
import org.yu.flow.module.release.repository.FlowRegressionRunRepository;
import org.yu.flow.module.release.repository.FlowRegressionSuiteRepository;
import org.yu.flow.module.release.support.PublishGateException;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.repository.FlowTaskRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 发布门禁 {@link PublishGateServiceImpl} 单测：
 * 覆盖四类检查项（ASSET_EXISTS / ENV_ENABLED / REGRESSION_PASS / INGRESS_AUTH_NONE）
 * 的拒绝与放行组合，以及 assertCanPublish 的阻断异常与审计。
 */
@DisplayName("PublishGateService 发布门禁")
@ExtendWith(MockitoExtension.class)
class PublishGateServiceImplTest {

    @Mock
    private FlowEnvRepository flowEnvRepository;
    @Mock
    private FlowRegressionSuiteRepository suiteRepository;
    @Mock
    private FlowRegressionRunRepository runRepository;
    @Mock
    private FlowApiRepository flowApiRepository;
    @Mock
    private FlowTaskRepository flowTaskRepository;
    @Mock
    private FlowServiceFlowRepository flowServiceFlowRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PublishGateServiceImpl service;

    private YuFlowProperties props;

    private static final String ASSET_ID = "api-1";

    @BeforeEach
    void setUp() {
        // yuFlowProperties 是 @Resource 字段注入，用真实实例便于开关 allowIngressAuthNone
        props = new YuFlowProperties();
        ReflectionTestUtils.setField(service, "yuFlowProperties", props);
    }

    // ==================== 辅助 ====================

    private FlowEnvDO env(int enabled, int requireSuitePass) {
        return FlowEnvDO.builder()
                .code("DEV").name("开发环境")
                .enabled(enabled)
                .requireSuitePass(requireSuitePass)
                .passTtlHours(24)
                .sortOrder(0)
                .build();
    }

    private FlowApiDO api(String securityConfig) {
        FlowApiDO api = new FlowApiDO();
        api.setId(ASSET_ID);
        api.setSecurityConfig(securityConfig);
        return api;
    }

    private PublishGateCheckItemDTO item(PublishGateResultDTO result, String code) {
        return result.getChecks().stream()
                .filter(c -> code.equals(c.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少检查项: " + code));
    }

    // ==================== check() 各检查项 ====================

    @Test
    @DisplayName("assetId 为空：直接拒绝，不触发任何仓库查询")
    void blankAssetId_rejectedFast() {
        PublishGateResultDTO result = service.check("API", "  ", "DEV");

        assertFalse(result.isPassed());
        assertEquals("assetId 不能为空", result.getMessage());
        verifyNoInteractions(flowEnvRepository, flowApiRepository);
    }

    @Test
    @DisplayName("资产不存在：ASSET_EXISTS FAIL，整体不通过")
    void assetNotExists_gateFails() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(false);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 0)));
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.empty());

        PublishGateResultDTO result = service.check("API", ASSET_ID, "DEV");

        assertFalse(result.isPassed());
        PublishGateCheckItemDTO check = item(result, "ASSET_EXISTS");
        assertEquals("FAIL", check.getStatus());
        assertEquals("资产不存在", check.getMessage());
    }

    @Test
    @DisplayName("环境不存在：ENV_ENABLED FAIL（回归检查因无环境配置而 SKIP）")
    void envMissing_gateFails() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("PROD")).thenReturn(Optional.empty());
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.of(api(null)));

        PublishGateResultDTO result = service.check("api", ASSET_ID, "prod");

        assertFalse(result.isPassed());
        assertEquals("FAIL", item(result, "ENV_ENABLED").getStatus());
        assertEquals("SKIP", item(result, "REGRESSION_PASS").getStatus());
        // 入参大小写被规范化
        assertEquals("API", result.getAssetType());
        assertEquals("PROD", result.getEnvCode());
    }

    @Test
    @DisplayName("环境已停用：ENV_ENABLED FAIL")
    void envDisabled_gateFails() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(0, 0)));
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.of(api(null)));

        PublishGateResultDTO result = service.check("API", ASSET_ID, "DEV");

        assertFalse(result.isPassed());
        assertEquals("FAIL", item(result, "ENV_ENABLED").getStatus());
    }

    @Test
    @DisplayName("环境要求回归但未配置启用套件：REGRESSION_PASS FAIL")
    void requireSuite_noSuite_gateFails() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 1)));
        when(suiteRepository.findByAssetTypeAndAssetIdAndEnabled("API", ASSET_ID, 1))
                .thenReturn(Collections.emptyList());
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.of(api(null)));

        PublishGateResultDTO result = service.check("API", ASSET_ID, "DEV");

        assertFalse(result.isPassed());
        PublishGateCheckItemDTO check = item(result, "REGRESSION_PASS");
        assertEquals("FAIL", check.getStatus());
        assertTrue(check.getMessage().contains("未配置启用中的套件"));
    }

    @Test
    @DisplayName("环境要求回归但 TTL 内无 PASSED 记录：REGRESSION_PASS FAIL")
    void requireSuite_noRecentPass_gateFails() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 1)));
        when(suiteRepository.findByAssetTypeAndAssetIdAndEnabled("API", ASSET_ID, 1))
                .thenReturn(List.of(FlowRegressionSuiteDO.builder().build()));
        when(runRepository.findFirstByAssetTypeAndAssetIdAndEnvCodeAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                eq("API"), eq(ASSET_ID), eq("DEV"), eq("PASSED"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.of(api(null)));

        PublishGateResultDTO result = service.check("API", ASSET_ID, "DEV");

        assertFalse(result.isPassed());
        PublishGateCheckItemDTO check = item(result, "REGRESSION_PASS");
        assertEquals("FAIL", check.getStatus());
        assertTrue(check.getMessage().contains("PASSED 回归"));
    }

    @Test
    @DisplayName("环境要求回归且 TTL 内有 PASSED：整体通过")
    void requireSuite_recentPass_gatePasses() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 1)));
        when(suiteRepository.findByAssetTypeAndAssetIdAndEnabled("API", ASSET_ID, 1))
                .thenReturn(List.of(FlowRegressionSuiteDO.builder().build()));
        when(runRepository.findFirstByAssetTypeAndAssetIdAndEnvCodeAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                eq("API"), eq(ASSET_ID), eq("DEV"), eq("PASSED"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(FlowRegressionRunDO.builder()
                        .finishedAt(LocalDateTime.now().minusHours(1)).build()));
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.of(api(null)));

        PublishGateResultDTO result = service.check("API", ASSET_ID, "DEV");

        assertTrue(result.isPassed());
        assertEquals("PASS", item(result, "REGRESSION_PASS").getStatus());
        assertEquals("PASS", item(result, "INGRESS_AUTH_NONE").getStatus());
    }

    @Test
    @DisplayName("接口 authMode=NONE 且未放开：INGRESS_AUTH_NONE FAIL")
    void authModeNone_forbidden_gateFails() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 0)));
        when(flowApiRepository.findById(ASSET_ID))
                .thenReturn(Optional.of(api("{\"authMode\":\"NONE\"}")));

        PublishGateResultDTO result = service.check("API", ASSET_ID, "DEV");

        assertFalse(result.isPassed());
        PublishGateCheckItemDTO check = item(result, "INGRESS_AUTH_NONE");
        assertEquals("FAIL", check.getStatus());
        assertTrue(check.getMessage().contains("authMode=NONE"));
    }

    @Test
    @DisplayName("接口 authMode=NONE 但已放开：仅 WARN，整体通过")
    void authModeNone_allowed_gateWarnsButPasses() {
        props.getSecurity().setAllowIngressAuthNone(true);
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 0)));
        when(flowApiRepository.findById(ASSET_ID))
                .thenReturn(Optional.of(api("{\"authMode\":\"NONE\"}")));

        PublishGateResultDTO result = service.check("API", ASSET_ID, "DEV");

        assertTrue(result.isPassed());
        assertEquals("WARN", item(result, "INGRESS_AUTH_NONE").getStatus());
    }

    @Test
    @DisplayName("TASK 资产：走 taskRepository 且不做入站鉴权检查")
    void taskAsset_noIngressCheck() {
        when(flowTaskRepository.existsById("task-1")).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 0)));

        PublishGateResultDTO result = service.check("TASK", "task-1", "DEV");

        assertTrue(result.isPassed());
        assertTrue(result.getChecks().stream()
                .noneMatch(c -> "INGRESS_AUTH_NONE".equals(c.getCode())));
        verifyNoInteractions(flowApiRepository, flowServiceFlowRepository);
    }

    // ==================== assertCanPublish ====================

    @Test
    @DisplayName("assertCanPublish 未通过：抛 PublishGateException 并记审计")
    void assertCanPublish_blocked_throwsAndAudits() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(false);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 0)));
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.empty());

        PublishGateException ex = assertThrows(PublishGateException.class,
                () -> service.assertCanPublish("API", ASSET_ID, "DEV"));

        assertEquals("PUBLISH_GATE_BLOCKED", ex.getErrorCode());
        assertNotNull(ex.getResult());
        assertFalse(ex.getResult().isPassed());
        verify(auditLogService).record(eq("PUBLISH_GATE_BLOCKED"), eq("API"), eq(ASSET_ID), anyString());
    }

    @Test
    @DisplayName("assertCanPublish 通过：不抛异常也不记审计")
    void assertCanPublish_passed_noException() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(true);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 0)));
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.of(api(null)));

        assertDoesNotThrow(() -> service.assertCanPublish("API", ASSET_ID, "DEV"));
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("审计写入失败不影响门禁异常抛出")
    void auditFailure_doesNotSwallowGateException() {
        when(flowApiRepository.existsById(ASSET_ID)).thenReturn(false);
        when(flowEnvRepository.findByCode("DEV")).thenReturn(Optional.of(env(1, 0)));
        when(flowApiRepository.findById(ASSET_ID)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("audit down"))
                .when(auditLogService).record(anyString(), anyString(), anyString(), anyString());

        assertThrows(PublishGateException.class,
                () -> service.assertCanPublish("API", ASSET_ID, "DEV"));
    }
}
