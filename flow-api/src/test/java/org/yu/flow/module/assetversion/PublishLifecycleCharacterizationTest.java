package org.yu.flow.module.assetversion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.FlowApiCacheManager;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.api.cache.ApiResponseCacheService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.service.FlowApiCrudServiceImpl;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.service.FlowAssetVersionService;
import org.yu.flow.module.host.HostReservedApiWriteGuard;
import org.yu.flow.module.log.audit.service.AuditLogService;
import org.yu.flow.module.open.cache.OpenPlatformCache;
import org.yu.flow.module.release.service.PublishGateService;
import org.yu.flow.module.release.support.ReleaseEnvironment;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.serviceflow.service.impl.FlowServiceFlowServiceImpl;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.task.scheduler.FlowTaskScheduler;
import org.yu.flow.module.task.service.impl.FlowTaskServiceImpl;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口、服务、定时任务的发布 / 下线 / 回滚草稿 / 回退历史版本的现有行为。
 */
class PublishLifecycleCharacterizationTest {

    private static final String SERVICE_DSL = "{\"nodes\":[{\"id\":\"s\",\"type\":\"service\"}],\"edges\":[]}";
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 1, 1, 8, 0);

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Service {

        @Mock private FlowServiceFlowRepository repository;
        @Mock private DemoModeGuard demoModeGuard;
        @Mock private FlowAssetVersionService versionService;
        @Mock private FlowReferenceIndex referenceIndex;
        @Mock private AuditLogService auditLogService;
        @Mock private PublishGateService publishGateService;
        @Mock private ReleaseEnvironment releaseEnvironment;

        @InjectMocks
        private FlowServiceFlowServiceImpl service;

        @BeforeEach
        void setUp() {
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(releaseEnvironment.resolve(any())).thenReturn("TEST");
        }

        @Test
        void publishFreezesSnapshotAndRecordsEverything() {
            FlowServiceFlowDO s = service("s1");
            when(repository.findById("s1")).thenReturn(Optional.of(s));

            FlowServiceFlowDO saved = service.publish("s1", "TEST");

            verify(demoModeGuard).checkModifyOrDelete("s1", "内部服务");
            verify(publishGateService).assertCanPublish("SERVICE", "s1", "TEST");
            assertEquals(1, saved.getPublishStatus());
            assertNotNull(saved.getPublishedSnapshot());
            assertNotNull(saved.getPublishTime());
            assertEquals(saved.getPublishTime(), saved.getUpdateTime());
            verify(versionService).append(eq(AssetBizType.SERVICE), eq("s1"), eq(saved.getPublishedSnapshot()),
                    eq(AssetBizType.SOURCE_PUBLISH), isNull(), any());
            verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
            verify(auditLogService).record(eq("SERVICE_PUBLISH"), eq("SERVICE"), eq("s1"), contains("\"env\":\"TEST\""));
        }

        @Test
        void publishRejectsEmptyOrInvalidDsl() {
            FlowServiceFlowDO empty = service("s1");
            empty.setDslContent(" ");
            when(repository.findById("s1")).thenReturn(Optional.of(empty));
            assertEquals("服务 DSL 为空，无法发布",
                    assertThrows(RuntimeException.class, () -> service.publish("s1", "DEV")).getMessage());

            FlowServiceFlowDO noEntry = service("s2");
            noEntry.setDslContent("{\"nodes\":[{\"id\":\"a\",\"type\":\"evaluate\"}]}");
            when(repository.findById("s2")).thenReturn(Optional.of(noEntry));
            assertEquals("缺少 Service 入口节点，无法发布",
                    assertThrows(RuntimeException.class, () -> service.publish("s2", "DEV")).getMessage());
            verify(repository, never()).save(any());
        }

        @Test
        void unpublishClearsSnapshot() {
            FlowServiceFlowDO s = published(service("s1"));
            when(repository.findById("s1")).thenReturn(Optional.of(s));

            FlowServiceFlowDO saved = service.unpublish("s1");

            assertEquals(0, saved.getPublishStatus());
            assertNull(saved.getPublishedSnapshot());
            assertNotNull(saved.getUpdateTime());
            verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
        }

        @Test
        void rollbackCopiesSnapshotBackAndAlignsUpdateTime() {
            FlowServiceFlowDO s = published(service("s1"));
            s.setName("草稿名");
            when(repository.findById("s1")).thenReturn(Optional.of(s));

            FlowServiceFlowDO saved = service.rollbackToPublished("s1");

            assertEquals("线上名", saved.getName());
            assertEquals(PUBLISHED_AT, saved.getUpdateTime());

            FlowServiceFlowDO never = service("s2");
            when(repository.findById("s2")).thenReturn(Optional.of(never));
            assertEquals("该服务没有发布快照，无法回滚",
                    assertThrows(RuntimeException.class, () -> service.rollbackToPublished("s2")).getMessage());
        }

        @Test
        void restoreVersionAppliesSnapshotIncludingSwitches() {
            FlowServiceFlowDO s = service("s1");
            s.setEnabled(true);
            when(repository.findById("s1")).thenReturn(Optional.of(s));
            when(versionService.requireOwned("v2", AssetBizType.SERVICE, "s1"))
                    .thenReturn(version(2, "{\"name\":\"v2名\",\"dslContent\":\"x\",\"enabled\":false}"));

            FlowServiceFlowDO saved = service.restoreVersion("s1", "v2");

            assertEquals("v2名", saved.getName());
            assertEquals(Boolean.FALSE, saved.getEnabled());
            assertEquals(1, saved.getPublishStatus());
            verify(versionService).append(eq(AssetBizType.SERVICE), eq("s1"), anyString(),
                    eq(AssetBizType.SOURCE_ROLLBACK), eq("回退至 v2"), any());
            verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
        }

        private FlowServiceFlowDO service(String id) {
            FlowServiceFlowDO s = new FlowServiceFlowDO();
            s.setId(id);
            s.setName("服务 " + id);
            s.setDslContent(SERVICE_DSL);
            return s;
        }

        private FlowServiceFlowDO published(FlowServiceFlowDO s) {
            s.setPublishStatus(1);
            s.setPublishTime(PUBLISHED_AT);
            s.setPublishedSnapshot("{\"name\":\"线上名\",\"dslContent\":\"" + SERVICE_DSL.replace("\"", "\\\"") + "\"}");
            return s;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Task {

        @Mock private FlowTaskRepository repository;
        @Mock private FlowTaskScheduler scheduler;
        @Mock private DemoModeGuard demoModeGuard;
        @Mock private FlowAssetVersionService versionService;
        @Mock private FlowReferenceIndex referenceIndex;
        @Mock private AuditLogService auditLogService;
        @Mock private PublishGateService publishGateService;
        @Mock private ReleaseEnvironment releaseEnvironment;

        @InjectMocks
        private FlowTaskServiceImpl service;

        @BeforeEach
        void setUp() {
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(releaseEnvironment.resolve(any())).thenReturn("TEST");
        }

        @Test
        void publishSchedulesOnlyEnabledTasks() {
            FlowTaskDO enabled = task("t1");
            enabled.setEnabled(true);
            when(repository.findById("t1")).thenReturn(Optional.of(enabled));
            FlowTaskDO saved = service.publish("t1", "TEST");

            verify(publishGateService).assertCanPublish("TASK", "t1", "TEST");
            assertEquals(1, saved.getPublishStatus());
            assertEquals(saved.getPublishTime(), saved.getUpdateTime());
            verify(scheduler).reschedule(saved);
            verify(versionService).append(eq(AssetBizType.TASK), eq("t1"), anyString(),
                    eq(AssetBizType.SOURCE_PUBLISH), isNull(), any());
            verify(auditLogService).record(eq("TASK_PUBLISH"), eq("TASK"), eq("t1"), contains("\"cron\":\"0 0 * * * *\""));

            FlowTaskDO disabled = task("t2");
            disabled.setEnabled(false);
            when(repository.findById("t2")).thenReturn(Optional.of(disabled));
            service.publish("t2", "TEST");
            verify(scheduler, never()).reschedule(disabled);
        }

        @Test
        void publishRejectsBlankDsl() {
            FlowTaskDO t = task("t1");
            t.setDslContent(null);
            when(repository.findById("t1")).thenReturn(Optional.of(t));
            assertEquals("任务 DSL 为空，无法发布",
                    assertThrows(RuntimeException.class, () -> service.publish("t1", "DEV")).getMessage());
        }

        @Test
        void unpublishCancelsSchedule() {
            FlowTaskDO t = task("t1");
            t.setPublishStatus(1);
            t.setPublishedSnapshot("{}");
            when(repository.findById("t1")).thenReturn(Optional.of(t));

            FlowTaskDO saved = service.unpublish("t1");

            assertEquals(0, saved.getPublishStatus());
            assertNull(saved.getPublishedSnapshot());
            verify(scheduler).cancel("t1");
            verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
        }

        @Test
        void rollbackReschedulesOrCancels() {
            FlowTaskDO t = task("t1");
            t.setEnabled(false);
            t.setPublishStatus(1);
            t.setPublishTime(PUBLISHED_AT);
            t.setPublishedSnapshot("{\"name\":\"线上任务\",\"cron\":\"0 0 * * * *\",\"dslContent\":\"d\",\"enabled\":true}");
            when(repository.findById("t1")).thenReturn(Optional.of(t));

            FlowTaskDO saved = service.rollbackToPublished("t1");

            assertEquals("线上任务", saved.getName());
            assertEquals(Boolean.TRUE, saved.getEnabled(), "回滚草稿连同启停一起恢复");
            assertEquals(PUBLISHED_AT, saved.getUpdateTime());
            verify(scheduler).reschedule(saved);

            FlowTaskDO none = task("t2");
            when(repository.findById("t2")).thenReturn(Optional.of(none));
            assertEquals("该任务没有发布快照，无法回滚",
                    assertThrows(RuntimeException.class, () -> service.rollbackToPublished("t2")).getMessage());
        }

        @Test
        void restoreVersionKeepsCurrentSwitches() {
            FlowTaskDO t = task("t1");
            t.setEnabled(false);
            t.setLogEnabled(true);
            when(repository.findById("t1")).thenReturn(Optional.of(t));
            when(versionService.requireOwned("v5", AssetBizType.TASK, "t1"))
                    .thenReturn(version(5, "{\"name\":\"v5\",\"cron\":\"0 5 * * * *\",\"dslContent\":\"d\","
                            + "\"enabled\":true,\"logEnabled\":false}"));

            FlowTaskDO saved = service.restoreVersion("t1", "v5");

            assertEquals("v5", saved.getName());
            assertEquals("0 5 * * * *", saved.getCron());
            assertEquals(Boolean.FALSE, saved.getEnabled());
            assertEquals(Boolean.TRUE, saved.getLogEnabled());
            verify(versionService).append(eq(AssetBizType.TASK), eq("t1"), anyString(),
                    eq(AssetBizType.SOURCE_ROLLBACK), eq("回退至 v5"), any());
            verify(scheduler).cancel("t1");
        }

        private FlowTaskDO task(String id) {
            FlowTaskDO t = new FlowTaskDO();
            t.setId(id);
            t.setName("任务 " + id);
            t.setCron("0 0 * * * *");
            t.setDslContent("dsl");
            return t;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Api {

        @Mock private FlowApiRepository repository;
        @Mock private HostReservedApiWriteGuard hostReservedApiWriteGuard;
        @Mock private FlowApiCacheManager flowApiCacheManager;
        @Mock private DemoModeGuard demoModeGuard;
        @Mock private ApiResponseCacheService apiResponseCacheService;
        @Mock private FlowAssetVersionService versionService;
        @Mock private FlowReferenceIndex referenceIndex;
        @Mock private PublishGateService publishGateService;
        @Mock private ReleaseEnvironment releaseEnvironment;
        @Mock private OpenPlatformCache openPlatformCache;
        @Mock private AuditLogService auditLogService;
        @Mock private YuFlowProperties yuFlowProperties;

        @InjectMocks
        private FlowApiCrudServiceImpl service;

        @BeforeEach
        void setUp() {
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(releaseEnvironment.resolve(any())).thenReturn("TEST");
        }

        @Test
        void publishRefreshesRouteAndKeepsUpdateTime() {
            FlowApiDO api = api("a1");
            api.setUpdateTime(PUBLISHED_AT);
            when(repository.findById("a1")).thenReturn(Optional.of(api));

            FlowApiDO saved = service.publish("a1", "TEST");

            var order = inOrder(hostReservedApiWriteGuard, demoModeGuard, publishGateService);
            order.verify(hostReservedApiWriteGuard).assertAllowed("a1");
            order.verify(demoModeGuard).checkModifyOrDelete("a1", "API 接口");
            order.verify(publishGateService).assertCanPublish("API", "a1", "TEST");
            assertEquals(1, saved.getPublishStatus());
            assertNotNull(saved.getPublishTime());
            assertEquals(PUBLISHED_AT, saved.getUpdateTime(), "接口发布不改 updateTime");
            verify(versionService).append(eq(AssetBizType.API), eq("a1"), anyString(),
                    eq(AssetBizType.SOURCE_PUBLISH), isNull(), any());
            verify(flowApiCacheManager).publishRefreshEvent("a1");
            verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
            verify(auditLogService).record(eq("API_PUBLISH"), eq("API"), eq("a1"), contains("\"url\":\"/orders\""));
        }

        @Test
        void unpublishRefreshesRouteAndOpenPlatform() {
            FlowApiDO api = api("a1");
            api.setPublishStatus(1);
            api.setPublishedSnapshot("{}");
            when(repository.findById("a1")).thenReturn(Optional.of(api));

            FlowApiDO saved = service.unpublish("a1");

            assertEquals(0, saved.getPublishStatus());
            assertNull(saved.getPublishedSnapshot());
            assertNull(saved.getUpdateTime(), "接口下线不改 updateTime");
            verify(flowApiCacheManager).publishRefreshEvent("a1");
            verify(openPlatformCache).publishRefresh();
            verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
        }

        @Test
        void rollbackAlignsUpdateTimeToPublishTime() {
            FlowApiDO api = api("a1");
            api.setName("草稿");
            api.setPublishStatus(1);
            api.setPublishTime(PUBLISHED_AT);
            api.setPublishedSnapshot("{\"name\":\"线上\",\"url\":\"/orders\",\"method\":\"GET\"}");
            when(repository.findById("a1")).thenReturn(Optional.of(api));

            FlowApiDO saved = service.rollbackToPublished("a1");

            assertEquals("线上", saved.getName());
            assertEquals(PUBLISHED_AT, saved.getUpdateTime());
            verify(flowApiCacheManager, never()).publishRefreshEvent(anyString());

            FlowApiDO none = api("a2");
            when(repository.findById("a2")).thenReturn(Optional.of(none));
            assertEquals("该 API 没有发布快照，无法回滚",
                    assertThrows(RuntimeException.class, () -> service.rollbackToPublished("a2")).getMessage());
        }

        @Test
        void restoreVersionGoesOnlineAndRefreshesRoute() {
            FlowApiDO api = api("a1");
            when(repository.findById("a1")).thenReturn(Optional.of(api));
            when(versionService.requireOwned("v1", AssetBizType.API, "a1"))
                    .thenReturn(version(1, "{\"name\":\"v1\",\"url\":\"/orders\",\"method\":\"GET\"}"));

            FlowApiDO saved = service.restoreVersion("a1", "v1");

            assertEquals("v1", saved.getName());
            assertEquals(1, saved.getPublishStatus());
            assertEquals(saved.getPublishTime(), saved.getUpdateTime());
            verify(versionService).append(eq(AssetBizType.API), eq("a1"), anyString(),
                    eq(AssetBizType.SOURCE_ROLLBACK), eq("回退至 v1"), any());
            verify(flowApiCacheManager).publishRefreshEvent("a1");
        }

        private FlowApiDO api(String id) {
            FlowApiDO api = new FlowApiDO();
            api.setId(id);
            api.setName("接口 " + id);
            api.setUrl("/orders");
            api.setMethod("GET");
            api.setServiceType("FLOW");
            api.setDslContent("dsl");
            return api;
        }
    }

    private static FlowAssetVersionDO version(int no, String snapshot) {
        FlowAssetVersionDO v = new FlowAssetVersionDO();
        v.setVersionNo(no);
        v.setSnapshot(snapshot);
        return v;
    }
}
