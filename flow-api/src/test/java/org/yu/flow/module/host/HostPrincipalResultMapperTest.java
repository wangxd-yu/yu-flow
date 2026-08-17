package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;
import org.yu.flow.dto.R;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPrincipalResultMapperTest {

    private static HostPrincipalSettings settings() {
        return new HostPrincipalSettings();
    }

    @Test
    void unwrapsWrappedSingleRow() {
        Object raw = R.ok(List.of(Map.of("userId", "u1", "username", "张三")));
        Map<String, Object> row = HostPrincipalResultMapper.firstRow(raw);
        assertEquals("u1", row.get("userId"));
    }

    @Test
    void mapsCustomFieldNames() {
        HostPrincipalSettings s = settings();
        s.setFields(Map.of("userId", "uid", "userType", "kind", "roles", "roleCodes"));
        Map<String, Object> row = Map.of(
                "uid", "1001", "kind", "END_USER", "roleCodes", List.of("vip", "buyer"));

        FlowHostPrincipal principal = HostPrincipalResultMapper.toPrincipal(
                row, s, HostPrincipalSettings.CHANNEL_API);

        assertEquals("1001", principal.getUserId());
        assertEquals("END_USER", principal.getUserType());
        assertEquals(Set.of("vip", "buyer"), principal.getRoles());
        // username 缺省回落 userId，避免下游按空用户名鉴权
        assertEquals("1001", principal.getUsername());
    }

    @Test
    void acceptsSnakeCaseAndCsv() {
        Map<String, Object> row = Map.of(
                "user_id", "1001", "user_type", "END_USER", "permissions", "order:read, order:write");

        FlowHostPrincipal principal = HostPrincipalResultMapper.toPrincipal(
                row, settings(), HostPrincipalSettings.CHANNEL_API);

        assertEquals("1001", principal.getUserId());
        assertEquals("END_USER", principal.getUserType());
        assertEquals(Set.of("order:read", "order:write"), principal.getPermissions());
    }

    @Test
    void missingUserIdYieldsNoPrincipal() {
        assertNull(HostPrincipalResultMapper.toPrincipal(
                Map.of("username", "张三"), settings(), HostPrincipalSettings.CHANNEL_API));
        assertNull(HostPrincipalResultMapper.toPrincipal(
                null, settings(), HostPrincipalSettings.CHANNEL_API));
        assertNull(HostPrincipalResultMapper.firstRow(List.of()));
    }

    @Test
    void adminUserTypeMatchIsCaseInsensitive() {
        HostPrincipalSettings s = settings();
        s.setAdminUserTypes(List.of("OPERATOR", " admin "));

        assertTrue(s.isAdminUserType("operator"));
        assertTrue(s.isAdminUserType("ADMIN"));
        assertFalse(s.isAdminUserType("END_USER"));
        assertFalse(s.isAdminUserType(null));
    }

    @Test
    void cacheSecondsIsClamped() {
        HostPrincipalSettings s = settings();
        s.setCacheSeconds(-5);
        assertEquals(0, s.resolvedCacheSeconds());
        s.setCacheSeconds(99999);
        assertEquals(600, s.resolvedCacheSeconds());
    }
}
