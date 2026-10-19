package org.yu.flow.module.release.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.api.support.ApiReleaseAsset;
import org.yu.flow.module.host.HostCatalogReserved;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetInfo;
import org.yu.flow.module.release.support.ReleaseAssetResolver.AssetRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseAssetResolverDescribeAllTest {

    @Mock
    private FlowApiRepository flowApiRepository;

    @Mock
    private ObjectProvider<FlowApiCrudService> crudService;

    private ReleaseAssetResolver resolver;

    @BeforeEach
    void setUp() {
        List<ReleaseAssetType<?>> types = new ArrayList<>();
        types.add(new ApiReleaseAsset(flowApiRepository, crudService));
        for (String type : ReleaseAssetResolver.TYPES) {
            if (!ReleaseAssetResolver.API.equals(type)) {
                types.add(typeOnly(type));
            }
        }
        resolver = new ReleaseAssetResolver(types);
    }

    @Test
    void sameTypeIsLoadedOnceAndReservedIdsAreDropped() {
        FlowApiDO visible = new FlowApiDO();
        visible.setId("api-1");
        visible.setName("下单");
        visible.setMethod("POST");
        visible.setUrl("/order");
        FlowApiDO reserved = new FlowApiDO();
        reserved.setId(HostCatalogReserved.DIR_USER);
        reserved.setName("宿主目录");
        when(flowApiRepository.findAllById(any())).thenReturn(List.of(visible, reserved));

        Map<String, AssetInfo> found = resolver.describeAll(List.of(
                new AssetRef("api", "api-1"),
                new AssetRef("API", HostCatalogReserved.DIR_USER)));

        assertEquals(1, found.size());
        AssetInfo info = found.get(ReleaseAssetResolver.refKey("API", "api-1"));
        assertEquals("下单", info.name());
        assertFalse(found.containsKey(ReleaseAssetResolver.refKey("API", HostCatalogReserved.DIR_USER)));
        verify(flowApiRepository, times(1)).findAllById(any());
    }

    @Test
    void everyTypeMustHaveAnImplementation() {
        List<ReleaseAssetType<?>> missingOne = List.of(new ApiReleaseAsset(flowApiRepository, crudService));
        assertThrows(IllegalStateException.class, () -> new ReleaseAssetResolver(missingOne));
    }

    private static ReleaseAssetType<?> typeOnly(String type) {
        ReleaseAssetType<?> t = mock(ReleaseAssetType.class);
        when(t.type()).thenReturn(type);
        return t;
    }
}
