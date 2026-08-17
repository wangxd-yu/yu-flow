package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.host.dto.HostIdentityCatalogDTO;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlowHostIdentityCatalogServiceTest {

    @Test
    void noProvider_unavailableAndNoOptions() {
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, null);

        HostIdentityCatalogDTO snap = svc.snapshot();
        assertFalse(snap.isAvailable());
        assertTrue(snap.getDimensions().get("USER_TYPE").getItems().isEmpty());
        assertTrue(svc.search(FlowHostCatalogDimension.USER_TYPE, null, 20).isEmpty());
    }

    @Test
    void provider_listsUserTypesAndAppendsOpenApp() {
        FlowHostIdentityCatalogProvider provider = new FlowHostIdentityCatalogProvider() {
            @Override
            public boolean searchable(FlowHostCatalogDimension dimension) {
                return false;
            }

            @Override
            public List<FlowHostCatalogItem> list(FlowHostCatalogDimension dimension, String keyword, int limit) {
                if (dimension == FlowHostCatalogDimension.USER_TYPE) {
                    return List.of(
                            FlowHostCatalogItem.builder().value("ADMIN").label("运营").build(),
                            FlowHostCatalogItem.builder().value("END_USER").label("C 端").build()
                    );
                }
                return List.of();
            }
        };
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(provider);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, null);

        HostIdentityCatalogDTO snap = svc.snapshot();
        assertTrue(snap.isAvailable());
        List<HostIdentityCatalogItemDTO> types = snap.getDimensions().get("USER_TYPE").getItems();
        assertEquals(3, types.size());
        assertEquals("ADMIN", types.get(0).getValue());
        assertEquals("END_USER", types.get(1).getValue());
        assertEquals("OPEN_APP", types.get(2).getValue());
        assertEquals("FLOW", types.get(2).getSource());
    }

    @Test
    void searchable_notPreloadedOnSnapshot() {
        AtomicInteger listCalls = new AtomicInteger();
        FlowHostIdentityCatalogProvider provider = new FlowHostIdentityCatalogProvider() {
            @Override
            public boolean searchable(FlowHostCatalogDimension dimension) {
                return dimension == FlowHostCatalogDimension.USER;
            }

            @Override
            public List<FlowHostCatalogItem> list(FlowHostCatalogDimension dimension, String keyword, int limit) {
                listCalls.incrementAndGet();
                return List.of(FlowHostCatalogItem.builder().value("u-1").label("张三").build());
            }
        };
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(provider);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, null);

        HostIdentityCatalogDTO snap = svc.snapshot();
        assertTrue(snap.getDimensions().get("USER").isSearchable());
        assertTrue(snap.getDimensions().get("USER").getItems().isEmpty());
        assertEquals(4, listCalls.get()); // USER_TYPE ROLE PERMISSION DEPT 非搜索预拉；USER 不预拉

        List<HostIdentityCatalogItemDTO> users = svc.search(FlowHostCatalogDimension.USER, "张", 10);
        assertEquals(1, users.size());
        assertEquals("u-1", users.get(0).getValue());
    }

    @Test
    void unsupportedDimension_empty() {
        FlowHostIdentityCatalogProvider provider = mock(FlowHostIdentityCatalogProvider.class);
        when(provider.supports(any())).thenReturn(true);
        when(provider.supports(FlowHostCatalogDimension.ROLE)).thenReturn(false);
        when(provider.searchable(any())).thenReturn(false);
        when(provider.list(eq(FlowHostCatalogDimension.ROLE), any(), anyInt())).thenReturn(
                List.of(FlowHostCatalogItem.builder().value("should-not-see").build()));

        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(provider);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, null);

        assertTrue(svc.search(FlowHostCatalogDimension.ROLE, null, 10).isEmpty());
    }

    @Test
    void parseDimension_rejectsUnknown() {
        FlowException ex = assertThrows(FlowException.class, () -> FlowHostCatalogDimension.parse("FOO"));
        assertTrue(ex.getMessage().contains("dimension"));
    }

    @Test
    void clampLimit() {
        assertEquals(50, FlowHostIdentityCatalogService.clampLimit(null));
        assertEquals(50, FlowHostIdentityCatalogService.clampLimit(0));
        assertEquals(200, FlowHostIdentityCatalogService.clampLimit(999));
        assertEquals(20, FlowHostIdentityCatalogService.clampLimit(20));
    }

    @Test
    void providerException_degradesEmpty() {
        FlowHostIdentityCatalogProvider provider = mock(FlowHostIdentityCatalogProvider.class);
        when(provider.supports(any())).thenReturn(true);
        when(provider.searchable(any())).thenReturn(false);
        when(provider.list(any(), any(), anyInt())).thenThrow(new RuntimeException("host down"));

        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(provider);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, null);

        HostIdentityCatalogDTO snap = svc.snapshot();
        assertTrue(snap.isAvailable());
        assertEquals(1, snap.getDimensions().get("USER_TYPE").getItems().size()); // 仅引擎补的 OPEN_APP
        assertEquals("OPEN_APP", snap.getDimensions().get("USER_TYPE").getItems().get(0).getValue());
    }

    @Test
    void reservedApiFallback_enabledAndPublished() {
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        HostCatalogApiExecutor executor = mock(HostCatalogApiExecutor.class);
        when(executor.listPreferPublished(eq(FlowHostCatalogDimension.USER_TYPE), any(), anyInt()))
                .thenReturn(List.of(new HostIdentityCatalogItemDTO().setValue("STAFF").setLabel("员工")));
        HostIdentityCatalogSettingsStore store = mock(HostIdentityCatalogSettingsStore.class);
        HostIdentityCatalogSettings settings = new HostIdentityCatalogSettings();
        HostCatalogDimBinding ut = HostCatalogDimBinding.disabledDefault();
        ut.setEnabled(true);
        settings.put(FlowHostCatalogDimension.USER_TYPE, ut);
        when(store.load()).thenReturn(settings);
        when(store.loadFreshRequired()).thenReturn(settings);

        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, executor, store);
        HostIdentityCatalogDTO snap = svc.snapshot();
        assertTrue(snap.isAvailable());
        assertTrue(snap.getDimensions().get("USER_TYPE").isSupported());
        assertEquals("STAFF", snap.getDimensions().get("USER_TYPE").getItems().get(0).getValue());
        assertEquals("OPEN_APP", snap.getDimensions().get("USER_TYPE").getItems().get(1).getValue());
        assertFalse(snap.getDimensions().get("ROLE").isSupported());
    }

    @Test
    void restrictToActiveDimensions_preservesSavedConstraintsWhenCatalogDisabled() {
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        HostIdentityCatalogSettingsStore store = mock(HostIdentityCatalogSettingsStore.class);
        HostIdentityCatalogSettings settings = new HostIdentityCatalogSettings();
        HostCatalogDimBinding ut = HostCatalogDimBinding.disabledDefault();
        ut.setEnabled(true);
        settings.put(FlowHostCatalogDimension.USER_TYPE, ut);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, store);
        CallerPolicy policy = new CallerPolicy();
        policy.setEnabled(true);
        policy.setUserTypes(List.of("ADMIN"));
        policy.setRoles(List.of("vip"));
        policy.setDeptIds(List.of("dept-1"));

        CallerPolicy effective = svc.restrictToActiveDimensions(policy);
        assertEquals(List.of("ADMIN"), effective.getUserTypes());
        assertEquals(List.of("vip"), effective.getRoles());
        assertEquals(List.of("dept-1"), effective.getDeptIds());
        assertEquals(List.of("vip"), policy.getRoles());
    }

    @Test
    void applyDeptTree_expandsChildrenWhenEnabled() {
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        HostCatalogApiExecutor executor = mock(HostCatalogApiExecutor.class);
        when(executor.listPreferPublished(eq(FlowHostCatalogDimension.DEPT), any(), anyInt()))
                .thenReturn(List.of(
                        new HostIdentityCatalogItemDTO().setValue("hq").setLabel("总部"),
                        new HostIdentityCatalogItemDTO().setValue("rd").setLabel("研发").setParentId("hq")
                ));
        HostIdentityCatalogSettingsStore store = mock(HostIdentityCatalogSettingsStore.class);
        HostIdentityCatalogSettings settings = new HostIdentityCatalogSettings();
        HostCatalogDimBinding dept = HostCatalogDimBinding.disabledDefault();
        dept.setEnabled(true);
        settings.put(FlowHostCatalogDimension.DEPT, dept);
        when(store.load()).thenReturn(settings);
        when(store.loadFreshRequired()).thenReturn(settings);

        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, executor, store);
        CallerPolicy policy = new CallerPolicy();
        policy.setEnabled(true);
        policy.setDeptIds(List.of("hq"));
        CallerPolicy effective = svc.effectivePolicy(policy);
        CallerPolicy effectiveAgain = svc.effectivePolicy(policy);
        assertTrue(effective.getDeptIds().contains("hq"));
        assertTrue(effective.getDeptIds().contains("rd"));
        assertSame(effective, effectiveAgain);
        assertEquals(effective.getDeptIds(), effectiveAgain.getDeptIds());
        verify(executor, times(1)).listPreferPublished(
                eq(FlowHostCatalogDimension.DEPT), any(), anyInt());
    }

    @Test
    void runtimeDimensionFilter_doesNotLoadCatalogOrSettings() {
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        HostCatalogApiExecutor executor = mock(HostCatalogApiExecutor.class);
        HostIdentityCatalogSettingsStore store = mock(HostIdentityCatalogSettingsStore.class);
        HostIdentityCatalogSettings settings = new HostIdentityCatalogSettings();
        HostCatalogDimBinding role = HostCatalogDimBinding.disabledDefault();
        role.setEnabled(true);
        settings.put(FlowHostCatalogDimension.ROLE, role);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, executor, store);
        CallerPolicy policy = new CallerPolicy();
        policy.setRoles(List.of("admin"));

        assertEquals(List.of("admin"), svc.restrictToActiveDimensions(policy).getRoles());
        assertEquals(List.of("admin"), svc.restrictToActiveDimensions(policy).getRoles());
        verifyNoInteractions(store);
        verifyNoInteractions(executor);
    }

    @Test
    void restrictToActiveDimensions_keepsConstraintsWhenNoCatalogEnabled() {
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, null);
        CallerPolicy policy = new CallerPolicy();
        policy.setRoles(List.of("vip"));
        policy.setUserTypes(List.of("ADMIN"));
        CallerPolicy effective = svc.restrictToActiveDimensions(policy);
        // 目录未启用不等于放开限制：已保存的约束保持原样，否则会把策略越改越松
        assertEquals(List.of("vip"), effective.getRoles());
        assertEquals(List.of("ADMIN"), effective.getUserTypes());
    }

    @Test
    void runtimeConfigFailure_isFailClosed() {
        ObjectProvider<FlowHostIdentityCatalogProvider> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        HostIdentityCatalogSettingsStore store = mock(HostIdentityCatalogSettingsStore.class);
        when(store.loadFreshRequired()).thenThrow(
                new FlowException("HOST_CATALOG_UNAVAILABLE", "unavailable"));
        FlowHostIdentityCatalogService svc = new FlowHostIdentityCatalogService(op, null, store);
        CallerPolicy policy = new CallerPolicy();
        policy.setEnabled(true);
        policy.setDeptIds(List.of("dept-1"));

        assertThrows(FlowException.class, () -> svc.effectivePolicy(policy));
    }
}
