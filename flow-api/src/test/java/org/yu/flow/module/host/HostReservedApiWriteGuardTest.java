package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.service.RbacService;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostReservedApiWriteGuardTest {

    @Test
    void reservedApi_requiresHostWriteWhenRbacEnabled() throws Exception {
        RbacService rbacService = mock(RbacService.class);
        when(rbacService.isRbacEnabled()).thenReturn(true);
        HostReservedApiWriteGuard guard = new HostReservedApiWriteGuard();
        setField(guard, "rbacService", rbacService);

        assertThrows(FlowException.class, () -> guard.assertAllowed(HostCatalogReserved.DIR_ROLE));
        assertDoesNotThrow(() -> guard.assertAllowed("normal-api"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
