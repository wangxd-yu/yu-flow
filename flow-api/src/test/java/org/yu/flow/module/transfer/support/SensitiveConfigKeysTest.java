package org.yu.flow.module.transfer.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveConfigKeysTest {

    @Test
    void secretsAreSensitive() {
        assertTrue(SensitiveConfigKeys.isSensitive("MAIL_PASSWORD"));
        assertTrue(SensitiveConfigKeys.isSensitive("OSS_SECRET_KEY"));
        assertTrue(SensitiveConfigKeys.isSensitive("ALERT_WEBHOOK_URL"));
        assertTrue(SensitiveConfigKeys.isSensitive("PRIVACY_SM4_KEY"));
        assertTrue(SensitiveConfigKeys.isSensitive(null));
    }

    @Test
    void durationsAndSwitchesAreNot() {
        assertFalse(SensitiveConfigKeys.isSensitive("TOKEN_EXPIRE"));
        assertFalse(SensitiveConfigKeys.isSensitive("TOKEN_REFRESH_EXPIRE"));
        assertFalse(SensitiveConfigKeys.isSensitive("SITE_TITLE"));
        assertFalse(SensitiveConfigKeys.isSensitive("API_TIMEOUT"));
    }
}
