package org.yu.flow.module.api.privacy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrivacyMaskerTest {

    @Test
    void maskPhone_keepsFirst3Last4() {
        assertEquals("138****1234", PrivacyMasker.maskPhone("13812341234"));
        assertEquals("138****1234", PrivacyMasker.maskPhone("138 1234 1234"));
        assertEquals("138*****2345", PrivacyMasker.maskPhone("138123412345"));
    }

    @Test
    void maskName_twoAndThreePlus() {
        assertEquals("*华", PrivacyMasker.maskName("李华"));
        assertEquals("张*丰", PrivacyMasker.maskName("张三丰"));
        assertEquals("欧*娜", PrivacyMasker.maskName("欧阳娜"));
        assertEquals("*", PrivacyMasker.maskName("王"));
    }

    @Test
    void maskIdCard_keepsFirstAndLast() {
        assertEquals("3****************X", PrivacyMasker.maskIdCard("31010119900101123X"));
    }

    @Test
    void mask_byAliasAndFallback() {
        Map<String, java.util.List<String>> aliases = PrivacyConfigMerge.defaultMask();
        assertEquals("138****1234", PrivacyMasker.mask("13812341234", "mobile", aliases));
        assertEquals("张*丰", PrivacyMasker.mask("张三丰", "realName", aliases));
        assertEquals("3****************X", PrivacyMasker.mask("31010119900101123X", "idNo", aliases));
        assertEquals("s*****", PrivacyMasker.mask("secret", "remark", aliases));
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "loginPhone", aliases));
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "authPhone", aliases));
        assertEquals("****", PrivacyMasker.placeholder());
    }

    @Test
    void mask_unmatchedKeepsFirstChar() {
        Map<String, java.util.List<String>> aliases = PrivacyConfigMerge.defaultMask();
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "userPhone", aliases));
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "home_phone", aliases));
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "loginPhone", java.util.List.of()));
        assertEquals("张**", PrivacyMasker.mask("张三丰", "other", java.util.List.of()));
        assertEquals("王", PrivacyMasker.keepFirst("王"));
    }

    @Test
    void mask_keepHeadTailConfigurable() {
        PrivacyMaskRule phone = new PrivacyMaskRule();
        phone.setAliases(java.util.List.of("mobile"));
        phone.setMethod(PrivacyMaskRule.KEEP_HEAD_TAIL);
        phone.setKeepHead(3);
        phone.setKeepTail(4);
        phone.setMaskLen(4);

        PrivacyMaskRule name = new PrivacyMaskRule();
        name.setAliases(java.util.List.of("realName"));
        name.setMethod(PrivacyMaskRule.NAME_KEEP_ENDS);

        java.util.List<PrivacyMaskRule> rules = java.util.List.of(phone, name);
        assertEquals("138****1234", PrivacyMasker.mask("13812341234", "mobile", rules));
        assertEquals("138*****2345", PrivacyMasker.mask("138123412345", "mobile", rules));
        assertEquals("张*丰", PrivacyMasker.mask("张三丰", "realName", rules));
        assertEquals("s*****", PrivacyMasker.mask("secret", "other", rules));
    }

    @Test
    void mask_containsSubstring() {
        PrivacyMaskRule phone = new PrivacyMaskRule();
        phone.setMatchMode(PrivacyMaskRule.MATCH_CONTAINS);
        phone.setAliases(java.util.List.of("phone", "mobile"));
        phone.setMethod(PrivacyMaskRule.PHONE);

        java.util.List<PrivacyMaskRule> rules = java.util.List.of(phone);
        assertEquals("138****1234", PrivacyMasker.mask("13812341234", "userPhone", rules));
        assertEquals("138****1234", PrivacyMasker.mask("13812341234", "home_phone", rules));
        assertEquals("138****1234", PrivacyMasker.mask("13812341234", "phoneNumber", rules));
        assertEquals("s*****", PrivacyMasker.mask("secret", "remark", rules));
    }

    @Test
    void exact_doesNotTreatMetacharAsRegex() {
        PrivacyMaskRule rule = new PrivacyMaskRule();
        rule.setMatchMode(PrivacyMaskRule.MATCH_EXACT);
        rule.setAliases(java.util.List.of("phone"));
        rule.setMethod(PrivacyMaskRule.PHONE);
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "userPhone", java.util.List.of(rule)));
        assertEquals("138****1234", PrivacyMasker.mask("13812341234", "phone", java.util.List.of(rule)));
    }

    @Test
    void regex_stillHonoredFromJson() {
        PrivacyMaskRule rule = new PrivacyMaskRule();
        rule.setMatchMode(PrivacyMaskRule.MATCH_REGEX);
        rule.setAliases(java.util.List.of(".*phone$"));
        rule.setMethod(PrivacyMaskRule.PHONE);
        assertEquals("138****1234", PrivacyMasker.mask("13812341234", "userPhone", java.util.List.of(rule)));
        assertEquals("1**********", PrivacyMasker.mask("13812341234", "phoneNumber", java.util.List.of(rule)));
    }
}
