package org.yu.flow.module.transfer.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigTransferPolicyTest {

    @Test
    void businessConfigsMigrate() {
        assertNull(ConfigTransferPolicy.rejectReason("SITE_TITLE", "GENERAL"));
        assertNull(ConfigTransferPolicy.rejectReason("ASSET_VERSION_RETENTION_COUNT", "FLOW"));
        assertNull(ConfigTransferPolicy.rejectReason("ORDER_TIMEOUT_MINUTES", "BIZ", null));
    }

    @Test
    void secretsAndSecurityPoliciesStay() {
        assertNotNull(ConfigTransferPolicy.rejectReason("MAIL_PASSWORD", "MAIL"));
        assertNotNull(ConfigTransferPolicy.rejectReason("LOGIN_MAX_RETRY", "SECURITY"));
        assertNotNull(ConfigTransferPolicy.rejectReason("INGRESS_DEFAULT_AUTH_MODE", "INGRESS"));
        assertNotNull(ConfigTransferPolicy.rejectReason("SYSTEM_PREFIX", "GATEWAY"));
        assertNotNull(ConfigTransferPolicy.rejectReason(null));
    }

    @Test
    void relabelledGroupDoesNotBypass() {
        // 包里改成 GENERAL，但目标环境同名配置属于 SECURITY
        assertNotNull(ConfigTransferPolicy.rejectReason("TOKEN_EXPIRE", "GENERAL", "SECURITY"));
        // 目标环境还没有这项配置时，按内置键前缀兜底
        assertNotNull(ConfigTransferPolicy.rejectReason("OPEN_ALLOW_PLAIN_SECRET", "GENERAL", null));
        assertNotNull(ConfigTransferPolicy.rejectReason("RBAC_ENABLED", "GENERAL", null));
    }
}
