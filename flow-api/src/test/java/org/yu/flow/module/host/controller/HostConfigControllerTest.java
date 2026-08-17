package org.yu.flow.module.host.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.host.FlowHostCatalogDimension;
import org.yu.flow.module.host.FlowHostIdentityCatalogProvider;
import org.yu.flow.module.host.FlowHostIdentityCatalogService;
import org.yu.flow.module.host.HostCatalogApiBootstrap;
import org.yu.flow.module.host.HostCatalogApiExecutor;
import org.yu.flow.module.host.HostCatalogDimBinding;
import org.yu.flow.module.host.HostIdentityCatalogSettings;
import org.yu.flow.module.host.HostIdentityCatalogSettingsStore;
import org.yu.flow.module.host.dto.SaveHostCatalogSettingsDTO;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HostConfigControllerTest {

    @Test
    void save_keepsDepartmentParentFieldMapping() throws Exception {
        HostIdentityCatalogSettingsStore store = mock(HostIdentityCatalogSettingsStore.class);
        when(store.load()).thenReturn(new HostIdentityCatalogSettings());
        when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FlowApiRepository repository = mock(FlowApiRepository.class);
        when(repository.findAllById(any())).thenReturn(List.of());

        HostConfigController controller = new HostConfigController();
        setField(controller, "settingsStore", store);
        setField(controller, "hostCatalogApiExecutor", mock(HostCatalogApiExecutor.class));
        setField(controller, "flowApiRepository", repository);
        setField(controller, "catalogProvider", mock(ObjectProvider.class));
        setField(controller, "hostIdentityCatalogService", mock(FlowHostIdentityCatalogService.class));
        setField(controller, "hostCatalogApiBootstrap", mock(HostCatalogApiBootstrap.class));

        HostCatalogDimBinding dept = HostCatalogDimBinding.disabledDefault();
        dept.setEnabled(true);
        dept.setParentField("pid");
        SaveHostCatalogSettingsDTO body = new SaveHostCatalogSettingsDTO();
        body.setSettings(Map.of(FlowHostCatalogDimension.DEPT.name(), dept));

        controller.save(body);

        ArgumentCaptor<HostIdentityCatalogSettings> captor =
                ArgumentCaptor.forClass(HostIdentityCatalogSettings.class);
        verify(store).save(captor.capture());
        assertEquals("pid", captor.getValue().get(FlowHostCatalogDimension.DEPT).getParentField());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
