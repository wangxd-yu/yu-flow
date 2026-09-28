package org.yu.flow.module.release.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.assetref.FlowReferenceIndex;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleaseReferenceCheckerTest {

    private static final String CALLS_S1 =
            "{\"nodes\":[{\"id\":\"n1\",\"type\":\"api\",\"data\":{\"serviceId\":\"s1\",\"targetType\":\"service\"}}]}";

    @Mock private FlowReferenceIndex flowReferenceIndex;
    @Mock private ReleaseAssetResolver assetResolver;
    @Mock private FlowMqTaskRepository flowMqTaskRepository;
    @Mock private FlowApiRepository flowApiRepository;
    @Mock private FlowServiceFlowRepository flowServiceFlowRepository;

    @InjectMocks
    private ReleaseReferenceChecker checker;

    private static AssetBundle bundleCallingS1() {
        AssetBundle bundle = new AssetBundle();
        bundle.getApis().add(new FlowApiDO().setId("a1").setName("下单").setDslContent(CALLS_S1));
        return bundle;
    }

    @Test
    void missingOrUnpublishedCalleeBlocks() {
        List<String> blocks = new ArrayList<>();
        when(flowServiceFlowRepository.existsById("s1")).thenReturn(false);
        checker.checkOutbound(bundleCallingS1(), Set.of(), blocks);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).contains("不存在"));

        blocks.clear();
        when(flowServiceFlowRepository.existsById("s1")).thenReturn(true);
        when(assetResolver.isOnline(ReleaseAssetResolver.SERVICE, "s1")).thenReturn(false);
        checker.checkOutbound(bundleCallingS1(), Set.of(), blocks);
        assertTrue(blocks.get(0).contains("未发布"));

        blocks.clear();
        checker.checkOutbound(bundleCallingS1(), Set.of("SERVICE:s1"), blocks);
        assertTrue(blocks.get(0).contains("本次下线"));

        blocks.clear();
        when(assetResolver.isOnline(ReleaseAssetResolver.SERVICE, "s1")).thenReturn(true);
        checker.checkOutbound(bundleCallingS1(), Set.of(), blocks);
        assertTrue(blocks.isEmpty());
    }

    @Test
    void offlineConflictsConsiderOnlinePublishedCallersOnly() {
        AssetRef target = new AssetRef(ReleaseAssetResolver.SERVICE, "s1");
        when(flowReferenceIndex.findReferrers("service", "s1")).thenReturn(Set.of(
                new FlowReferenceIndex.SourceRef(FlowReferenceIndex.SourceKind.API, "live", "查询"),
                new FlowReferenceIndex.SourceRef(FlowReferenceIndex.SourceKind.API, "draftOnly", "草稿"),
                new FlowReferenceIndex.SourceRef(FlowReferenceIndex.SourceKind.API, "alsoOffline", "一起下线"),
                new FlowReferenceIndex.SourceRef(FlowReferenceIndex.SourceKind.API, "a1", "下单")));
        when(assetResolver.describe(ReleaseAssetResolver.API, "live")).thenReturn(new AssetInfo(ReleaseAssetResolver.API,
                "live", "查询", null, true, true, false, "h", List.of(target)));
        when(assetResolver.describe(ReleaseAssetResolver.API, "draftOnly")).thenReturn(new AssetInfo(ReleaseAssetResolver.API,
                "draftOnly", "草稿", null, true, true, false, "h", List.of()));

        // a1 随包发布的新版本不再调用 s1
        List<ReleaseReferenceChecker.Conflict> conflicts = checker.offlineConflicts(
                List.of(target, new AssetRef(ReleaseAssetResolver.API, "alsoOffline")),
                Map.of("API:a1", "{\"nodes\":[]}"), Map.of(target, "旧服务"));

        assertEquals(1, conflicts.size());
        assertEquals("旧服务", conflicts.get(0).targetName());
        assertEquals(List.of("接口「查询」"), conflicts.get(0).callers());
    }
}
