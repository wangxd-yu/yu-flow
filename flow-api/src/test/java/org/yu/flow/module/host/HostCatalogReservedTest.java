package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.domain.FlowApiDO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostCatalogReservedTest {

    @Test
    void fixedIdsAndUrls() {
        assertEquals(6, HostCatalogReserved.ids().size());
        assertTrue(HostCatalogReserved.isReservedId(HostCatalogReserved.DIR_USER_TYPE));
        assertTrue(HostCatalogReserved.isReservedUrl("/__sys/host-catalog/user-type"));
        assertFalse(HostCatalogReserved.isReservedUrl("/flow-api/orders"));
        assertEquals(FlowHostCatalogDimension.USER_TYPE,
                HostCatalogReserved.dimensionOfId(HostCatalogReserved.DIR_USER_TYPE));
    }

    @Test
    void principalResolverIsReservedButNotADimension() {
        assertTrue(HostCatalogReserved.isReservedId(HostCatalogReserved.DIR_PRINCIPAL));
        assertTrue(HostCatalogReserved.isReservedUrl("/__sys/host-principal/resolve"));
        assertNull(HostCatalogReserved.dimensionOfId(HostCatalogReserved.DIR_PRINCIPAL));
        assertEquals(HostCatalogReserved.PRINCIPAL,
                HostCatalogReserved.specOfId(HostCatalogReserved.DIR_PRINCIPAL));
        assertThrows(FlowException.class,
                () -> HostCatalogLocks.assertNotDeleted(HostCatalogReserved.DIR_PRINCIPAL));
    }

    @Test
    void locksRejectDeleteAndMove() {
        assertThrows(FlowException.class, () -> HostCatalogLocks.assertNotDeleted(HostCatalogReserved.DIR_ROLE));
        assertThrows(FlowException.class, () -> HostCatalogLocks.assertNotMoved(List.of(HostCatalogReserved.DIR_USER)));
        HostCatalogLocks.assertNotDeleted("api_normal");
    }

    @Test
    void updateForcesReservedIdentity() {
        FlowApiDO existing = new FlowApiDO();
        existing.setId(HostCatalogReserved.DIR_USER_TYPE);
        FlowApiDO incoming = new FlowApiDO();
        incoming.setId(HostCatalogReserved.DIR_USER_TYPE);
        incoming.setUrl("/changed");
        incoming.setServiceType("JSON");
        incoming.setInterceptMode("REPLACE");
        incoming.setDirectoryId("dir-1");
        HostCatalogLocks.assertUpdateAllowed(incoming, existing);
        assertEquals("/__sys/host-catalog/user-type", incoming.getUrl());
        assertEquals("GET", incoming.getMethod());
        assertNull(incoming.getDirectoryId());
    }

    @Test
    void updateRejectsNonGetMethod() {
        FlowApiDO existing = new FlowApiDO();
        existing.setId(HostCatalogReserved.DIR_USER_TYPE);
        FlowApiDO incoming = new FlowApiDO();
        incoming.setId(HostCatalogReserved.DIR_USER_TYPE);
        incoming.setUrl("/__sys/host-catalog/user-type");
        incoming.setMethod("POST");
        incoming.setServiceType("JSON");
        incoming.setInterceptMode("REPLACE");
        assertThrows(FlowException.class, () -> HostCatalogLocks.assertUpdateAllowed(incoming, existing));
    }

    @Test
    void updateRejectsWrap() {
        FlowApiDO existing = new FlowApiDO();
        existing.setId(HostCatalogReserved.DIR_ROLE);
        FlowApiDO incoming = new FlowApiDO();
        incoming.setId(HostCatalogReserved.DIR_ROLE);
        incoming.setUrl("/__sys/host-catalog/role");
        incoming.setMethod("GET");
        incoming.setServiceType("HOST");
        incoming.setInterceptMode("WRAP");
        assertThrows(FlowException.class, () -> HostCatalogLocks.assertUpdateAllowed(incoming, existing));
    }

    @Test
    void snowIdGeneratorKeepsAssignedId() {
        FlowApiDO api = new FlowApiDO();
        api.setId(HostCatalogReserved.DIR_USER_TYPE);
        org.yu.flow.auto.util.SnowIdGenerator gen = new org.yu.flow.auto.util.SnowIdGenerator();
        assertEquals(HostCatalogReserved.DIR_USER_TYPE, gen.generate(null, api));
    }
}
