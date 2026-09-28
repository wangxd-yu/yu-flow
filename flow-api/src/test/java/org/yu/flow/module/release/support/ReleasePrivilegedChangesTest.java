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
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.repository.FlowOpenApiGrantRepository;
import org.yu.flow.module.open.repository.FlowOpenPlatformRepository;
import org.yu.flow.module.release.dto.ReleaseInspectResultDTO.PrivilegedChange;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.repository.SysMacroRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.BundleOpenPlatform;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleasePrivilegedChangesTest {

    @Mock private SysMacroRepository sysMacroRepository;
    @Mock private FlowOpenPlatformRepository flowOpenPlatformRepository;
    @Mock private FlowOpenApiGrantRepository flowOpenApiGrantRepository;
    @Mock private FlowApiRepository flowApiRepository;

    @InjectMocks
    private ReleasePrivilegedChanges changes;

    private static SysMacroDO macro(String code, String expression) {
        return SysMacroDO.builder().macroCode(code).macroName(code).expression(expression).status(1).build();
    }

    @Test
    void newOrChangedMacroExpressionsAreListed() {
        AssetBundle bundle = new AssetBundle();
        bundle.getSysMacros().add(macro("NEW_ONE", "@environment.getProperty('a')"));
        bundle.getSysMacros().add(macro("SAME", "T(java.time.LocalDate).now()"));
        bundle.getSysMacros().add(macro("CHANGED", "@environment.getProperty('b')"));
        when(sysMacroRepository.findByMacroCode("NEW_ONE")).thenReturn(Optional.empty());
        when(sysMacroRepository.findByMacroCode("SAME")).thenReturn(Optional.of(macro("SAME", "T(java.time.LocalDate).now()")));
        when(sysMacroRepository.findByMacroCode("CHANGED")).thenReturn(Optional.of(macro("CHANGED", "'x'")));

        List<PrivilegedChange> result = changes.collect(bundle);

        assertEquals(List.of("NEW_ONE", "CHANGED"), result.stream().map(PrivilegedChange::getKey).toList());
        assertTrue(result.get(0).getDescription().startsWith("新增全局宏"));
        assertTrue(result.get(1).getDescription().contains("'x' → @environment.getProperty('b')"));
    }

    @Test
    void grantDiffIgnoresApisMissingEverywhere() {
        FlowOpenPlatformDO existing = FlowOpenPlatformDO.builder().id("p-prod").code("partner").name("合作方").build();
        when(flowOpenPlatformRepository.findByCode("partner")).thenReturn(Optional.of(existing));
        when(flowOpenApiGrantRepository.findByPlatformId("p-prod")).thenReturn(List.of(
                FlowOpenApiGrantDO.builder().apiId("api-1").allowMethods("GET").build(),
                FlowOpenApiGrantDO.builder().apiId("api-9").allowMethods("GET").build()));
        when(flowApiRepository.existsById("api-1")).thenReturn(true);
        when(flowApiRepository.existsById("api-3")).thenReturn(false);
        when(flowApiRepository.findById("api-9")).thenReturn(Optional.of(new FlowApiDO().setName("退款")));

        AssetBundle bundle = new AssetBundle();
        FlowApiDO api2 = new FlowApiDO().setId("api-2").setName("下单");
        bundle.getApis().add(api2);
        BundleOpenPlatform platform = new BundleOpenPlatform();
        platform.setPlatform(FlowOpenPlatformDO.builder().code("partner").name("合作方").build());
        platform.getGrants().add(new BundleOpenPlatform.Grant("api-1", "GET"));
        platform.getGrants().add(new BundleOpenPlatform.Grant("api-2", "POST"));
        platform.getGrants().add(new BundleOpenPlatform.Grant("api-3", "GET"));
        bundle.getOpenPlatforms().add(platform);

        List<PrivilegedChange> result = changes.collect(bundle);

        assertEquals(1, result.size());
        String description = result.get(0).getDescription();
        assertTrue(description.contains("新增授权 1 个：下单"), description);
        assertTrue(description.contains("撤销授权 1 个：退款"), description);
        assertFalse(description.contains("api-3"), description);
    }

    @Test
    void unchangedPlatformIsNotListed() {
        FlowOpenPlatformDO existing = FlowOpenPlatformDO.builder().id("p-prod").code("partner").name("合作方").build();
        when(flowOpenPlatformRepository.findByCode("partner")).thenReturn(Optional.of(existing));
        when(flowOpenApiGrantRepository.findByPlatformId("p-prod")).thenReturn(List.of(
                FlowOpenApiGrantDO.builder().apiId("api-1").allowMethods("GET").build()));
        when(flowApiRepository.existsById("api-1")).thenReturn(true);
        AssetBundle bundle = new AssetBundle();
        BundleOpenPlatform platform = new BundleOpenPlatform();
        platform.setPlatform(FlowOpenPlatformDO.builder().code("partner").name("合作方").build());
        platform.getGrants().add(new BundleOpenPlatform.Grant("api-1", "GET"));
        bundle.getOpenPlatforms().add(platform);

        assertTrue(changes.collect(bundle).isEmpty());
    }
}
