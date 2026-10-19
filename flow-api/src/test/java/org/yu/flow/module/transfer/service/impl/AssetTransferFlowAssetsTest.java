package org.yu.flow.module.transfer.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiExcelTemplateRepository;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.envvar.cache.EnvVariableCacheManager;
import org.yu.flow.module.envvar.repository.SysEnvVariableRepository;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.log.audit.service.AuditLogService;
import org.yu.flow.module.mq.repository.MqConnectionRepository;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.release.repository.FlowRegressionCaseRepository;
import org.yu.flow.module.release.repository.FlowRegressionSuiteRepository;
import org.yu.flow.module.responsetemplate.cache.ResponseTemplateCacheManager;
import org.yu.flow.module.responsetemplate.repository.ResponseTemplateRepository;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.AssetExportRequestDTO;
import org.yu.flow.module.transfer.dto.TransferItemDTO;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.module.transfer.support.ConfigAssetTransfer;
import org.yu.flow.module.transfer.support.RequirementAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 四类可发布编排资产（接口 / 服务 / 定时任务 / MQ 任务）的迁移导入导出行为。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetTransferFlowAssetsTest {

    @Mock private FlowApiRepository flowApiRepository;
    @Mock private FlowServiceFlowRepository flowServiceFlowRepository;
    @Mock private FlowTaskRepository flowTaskRepository;
    @Mock private FlowMqTaskRepository flowMqTaskRepository;
    @Mock private FlowDirectoryRepository flowDirectoryRepository;
    @Mock private FlowDirectoryService flowDirectoryService;
    @Mock private FlowRegressionSuiteRepository flowRegressionSuiteRepository;
    @Mock private FlowRegressionCaseRepository flowRegressionCaseRepository;
    @Mock private DynamicDataSourceService dynamicDataSourceService;
    @Mock private MqConnectionRepository mqConnectionRepository;
    @Mock private OssConnectionRepository ossConnectionRepository;
    @Mock private ResponseTemplateRepository responseTemplateRepository;
    @Mock private SysEnvVariableRepository sysEnvVariableRepository;
    @Mock private FlowApiExcelTemplateRepository flowApiExcelTemplateRepository;
    @Mock private ResponseTemplateCacheManager responseTemplateCacheManager;
    @Mock private ConfigAssetTransfer configAssetTransfer;
    @Mock private RequirementAttributes requirementAttributes;
    @Mock private EnvVariableCacheManager envVariableCacheManager;
    @Mock private DemoModeGuard demoModeGuard;
    @Mock private RbacService rbacService;
    @Mock private FlowReferenceIndex flowReferenceIndex;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private AssetTransferServiceImpl service;

    @BeforeEach
    void setUp() {
        when(dynamicDataSourceService.getAllDataSources()).thenReturn(Map.of());
        when(flowApiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(flowServiceFlowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(flowTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(flowMqTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── 接口 ─────────────────────────────────────────────────────────────

    @Test
    void newApiIsCreatedAsUnpublishedDraft() {
        AssetBundle bundle = bundle();
        FlowApiDO api = api("a1");
        api.setDirectoryId("missing-dir");
        bundle.getApis().add(api);

        TransferItemDTO item = single(service.preflight(bundle, false));
        assertEquals(TransferItemDTO.ACTION_CREATE, item.getAction());
        assertNull(item.getMessage());

        service.importBundleAuthorized(bundle, false);
        FlowApiDO saved = captureSaved(flowApiRepository, FlowApiDO.class);
        assertEquals("a1", saved.getId());
        assertEquals(0, saved.getPublishStatus());
        assertEquals(0, saved.getDeleted());
        assertEquals("/orders", saved.getUrl());
        assertEquals("GET", saved.getMethod());
        assertEquals("dsl-a1", saved.getDslContent());
        assertNull(saved.getDirectoryId(), "目录不存在时落到根目录");
        assertEquals(Boolean.FALSE, saved.getLogEnabled());
    }

    @Test
    void existingApiIsSkippedWithoutOverwrite() {
        when(flowApiRepository.findById("a1")).thenReturn(Optional.of(api("a1")));
        AssetBundle bundle = bundle();
        bundle.getApis().add(api("a1"));

        TransferItemDTO item = single(service.preflight(bundle, false));
        assertEquals(TransferItemDTO.ACTION_SKIP, item.getAction());
        assertEquals("目标环境已存在，未开启覆盖", item.getMessage());
    }

    @Test
    void overwritingApiOnlyTouchesDraft() {
        FlowApiDO existing = api("a1");
        existing.setName("旧名");
        existing.setPublishStatus(1);
        existing.setPublishedSnapshot("{\"name\":\"线上\"}");
        when(flowApiRepository.findById("a1")).thenReturn(Optional.of(existing));
        AssetBundle bundle = bundle();
        FlowApiDO incoming = api("a1");
        incoming.setName("新名");
        bundle.getApis().add(incoming);

        TransferItemDTO item = single(service.preflight(bundle, true));
        assertEquals(TransferItemDTO.ACTION_UPDATE, item.getAction());
        assertEquals("仅覆盖草稿，线上快照不变，需重新发布才生效", item.getMessage());

        service.importBundleAuthorized(bundle, true);
        FlowApiDO saved = captureSaved(flowApiRepository, FlowApiDO.class);
        assertSame(existing, saved);
        assertEquals("新名", saved.getName());
        assertEquals(1, saved.getPublishStatus());
        assertEquals("{\"name\":\"线上\"}", saved.getPublishedSnapshot());
    }

    @Test
    void softDeletedApiIsRestoredWhenOverwriting() {
        FlowApiDO restored = api("a1");
        when(flowApiRepository.findById("a1"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(restored));
        when(flowApiRepository.countAnyById("a1")).thenReturn(1L);
        AssetBundle bundle = bundle();
        bundle.getApis().add(api("a1"));

        TransferReportDTO report = service.importBundleAuthorized(bundle, true);

        TransferItemDTO item = single(report);
        assertEquals(TransferItemDTO.ACTION_UPDATE, item.getAction());
        assertEquals("恢复目标环境已删除的同 ID 接口并覆盖草稿", item.getMessage());
        verify(flowApiRepository, times(1)).restoreDeletedById("a1");
        assertSame(restored, captureSaved(flowApiRepository, FlowApiDO.class));
    }

    @Test
    void softDeletedApiIsSkippedWithoutOverwrite() {
        when(flowApiRepository.countAnyById("a1")).thenReturn(1L);
        AssetBundle bundle = bundle();
        bundle.getApis().add(api("a1"));

        TransferItemDTO item = single(service.preflight(bundle, false));
        assertEquals(TransferItemDTO.ACTION_SKIP, item.getAction());
        assertEquals("目标环境存在同 ID 的已删除接口，未开启覆盖", item.getMessage());
    }

    @Test
    void apiPathTakenByAnotherApiBlocksTheWholeImport() {
        when(flowApiRepository.existsDraftByUrlAndMethod("/orders", "GET", "a1")).thenReturn(true);
        AssetBundle bundle = bundle();
        bundle.getApis().add(api("a1"));

        TransferReportDTO report = service.preflight(bundle, true);
        assertEquals(TransferItemDTO.ACTION_CONFLICT, single(report).getAction());
        assertEquals("目标环境已有其它接口占用 GET /orders", single(report).getMessage());
        assertTrue(report.isBlocked());

        FlowException e = assertThrows(FlowException.class, () -> service.importBundleAuthorized(bundle, true));
        assertEquals("TRANSFER_CONFLICT", e.getErrorCode());
        verify(flowApiRepository, never()).save(any());
    }

    @Test
    void reservedAndIncompleteApisAreNotImported() {
        AssetBundle bundle = bundle();
        bundle.getApis().add(api(HostCatalogReserved.DIR_ROLE));
        FlowApiDO noUrl = api("a2");
        noUrl.setUrl(null);
        bundle.getApis().add(noUrl);
        bundle.getApis().add(api(null));

        List<TransferItemDTO> items = service.preflight(bundle, true).getItems();
        assertEquals(TransferItemDTO.ACTION_SKIP, items.get(0).getAction());
        assertEquals("系统保留接口不参与迁移", items.get(0).getMessage());
        assertEquals(TransferItemDTO.ACTION_CONFLICT, items.get(1).getAction());
        assertEquals("缺少 path 或 method", items.get(1).getMessage());
        assertEquals(TransferItemDTO.ACTION_CONFLICT, items.get(2).getAction());
        assertEquals("缺少 ID，包体不完整", items.get(2).getMessage());
    }

    // ─── 服务 ─────────────────────────────────────────────────────────────

    @Test
    void serviceEnabledFlagComesFromBundleOnlyOnCreate() {
        AssetBundle bundle = bundle();
        FlowServiceFlowDO created = service("s1");
        created.setEnabled(false);
        bundle.getServices().add(created);
        service.importBundleAuthorized(bundle, true);
        FlowServiceFlowDO saved = captureSaved(flowServiceFlowRepository, FlowServiceFlowDO.class);
        assertEquals(Boolean.FALSE, saved.getEnabled());
        assertEquals(0, saved.getPublishStatus());

        FlowServiceFlowDO existing = service("s2");
        existing.setEnabled(true);
        when(flowServiceFlowRepository.findById("s2")).thenReturn(Optional.of(existing));
        AssetBundle update = bundle();
        FlowServiceFlowDO incoming = service("s2");
        incoming.setEnabled(false);
        incoming.setName("新服务名");
        update.getServices().add(incoming);

        assertEquals("仅覆盖草稿，线上快照不变，需重新发布才生效", single(service.preflight(update, true)).getMessage());
        service.importBundleAuthorized(update, true);
        assertEquals(Boolean.TRUE, existing.getEnabled(), "启停是目标环境的运维状态");
        assertEquals("新服务名", existing.getName());
    }

    @Test
    void softDeletedServiceWithoutOverwriteIsSkipped() {
        when(flowServiceFlowRepository.countAnyById("s1")).thenReturn(1L);
        AssetBundle bundle = bundle();
        bundle.getServices().add(service("s1"));

        assertEquals("目标环境存在同 ID 的已删除服务，未开启覆盖", single(service.preflight(bundle, false)).getMessage());
    }

    // ─── 定时任务 ─────────────────────────────────────────────────────────

    @Test
    void taskCronIsValidatedAndTrimmed() {
        AssetBundle bad = bundle();
        FlowTaskDO invalid = task("t1");
        invalid.setCron("not a cron");
        bad.getTasks().add(invalid);
        TransferItemDTO conflict = single(service.preflight(bad, true));
        assertEquals(TransferItemDTO.ACTION_CONFLICT, conflict.getAction());
        assertEquals("Cron 表达式不合法: not a cron", conflict.getMessage());

        AssetBundle good = bundle();
        FlowTaskDO valid = task("t2");
        valid.setCron("  0 0 * * * *  ");
        good.getTasks().add(valid);
        service.importBundleAuthorized(good, true);
        assertEquals("0 0 * * * *", captureSaved(flowTaskRepository, FlowTaskDO.class).getCron());
    }

    @Test
    void taskUpdateAndSoftDeleteMessages() {
        when(flowTaskRepository.findById("t1")).thenReturn(Optional.of(task("t1")));
        AssetBundle bundle = bundle();
        bundle.getTasks().add(task("t1"));
        assertEquals("仅覆盖草稿，线上调度按已发布快照运行，需重新发布才生效",
                single(service.preflight(bundle, true)).getMessage());

        when(flowTaskRepository.countAnyById("t2")).thenReturn(1L);
        AssetBundle deleted = bundle();
        deleted.getTasks().add(task("t2"));
        assertEquals("目标环境存在同 ID 的已删除任务，未开启覆盖", single(service.preflight(deleted, false)).getMessage());
    }

    // ─── MQ 任务 ──────────────────────────────────────────────────────────

    @Test
    void mqTaskNeedsConnectionAndTopic() {
        AssetBundle bundle = bundle();
        FlowMqTaskDO noTopic = mqTask("m1");
        noTopic.setTopic(null);
        bundle.getMqTasks().add(noTopic);

        TransferItemDTO item = single(service.preflight(bundle, true));
        assertEquals(TransferItemDTO.ACTION_CONFLICT, item.getAction());
        assertEquals("缺少连接编码或 topic", item.getMessage());
    }

    @Test
    void mqTaskUpdateAndSoftDeleteMessages() {
        when(flowMqTaskRepository.findById("m1")).thenReturn(Optional.of(mqTask("m1")));
        AssetBundle bundle = bundle();
        bundle.getMqTasks().add(mqTask("m1"));
        assertEquals("仅覆盖草稿，线上订阅按已发布快照运行，需重新发布才生效",
                single(service.preflight(bundle, true)).getMessage());

        when(flowMqTaskRepository.countAnyById("m2")).thenReturn(1L);
        AssetBundle deleted = bundle();
        deleted.getMqTasks().add(mqTask("m2"));
        assertEquals("目标环境存在同 ID 的已删除 MQ 任务，未开启覆盖", single(service.preflight(deleted, false)).getMessage());

        AssetBundle created = bundle();
        FlowMqTaskDO incoming = mqTask("m3");
        incoming.setConsumerGroup("g1");
        created.getMqTasks().add(incoming);
        service.importBundleAuthorized(created, true);
        FlowMqTaskDO saved = captureSaved(flowMqTaskRepository, FlowMqTaskDO.class);
        assertEquals("conn", saved.getConnectionCode());
        assertEquals("topic-a", saved.getTopic());
        assertEquals("g1", saved.getConsumerGroup());
        assertEquals(Boolean.TRUE, saved.getEnabled());
    }

    // ─── 导出 ─────────────────────────────────────────────────────────────

    @Test
    void exportTakesPublishedContentAndClearsPublishState() {
        FlowApiDO row = api("a1");
        row.setName("草稿名");
        row.setDslContent("draft");
        row.setPublishStatus(1);
        row.setPublishedSnapshot("{\"name\":\"线上名\",\"dslContent\":\"live\"}");
        when(flowApiRepository.findById("a1")).thenReturn(Optional.of(row));
        FlowTaskDO task = task("t1");
        task.setPublishStatus(0);
        when(flowTaskRepository.findById("t1")).thenReturn(Optional.of(task));

        AssetExportRequestDTO request = new AssetExportRequestDTO();
        request.setApiIds(List.of("a1", "missing"));
        request.setTaskIds(List.of("t1"));
        request.setIncludeDependencies(false);
        request.setIncludeRegression(false);
        AssetBundle bundle = service.export(request);

        FlowApiDO exported = bundle.getApis().get(0);
        assertEquals("线上名", exported.getName());
        assertEquals("live", exported.getDslContent());
        assertNull(exported.getPublishedSnapshot());
        assertEquals(0, exported.getPublishStatus());
        assertEquals("草稿名", row.getName(), "导出不能改源实体");
        assertEquals("dsl-t1", bundle.getTasks().get(0).getDslContent());
        assertTrue(bundle.getWarnings().contains("接口不存在或已删除，已跳过: missing"));
        assertFalse(bundle.getApis().stream().anyMatch(a -> "missing".equals(a.getId())));
    }

    @Test
    void draftExportKeepsDraftContent() {
        FlowApiDO row = api("a1");
        row.setName("草稿名");
        row.setPublishStatus(1);
        row.setPublishedSnapshot("{\"name\":\"线上名\"}");
        when(flowApiRepository.findById("a1")).thenReturn(Optional.of(row));

        AssetExportRequestDTO request = new AssetExportRequestDTO();
        request.setApiIds(List.of("a1"));
        request.setContentSource(AssetExportRequestDTO.SOURCE_DRAFT);
        request.setIncludeDependencies(false);
        request.setIncludeRegression(false);

        assertEquals("草稿名", service.export(request).getApis().get(0).getName());
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private static AssetBundle bundle() {
        AssetBundle bundle = new AssetBundle();
        bundle.setKind(AssetBundle.KIND);
        bundle.setSchemaVersion(AssetBundle.SCHEMA_VERSION);
        return bundle;
    }

    private static FlowApiDO api(String id) {
        FlowApiDO api = new FlowApiDO();
        api.setId(id);
        api.setName("接口 " + id);
        api.setUrl("/orders");
        api.setMethod("GET");
        api.setDslContent("dsl-" + id);
        return api;
    }

    private static FlowServiceFlowDO service(String id) {
        FlowServiceFlowDO s = new FlowServiceFlowDO();
        s.setId(id);
        s.setName("服务 " + id);
        s.setDslContent("dsl-" + id);
        return s;
    }

    private static FlowTaskDO task(String id) {
        FlowTaskDO t = new FlowTaskDO();
        t.setId(id);
        t.setName("任务 " + id);
        t.setCron("0 0 * * * *");
        t.setDslContent("dsl-" + id);
        return t;
    }

    private static FlowMqTaskDO mqTask(String id) {
        FlowMqTaskDO t = new FlowMqTaskDO();
        t.setId(id);
        t.setName("MQ " + id);
        t.setConnectionCode("conn");
        t.setTopic("topic-a");
        t.setDslContent("dsl-" + id);
        return t;
    }

    private static TransferItemDTO single(TransferReportDTO report) {
        assertEquals(1, report.getItems().size(), String.valueOf(report.getItems()));
        return report.getItems().get(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T captureSaved(Object repository, Class<T> type) {
        ArgumentCaptor<T> captor = ArgumentCaptor.forClass(type);
        if (repository instanceof FlowApiRepository r) {
            verify(r).save((FlowApiDO) captor.capture());
        } else if (repository instanceof FlowServiceFlowRepository r) {
            verify(r).save((FlowServiceFlowDO) captor.capture());
        } else if (repository instanceof FlowTaskRepository r) {
            verify(r).save((FlowTaskDO) captor.capture());
        } else if (repository instanceof FlowMqTaskRepository r) {
            verify(r).save((FlowMqTaskDO) captor.capture());
        }
        return captor.getValue();
    }
}
