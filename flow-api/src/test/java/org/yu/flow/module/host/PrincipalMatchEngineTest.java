package org.yu.flow.module.host;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrincipalMatchEngineTest {

    @Test
    void anyAuthenticated_excludesOpenApp() {
        PrincipalMatch rule = new PrincipalMatch();
        rule.setPrincipals(PrincipalMatch.PRINCIPALS_ANY);
        assertTrue(PrincipalMatchEngine.matches(rule, hostUser("u1", "STAFF", Set.of("user"))));
        assertFalse(PrincipalMatchEngine.matches(rule, openApp("app1")));
        assertFalse(PrincipalMatchEngine.matches(rule, null));
    }

    @Test
    void openApp_matchesOnlyOpenRow() {
        PrincipalMatch any = new PrincipalMatch();
        any.setPrincipals(PrincipalMatch.PRINCIPALS_ANY);
        PrincipalMatch open = new PrincipalMatch();
        open.setPrincipals(PrincipalMatch.PRINCIPALS_OPEN);
        PrincipalMatch matchStaff = new PrincipalMatch();
        matchStaff.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        matchStaff.setUserTypes(List.of("STAFF"));
        FlowHostPrincipal app = openApp("shop");
        assertFalse(PrincipalMatchEngine.matches(any, app));
        assertTrue(PrincipalMatchEngine.matches(open, app));
        assertFalse(PrincipalMatchEngine.matches(matchStaff, app));
    }

    @Test
    void openApp_canLimitAppKey() {
        PrincipalMatch open = new PrincipalMatch();
        open.setPrincipals(PrincipalMatch.PRINCIPALS_OPEN);
        open.setUserIds(List.of("shop"));
        assertTrue(PrincipalMatchEngine.matches(open, openApp("shop")));
        assertTrue(PrincipalMatchEngine.matches(open, openApp("open:shop")));
        assertFalse(PrincipalMatchEngine.matches(open, openApp("other")));
    }

    @Test
    void match_rolesCaseInsensitive() {
        PrincipalMatch rule = new PrincipalMatch();
        rule.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        rule.setRoles(List.of("PrivacyAdmin"));
        FlowHostPrincipal p = FlowHostPrincipal.builder()
                .userId("u1")
                .roles(Set.of("privacyadmin"))
                .authChannel("HOST_RESOLVER_API")
                .build();
        assertTrue(PrincipalMatchEngine.matches(rule, p));
    }

    @Test
    void match_adminUserType() {
        PrincipalMatch rule = new PrincipalMatch();
        rule.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        rule.setUserTypes(List.of("ADMIN"));
        assertTrue(PrincipalMatchEngine.matches(rule, hostUser("a1", "ADMIN", Set.of())));
        assertFalse(PrincipalMatchEngine.matches(rule, hostUser("u1", "END_USER", Set.of())));
    }

    @Test
    void match_emptyConstraint_false() {
        PrincipalMatch rule = new PrincipalMatch();
        rule.setPrincipals(PrincipalMatch.PRINCIPALS_MATCH);
        assertFalse(PrincipalMatchEngine.matches(rule, hostUser("u1", "STAFF", Set.of())));
    }

    private static FlowHostPrincipal hostUser(String id, String type, Set<String> roles) {
        return FlowHostPrincipal.builder()
                .userId(id)
                .userType(type)
                .roles(roles)
                .authChannel("HOST_RESOLVER_API")
                .build();
    }

    private static FlowHostPrincipal openApp(String appKey) {
        String id = appKey.startsWith("open:") ? appKey : "open:" + appKey;
        return FlowHostPrincipal.builder()
                .userId(id)
                .username(appKey)
                .userType(FlowHostPrincipal.TYPE_OPEN_APP)
                .authChannel("OPEN_APP")
                .build();
    }
}
