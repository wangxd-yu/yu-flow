package org.yu.flow.module.directory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yu.flow.module.api.security.ApiSecurityConfig;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.repository.FlowDirectoryRepository;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowDirectoryServiceImplTest {

    private FlowDirectoryRepository directoryRepository;
    private FlowDirectoryServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        directoryRepository = mock(FlowDirectoryRepository.class);
        service = new FlowDirectoryServiceImpl();
        setField(service, "directoryRepository", directoryRepository);
    }

    @Test
    void resolveDirectorySecurity_walksToRootAndCachesFindAll() {
        FlowDirectoryDO root = dir("root", null,
                "{\"callerPolicy\":{\"enabled\":true,\"roles\":[\"admin\"]}}");
        FlowDirectoryDO child = dir("child", "root",
                "{\"authMode\":\"HOST\",\"antiReplay\":true,\"rateLimitEnabled\":true,"
                        + "\"rateLimitQps\":10,\"ipAllowlist\":\"1.1.1.1\",\"timeoutMs\":1000}");
        when(directoryRepository.findAll()).thenReturn(List.of(root, child));

        ApiSecurityConfig first = service.resolveDirectorySecurityOverrides("child");
        ApiSecurityConfig second = service.resolveDirectorySecurityOverrides("child");

        assertEquals("HOST", first.getAuthMode());
        assertNotNull(first.getCallerPolicy());
        assertTrue(first.getCallerPolicy().isEnabled());
        assertEquals(List.of("admin"), first.getCallerPolicy().getRoles());
        assertSame(first, second);
        verify(directoryRepository, times(1)).findAll();
        verify(directoryRepository, never()).findById(any());
    }

    @Test
    void resolveEffectivePathPrefix_joinsRootToLeafInMemory() {
        FlowDirectoryDO root = dir("root", null, null);
        root.setPathPrefix("/api/public");
        FlowDirectoryDO child = dir("child", "root", null);
        child.setPathPrefix("/v1");
        when(directoryRepository.findAll()).thenReturn(List.of(root, child));

        assertEquals("/api/public/v1", service.resolveEffectivePathPrefix("child"));
        assertEquals("/api/public/v1", service.resolveEffectivePathPrefix("child"));
        verify(directoryRepository, times(1)).findAll();
    }

    @Test
    void childCallerPolicy_overridesParent() {
        FlowDirectoryDO root = dir("root", null,
                "{\"callerPolicy\":{\"enabled\":true,\"roles\":[\"parent\"]}}");
        FlowDirectoryDO child = dir("child", "root",
                "{\"callerPolicy\":{\"enabled\":true,\"roles\":[\"child\"]}}");
        when(directoryRepository.findAll()).thenReturn(List.of(root, child));

        ApiSecurityConfig merged = service.resolveDirectorySecurityOverrides("child");
        assertEquals(List.of("child"), merged.getCallerPolicy().getRoles());
    }

    @Test
    void resolveDirectoryPrivacy_childOverridesThenStopsInheritFalse() {
        FlowDirectoryDO root = dir("root", null, null);
        root.setPrivacyConfig("{\"enabled\":true,\"fieldSuffix\":\"_enc\"}");
        FlowDirectoryDO child = dir("child", "root", null);
        child.setPrivacyConfig("{\"enabled\":true,\"inherit\":false,\"fieldSuffix\":\"_sec\"}");
        when(directoryRepository.findAll()).thenReturn(List.of(root, child));

        var merged = service.resolveDirectoryPrivacyOverrides("child");
        assertEquals(Boolean.TRUE, merged.getEnabled());
        assertEquals("_sec", merged.getFieldSuffix());
    }

    @Test
    void getAllChildIds_usesChildIndexAndStopsOnCycle() {
        FlowDirectoryDO a = dir("a", "c", null);
        FlowDirectoryDO b = dir("b", "a", null);
        FlowDirectoryDO c = dir("c", "b", null);
        when(directoryRepository.findAll()).thenReturn(List.of(a, b, c));

        assertEquals(List.of("a", "b", "c"), service.getAllChildIds("a"));
        verify(directoryRepository, times(1)).findAll();
    }

    @Test
    void getAllChildIds_includesSelfWhenLeaf() {
        FlowDirectoryDO leaf = dir("leaf", null, null);
        when(directoryRepository.findAll()).thenReturn(List.of(leaf));

        assertEquals(List.of("leaf"), service.getAllChildIds("leaf"));
    }

    @Test
    void getAllChildIds_includesSelfThenDescendants() {
        FlowDirectoryDO root = dir("root", null, null);
        FlowDirectoryDO child = dir("child", "root", null);
        when(directoryRepository.findAll()).thenReturn(List.of(root, child));

        assertEquals(List.of("root", "child"), service.getAllChildIds("root"));
        assertEquals(List.of("child"), service.getAllChildIds("child"));
    }

    private static FlowDirectoryDO dir(String id, String parentId, String securityConfig) {
        FlowDirectoryDO d = new FlowDirectoryDO();
        d.setId(id);
        d.setParentId(parentId);
        d.setName(id);
        d.setSecurityConfig(securityConfig);
        return d;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
