package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HostCatalogApiExecutorTest {

    @Test
    void listPreferPublished_queriesReservedApiOnlyOnce() throws Exception {
        FlowApiRepository repository = mock(FlowApiRepository.class);
        FlowApiExecutionService executionService = mock(FlowApiExecutionService.class);
        HostIdentityCatalogSettingsStore settingsStore = mock(HostIdentityCatalogSettingsStore.class);
        FlowApiDO api = new FlowApiDO();
        api.setId(HostCatalogReserved.spec(FlowHostCatalogDimension.ROLE).id());
        api.setPublishStatus(1);
        api.setPublishedSnapshot("{\"serviceType\":\"JSON\",\"responseType\":\"LIST\","
                + "\"jsonContent\":\"[{\\\"value\\\":\\\"admin\\\",\\\"label\\\":\\\"管理员\\\"}]\"}");
        when(repository.findById(api.getId())).thenReturn(Optional.of(api));
        when(executionService.executeApi(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("value", "admin", "label", "管理员")));
        when(settingsStore.load()).thenReturn(enabledSettings(FlowHostCatalogDimension.ROLE));

        HostCatalogApiExecutor executor = new HostCatalogApiExecutor();
        setField(executor, "flowApiRepository", repository);
        setField(executor, "flowApiExecutionService", executionService);
        setField(executor, "settingsStore", settingsStore);

        List<HostIdentityCatalogItemDTO> items =
                executor.listPreferPublished(FlowHostCatalogDimension.ROLE, null, 50);

        assertEquals(List.of("admin"), items.stream().map(HostIdentityCatalogItemDTO::getValue).toList());
        verify(repository, times(1)).findById(api.getId());
    }

    @Test
    void publishedExecution_usesSnapshotFieldsInsteadOfDraft() throws Exception {
        FlowApiRepository repository = mock(FlowApiRepository.class);
        FlowApiExecutionService executionService = mock(FlowApiExecutionService.class);
        HostIdentityCatalogSettingsStore settingsStore = mock(HostIdentityCatalogSettingsStore.class);
        FlowApiDO api = new FlowApiDO();
        api.setId(HostCatalogReserved.DIR_ROLE);
        api.setPublishStatus(1);
        api.setServiceType("DB");
        api.setResponseType("UPDATE");
        api.setSqlContent("UPDATE dangerous SET value=1");
        api.setPublishedSnapshot("{\"serviceType\":\"JSON\",\"responseType\":\"LIST\","
                + "\"jsonContent\":\"[{\\\"value\\\":\\\"safe\\\"}]\"}");
        when(repository.findById(api.getId())).thenReturn(Optional.of(api));
        when(executionService.executeApi(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("value", "safe")));
        when(settingsStore.load()).thenReturn(enabledSettings(FlowHostCatalogDimension.ROLE));

        HostCatalogApiExecutor executor = new HostCatalogApiExecutor();
        setField(executor, "flowApiRepository", repository);
        setField(executor, "flowApiExecutionService", executionService);
        setField(executor, "settingsStore", settingsStore);

        assertEquals(1, executor.listPreferPublished(FlowHostCatalogDimension.ROLE, null, 50).size());
        ArgumentCaptor<FlowApiDO> captor = ArgumentCaptor.forClass(FlowApiDO.class);
        verify(executionService).executeApi(captor.capture(), any(), any(), any());
        assertEquals("JSON", captor.getValue().getServiceType());
        assertEquals("LIST", captor.getValue().getResponseType());
        assertEquals("[{\"value\":\"safe\"}]", captor.getValue().getJsonContent());
    }

    private static HostIdentityCatalogSettings enabledSettings(FlowHostCatalogDimension dimension) {
        HostIdentityCatalogSettings settings = new HostIdentityCatalogSettings();
        HostCatalogDimBinding binding = HostCatalogDimBinding.disabledDefault();
        binding.setEnabled(true);
        settings.put(dimension, binding);
        return settings;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
