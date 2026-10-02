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
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.exception.FlowException;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.release.domain.FlowReleaseDO;
import org.yu.flow.module.release.domain.FlowReleaseItemDO;
import org.yu.flow.module.release.dto.AddOfflineItemsDTO;
import org.yu.flow.module.release.dto.AddReleaseItemsDTO;
import org.yu.flow.module.release.dto.ReleaseCompareDTO;
import org.yu.flow.module.release.dto.ReleaseDTO;
import org.yu.flow.module.release.support.ReleaseSigning;
import org.yu.flow.module.release.dto.ReleaseCheckResultDTO;
import org.yu.flow.module.release.repository.FlowRegressionRunRepository;
import org.yu.flow.module.release.repository.FlowReleaseItemRepository;
import org.yu.flow.module.release.repository.FlowReleaseRepository;
import org.yu.flow.module.release.support.ReleaseAssetResolver;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;
import org.yu.flow.module.release.support.ReleaseEnvironment;
import org.yu.flow.module.release.support.ReleaseReferenceChecker;
import org.yu.flow.module.transfer.service.AssetTransferService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleasePlanServiceImplTest {

    @Mock
    private FlowReleaseRepository flowReleaseRepository;
    @Mock
    private FlowReleaseItemRepository flowReleaseItemRepository;
    @Mock
    private ReleaseAssetResolver assetResolver;
    @Mock
    private AssetTransferService assetTransferService;
    @Mock
    private ReleaseEnvironment releaseEnvironment;
    @Mock
    private DemoModeGuard demoModeGuard;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FlowRegressionRunRepository flowRegressionRunRepository;
    @Mock
    private ReleaseSigning releaseSigning;
    @Mock
    private ReleaseReferenceChecker referenceChecker;

    @InjectMocks
    private ReleasePlanServiceImpl service;

    private FlowReleaseDO release;
    private final List<FlowReleaseItemDO> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        release = FlowReleaseDO.builder().id("r1").code("v2026.10").status(FlowReleaseDO.STATUS_DRAFT).build();
        when(flowReleaseRepository.findById("r1")).thenReturn(Optional.of(release));
        when(flowReleaseItemRepository.findByReleaseIdOrderByCreateTimeAsc("r1")).thenReturn(items);
        when(flowReleaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AssetInfo api(String id, boolean published, boolean dirty, String hash, AssetRef... deps) {
        return new AssetInfo(ReleaseAssetResolver.API, id, "接口" + id, "GET /" + id, true, published, dirty, hash,
                new ArrayList<>(List.of(deps)));
    }

    private void addItem(String type, String id, String hash) {
        items.add(FlowReleaseItemDO.builder().id("i-" + id).releaseId("r1").assetType(type).assetId(id)
                .assetName("接口" + id).action("UPSERT").origin("MANUAL").contentHash(hash).build());
    }

    @Test
    void getLoadsAssetsInOneBatch() {
        addItem(ReleaseAssetResolver.API, "a", "h-a");
        addItem(ReleaseAssetResolver.API, "b", "h-b");
        when(assetResolver.describeAll(any())).thenReturn(Map.of(
                ReleaseAssetResolver.refKey(ReleaseAssetResolver.API, "a"), api("a", true, false, "h-a"),
                ReleaseAssetResolver.refKey(ReleaseAssetResolver.API, "b"), api("b", true, false, "h-b")));

        ReleaseDTO dto = service.get("r1");

        assertEquals(2, dto.getItemCount());
        assertEquals("接口a", dto.getItems().get(0).getAssetName());
        assertEquals("接口b", dto.getItems().get(1).getAssetName());
        verify(assetResolver, times(1)).describeAll(any());
        verify(assetResolver, never()).describe(any(), any());
    }

    @Test
    void freezeBlockedWhenAssetUnpublishedOrDirty() {
        addItem(ReleaseAssetResolver.API, "a", null);
        addItem(ReleaseAssetResolver.API, "b", null);
        when(assetResolver.describe(ReleaseAssetResolver.API, "a")).thenReturn(api("a", false, false, null));
        when(assetResolver.describe(ReleaseAssetResolver.API, "b")).thenReturn(api("b", true, true, "h-b"));

        ReleaseCheckResultDTO result = service.freeze("r1");

        assertFalse(result.isPassed());
        assertFalse(result.isFrozen());
        assertEquals(2, result.getIssues().stream().filter(i -> "ERROR".equals(i.getLevel())).count());
        assertEquals(FlowReleaseDO.STATUS_DRAFT, release.getStatus());
    }

    @Test
    void freezeRecordsHashesAndWarnsAboutMissingDependency() {
        addItem(ReleaseAssetResolver.API, "a", null);
        when(assetResolver.describe(ReleaseAssetResolver.API, "a"))
                .thenReturn(api("a", true, false, "h-a", new AssetRef(ReleaseAssetResolver.SERVICE, "s1")));
        when(assetResolver.describe(ReleaseAssetResolver.SERVICE, "s1")).thenReturn(
                new AssetInfo(ReleaseAssetResolver.SERVICE, "s1", "内部服务", null, true, true, false, "h-s", List.of()));

        ReleaseCheckResultDTO result = service.freeze("r1");

        assertTrue(result.isPassed());
        assertTrue(result.isFrozen());
        assertEquals(1, result.getIssues().size());
        assertEquals("WARN", result.getIssues().get(0).getLevel());
        assertEquals("h-a", items.get(0).getContentHash());
        assertEquals(FlowReleaseDO.STATUS_FROZEN, release.getStatus());
    }

    @Test
    void addItemsExpandsDependencies() {
        when(assetResolver.describe(ReleaseAssetResolver.API, "a")).thenReturn(api("a", true, false, "h-a",
                new AssetRef(ReleaseAssetResolver.SERVICE, "s1"), new AssetRef(ReleaseAssetResolver.RESPONSE_TEMPLATE, "t1")));
        when(assetResolver.describe(ReleaseAssetResolver.SERVICE, "s1")).thenReturn(
                new AssetInfo(ReleaseAssetResolver.SERVICE, "s1", "内部服务", null, true, true, false, "h-s", List.of()));
        when(assetResolver.describe(ReleaseAssetResolver.RESPONSE_TEMPLATE, "t1")).thenReturn(
                new AssetInfo(ReleaseAssetResolver.RESPONSE_TEMPLATE, "t1", "模板", null, false, false, false, "h-t", List.of()));
        AddReleaseItemsDTO dto = new AddReleaseItemsDTO();
        AddReleaseItemsDTO.AssetRefDTO ref = new AddReleaseItemsDTO.AssetRefDTO();
        ref.setAssetType("api");
        ref.setAssetId("a");
        dto.getItems().add(ref);

        service.addItems("r1", dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FlowReleaseItemDO>> saved = ArgumentCaptor.forClass(List.class);
        verify(flowReleaseItemRepository).saveAll(saved.capture());
        assertEquals(List.of("MANUAL", "DEPENDENCY", "DEPENDENCY"),
                saved.getValue().stream().map(FlowReleaseItemDO::getOrigin).toList());
    }

    @Test
    void addItemsRejectedAfterFreeze() {
        release.setStatus(FlowReleaseDO.STATUS_FROZEN);
        FlowException ex = assertThrows(FlowException.class, () -> service.addItems("r1", new AddReleaseItemsDTO()));
        assertEquals("RELEASE_STATE", ex.getErrorCode());
    }

    @Test
    void exportBlockedWhenContentDriftedAfterFreeze() {
        release.setStatus(FlowReleaseDO.STATUS_FROZEN);
        addItem(ReleaseAssetResolver.API, "a", "h-old");
        when(assetResolver.describe(ReleaseAssetResolver.API, "a")).thenReturn(api("a", true, false, "h-new"));

        FlowException ex = assertThrows(FlowException.class, () -> service.exportPackage("r1"));

        assertEquals("RELEASE_DRIFTED", ex.getErrorCode());
        verifyNoInteractions(assetTransferService);
        verify(flowReleaseItemRepository, never()).saveAll(anyList());
    }

    @Test
    void offlineItemsSkipPublishChecksAndDriftButWarnWhenStillOnline() {
        items.add(FlowReleaseItemDO.builder().id("i-gone").releaseId("r1").assetType(ReleaseAssetResolver.API)
                .assetId("gone").assetName("已删除接口").action(FlowReleaseItemDO.ACTION_OFFLINE).origin("MANUAL").build());
        items.add(FlowReleaseItemDO.builder().id("i-live").releaseId("r1").assetType(ReleaseAssetResolver.PAGE)
                .assetId("p1").assetName("旧页面").action(FlowReleaseItemDO.ACTION_OFFLINE).origin("MANUAL").build());
        when(assetResolver.isOnline(ReleaseAssetResolver.API, "gone")).thenReturn(false);
        when(assetResolver.isOnline(ReleaseAssetResolver.PAGE, "p1")).thenReturn(true);

        ReleaseCheckResultDTO result = service.freeze("r1");

        assertTrue(result.isFrozen());
        assertEquals(1, result.getIssues().size());
        assertEquals("WARN", result.getIssues().get(0).getLevel());
        assertNull(items.get(0).getContentHash());
        verify(assetResolver, never()).describe(ReleaseAssetResolver.API, "gone");
    }

    @Test
    void offlineServiceStillCalledByOnlineAssetsIsWarned() {
        items.add(FlowReleaseItemDO.builder().id("i-s").releaseId("r1").assetType(ReleaseAssetResolver.SERVICE)
                .assetId("s1").assetName("旧服务").action(FlowReleaseItemDO.ACTION_OFFLINE).origin("MANUAL").build());
        AssetRef target = new AssetRef(ReleaseAssetResolver.SERVICE, "s1");
        when(referenceChecker.offlineConflicts(eq(List.of(target)), eq(java.util.Map.of()), any()))
                .thenReturn(List.of(new ReleaseReferenceChecker.Conflict(target, "旧服务", List.of("接口「下单」"))));

        ReleaseCheckResultDTO result = service.check("r1");

        assertTrue(result.isPassed());
        assertEquals(1, result.getIssues().size());
        assertTrue(result.getIssues().get(0).getMessage().contains("接口「下单」"));
    }

    @Test
    void offlineRejectsTypesWithoutOnlineState() {
        AddOfflineItemsDTO dto = new AddOfflineItemsDTO();
        AddOfflineItemsDTO.Item item = new AddOfflineItemsDTO.Item();
        item.setAssetType("MODEL");
        item.setAssetId("m1");
        dto.getItems().add(item);

        FlowException ex = assertThrows(FlowException.class, () -> service.addOfflineItems("r1", dto));
        assertEquals("RELEASE_INVALID", ex.getErrorCode());
    }

    @Test
    void compareReportsAddedRemovedAndChanged() {
        FlowReleaseDO other = FlowReleaseDO.builder().id("r2").code("v2026.11").status(FlowReleaseDO.STATUS_FROZEN).build();
        release.setStatus(FlowReleaseDO.STATUS_EXPORTED);
        when(flowReleaseRepository.findById("r2")).thenReturn(Optional.of(other));
        addItem(ReleaseAssetResolver.API, "same", "h1");
        addItem(ReleaseAssetResolver.API, "changed", "h-old");
        addItem(ReleaseAssetResolver.API, "removed", "h3");
        List<FlowReleaseItemDO> targetItems = List.of(
                FlowReleaseItemDO.builder().assetType(ReleaseAssetResolver.API).assetId("same").action("UPSERT").contentHash("h1").build(),
                FlowReleaseItemDO.builder().assetType(ReleaseAssetResolver.API).assetId("changed").action("UPSERT").contentHash("h-new").build(),
                FlowReleaseItemDO.builder().assetType(ReleaseAssetResolver.PAGE).assetId("added").action("UPSERT").contentHash("h4").build());
        when(flowReleaseItemRepository.findByReleaseIdOrderByCreateTimeAsc("r2")).thenReturn(targetItems);

        ReleaseCompareDTO diff = service.compare("r1", "r2");

        assertTrue(diff.isContentComparable());
        assertEquals(1, diff.getUnchangedCount());
        assertEquals(List.of("changed"), diff.getChanged().stream().map(ReleaseCompareDTO.Entry::getAssetId).toList());
        assertEquals(List.of("removed"), diff.getOnlyInBase().stream().map(ReleaseCompareDTO.Entry::getAssetId).toList());
        assertEquals(List.of("added"), diff.getOnlyInTarget().stream().map(ReleaseCompareDTO.Entry::getAssetId).toList());
    }

    @Test
    void exportedReleaseCannotBeDeleted() {
        release.setStatus(FlowReleaseDO.STATUS_EXPORTED);
        FlowException ex = assertThrows(FlowException.class, () -> service.delete("r1"));
        assertEquals("RELEASE_STATE", ex.getErrorCode());
    }
}
