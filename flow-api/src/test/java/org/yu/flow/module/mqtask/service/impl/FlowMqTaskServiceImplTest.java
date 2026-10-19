package org.yu.flow.module.mqtask.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.assetversion.AssetBizType;
import org.yu.flow.module.assetversion.domain.FlowAssetVersionDO;
import org.yu.flow.module.assetversion.service.FlowAssetVersionService;
import org.yu.flow.module.log.audit.service.AuditLogService;
import org.yu.flow.module.mqtask.consumer.MqConsumerManager;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.release.service.PublishGateService;
import org.yu.flow.module.release.support.ReleaseEnvironment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowMqTaskServiceImplTest {

    @Mock private FlowMqTaskRepository repository;
    @Mock private MqConsumerManager mqConsumerManager;
    @Mock private DemoModeGuard demoModeGuard;
    @Mock private FlowAssetVersionService versionService;
    @Mock private FlowReferenceIndex referenceIndex;
    @Mock private AuditLogService auditLogService;
    @Mock private PublishGateService publishGateService;
    @Mock private ReleaseEnvironment releaseEnvironment;

    @InjectMocks
    private FlowMqTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void publishRecordsVersionAuditAndReferenceRefresh() {
        FlowMqTaskDO task = task("m1");
        task.setEnabled(true);
        when(repository.findById("m1")).thenReturn(Optional.of(task));

        FlowMqTaskDO saved = service.publish("m1");

        assertEquals(1, saved.getPublishStatus());
        verify(versionService).append(eq(AssetBizType.MQ_TASK), eq("m1"), eq(saved.getPublishedSnapshot()),
                eq(AssetBizType.SOURCE_PUBLISH), isNull(), any());
        verify(mqConsumerManager).resubscribe(saved);
        verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
        verify(auditLogService).record(eq("MQ_TASK_PUBLISH"), eq("MQ_TASK"), eq("m1"), contains("topic-a"));
    }

    @Test
    void disabledTaskIsPublishedWithoutSubscribing() {
        FlowMqTaskDO task = task("m1");
        task.setEnabled(false);
        when(repository.findById("m1")).thenReturn(Optional.of(task));

        service.publish("m1");

        verify(mqConsumerManager, never()).resubscribe(any());
    }

    @Test
    void unpublishCancelsSubscriptionAndRefreshesReferences() {
        FlowMqTaskDO task = task("m1");
        task.setPublishStatus(1);
        task.setPublishedSnapshot("{}");
        when(repository.findById("m1")).thenReturn(Optional.of(task));

        FlowMqTaskDO saved = service.unpublish("m1");

        assertEquals(0, saved.getPublishStatus());
        assertNull(saved.getPublishedSnapshot());
        verify(mqConsumerManager).cancel("m1");
        verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
    }

    @Test
    void restoreVersionGoesOnlineButKeepsCurrentSwitches() {
        FlowMqTaskDO task = task("m1");
        task.setEnabled(false);
        task.setLogEnabled(true);
        when(repository.findById("m1")).thenReturn(Optional.of(task));
        FlowAssetVersionDO version = new FlowAssetVersionDO();
        version.setVersionNo(3);
        version.setSnapshot("{\"name\":\"旧版\",\"topic\":\"topic-old\",\"dslContent\":\"old\","
                + "\"enabled\":true,\"logEnabled\":false}");
        when(versionService.requireOwned("v3", AssetBizType.MQ_TASK, "m1")).thenReturn(version);

        FlowMqTaskDO saved = service.restoreVersion("m1", "v3");

        assertEquals("旧版", saved.getName());
        assertEquals("topic-old", saved.getTopic());
        assertEquals("old", saved.getDslContent());
        assertEquals(Boolean.FALSE, saved.getEnabled(), "启停保持当前值");
        assertEquals(Boolean.TRUE, saved.getLogEnabled(), "日志开关保持当前值");
        assertEquals(1, saved.getPublishStatus());
        assertEquals(version.getSnapshot(), saved.getPublishedSnapshot());
        verify(versionService).append(eq(AssetBizType.MQ_TASK), eq("m1"), eq(version.getSnapshot()),
                eq(AssetBizType.SOURCE_ROLLBACK), eq("回退至 v3"), any());
        verify(mqConsumerManager).cancel("m1");
        verify(referenceIndex).scheduleRebuildBroadcastAfterCommit();
    }

    @Test
    void restoreRejectsVersionOfAnotherTask() {
        when(repository.findById("m1")).thenReturn(Optional.of(task("m1")));
        when(versionService.requireOwned(anyString(), eq(AssetBizType.MQ_TASK), eq("m1")))
                .thenThrow(new IllegalArgumentException("版本不属于该资产"));

        assertThrows(IllegalArgumentException.class, () -> service.restoreVersion("m1", "other"));
        verify(repository, never()).save(any());
    }

    private static FlowMqTaskDO task(String id) {
        FlowMqTaskDO t = new FlowMqTaskDO();
        t.setId(id);
        t.setName("MQ " + id);
        t.setConnectionCode("conn");
        t.setTopic("topic-a");
        t.setDslContent("dsl");
        return t;
    }
}
