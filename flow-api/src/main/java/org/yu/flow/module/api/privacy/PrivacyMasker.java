package org.yu.flow.module.api.privacy;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 解密后的展示脱敏：命中方案/目录规则则按规则；未命中则只留第一位。
 */
public final class PrivacyMasker {

    public static final String PHONE = "phone";
    public static final String NAME = "name";
    public static final String ID_CARD = "idCard";

    private PrivacyMasker() {
    }

    public static String mask(String plain, String outputKey, Map<String, List<String>> aliases) {
        return mask(plain, outputKey, rulesFromAliasMap(aliases));
    }

    public static String mask(String plain, String outputKey, List<PrivacyMaskRule> rules) {
        if (plain == null) {
            return null;
        }
        PrivacyMaskRule rule = matchRule(outputKey, rules);
        if (rule == null) {
            return keepFirst(plain);
        }
        return apply(plain, rule);
    }

    public static String placeholder() {
        return "****";
    }

    /** 未配置规则时的默认展示：只留第一位，其余每位一个 {@code *}，长度与原文一致。 */
    public static String keepFirst(String raw) {
        if (raw == null) {
            return null;
        }
        String s = compactIfContact(raw);
        if (s.isEmpty()) {
            return placeholder();
        }
        int n = s.codePointCount(0, s.length());
        int first = s.offsetByCodePoints(0, 1);
        return s.substring(0, first) + "*".repeat(n - 1);
    }

    public static List<PrivacyMaskRule> defaultRules() {
        return rulesFromAliasMap(PrivacyConfigMerge.defaultMask());
    }

    public static List<PrivacyMaskRule> rulesFromAliasMap(Map<String, List<String>> aliases) {
        List<PrivacyMaskRule> rules = new ArrayList<>();
        if (aliases == null) {
            return rules;
        }
        aliases.forEach((type, names) -> {
            PrivacyMaskRule rule = new PrivacyMaskRule();
            rule.setAliases(names == null ? new ArrayList<>() : new ArrayList<>(names));
            String t = StrUtil.blankToDefault(type, "").trim().toLowerCase(Locale.ROOT);
            switch (t) {
                case PHONE -> {
                    rule.setMethod(PrivacyMaskRule.PHONE);
                    rule.setKeepHead(3);
                    rule.setKeepTail(4);
                    rule.setMaskLen(4);
                }
                case NAME -> rule.setMethod(PrivacyMaskRule.NAME_KEEP_ENDS);
                case "idcard", "id_card" -> {
                    rule.setMethod(PrivacyMaskRule.ID_CARD);
                    rule.setKeepHead(1);
                    rule.setKeepTail(1);
                }
                default -> rule.setMethod(PrivacyMaskRule.FULL);
            }
            rules.add(rule);
        });
        return rules;
    }

    static PrivacyMaskRule matchRule(String outputKey, List<PrivacyMaskRule> rules) {
        String key = StrUtil.blankToDefault(outputKey, "").trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || rules == null) {
            return null;
        }
        for (PrivacyMaskRule rule : rules) {
            if (rule == null || rule.getAliases() == null) {
                continue;
            }
            boolean regex = isRegexMode(rule.getMatchMode());
            boolean contains = isContainsMode(rule.getMatchMode());
            for (String alias : rule.getAliases()) {
                if (aliasMatches(key, alias, regex, contains)) {
                    return rule;
                }
            }
        }
        return null;
    }

    static boolean isRegexMode(String matchMode) {
        String m = StrUtil.blankToDefault(matchMode, PrivacyMaskRule.MATCH_EXACT)
                .trim().toUpperCase(Locale.ROOT);
        return PrivacyMaskRule.MATCH_REGEX.equals(m) || "REGEXP".equals(m) || "PATTERN".equals(m);
    }

    static boolean isContainsMode(String matchMode) {
        String m = StrUtil.blankToDefault(matchMode, PrivacyMaskRule.MATCH_EXACT)
                .trim().toUpperCase(Locale.ROOT);
        return PrivacyMaskRule.MATCH_CONTAINS.equals(m)
                || "CONTAIN".equals(m) || "INCLUDES".equals(m) || "INCLUDE".equals(m)
                || "SUBSTRING".equals(m);
    }

    static boolean aliasMatches(String fieldKey, String alias, boolean regex, boolean contains) {
        String a = StrUtil.trim(alias);
        if (a.isEmpty()) {
            return false;
        }
        String needle = a.toLowerCase(Locale.ROOT);
        if (regex) {
            Pattern pattern = compileFieldRegex(a);
            return pattern != null && pattern.matcher(fieldKey).matches();
        }
        if (contains) {
            return fieldKey.contains(needle);
        }
        return fieldKey.equals(needle);
    }

    /**
     * 字段名正则：忽略大小写；支持 {@code /pattern/} 或 {@code /pattern/i} 写法。
     * 对整段字段名 {@code matches()}，包含匹配请写 {@code .*phone.*}。
     */
    public static Pattern compileFieldRegex(String raw) {
        String a = StrUtil.trim(raw);
        if (a.isEmpty() || a.length() > 128) {
            return null;
        }
        if (a.length() >= 2 && a.startsWith("/")) {
            int last = a.lastIndexOf('/');
            if (last > 0) {
                a = a.substring(1, last);
            }
        }
        if (a.isEmpty()) {
            return null;
        }
        try {
            return Pattern.compile(a, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    static String apply(String plain, PrivacyMaskRule rule) {
        String method = StrUtil.blankToDefault(rule.getMethod(), PrivacyMaskRule.FULL)
                .trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (method) {
            case PrivacyMaskRule.PHONE, "MASK_PHONE" -> maskPhone(plain);
            case PrivacyMaskRule.NAME_KEEP_ENDS, "NAME" -> maskName(plain);
            case PrivacyMaskRule.ID_CARD, "IDCARD" -> maskIdCard(plain);
            case PrivacyMaskRule.KEEP_HEAD_TAIL, "HEAD_TAIL" -> keepHeadTail(
                    plain, rule.getKeepHead(), rule.getKeepTail(), rule.getMaskChar());
            case PrivacyMaskRule.KEEP_HEAD, "HEAD" -> keepHead(plain, rule.getKeepHead(), rule.getMaskChar());
            case PrivacyMaskRule.KEEP_TAIL, "TAIL" -> keepTail(plain, rule.getKeepTail(), rule.getMaskChar());
            default -> maskFull(plain);
        };
    }

    static String maskPhone(String raw) {
        String digits = raw.replaceAll("\\s+", "");
        if (digits.length() < 7) {
            return maskFull(raw);
        }
        return digits.substring(0, 3)
                + "*".repeat(digits.length() - 7)
                + digits.substring(digits.length() - 4);
    }

    static String maskName(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return s;
        }
        int n = s.codePointCount(0, s.length());
        if (n == 1) {
            return "*";
        }
        if (n == 2) {
            return "*" + new String(Character.toChars(s.codePointBefore(s.length())));
        }
        int first = s.offsetByCodePoints(0, 1);
        int last = s.offsetByCodePoints(0, n - 1);
        return s.substring(0, first) + "*" + s.substring(last);
    }

    static String maskIdCard(String raw) {
        String s = raw.trim();
        if (s.length() < 3) {
            return maskFull(s);
        }
        return s.charAt(0) + "*".repeat(s.length() - 2) + s.charAt(s.length() - 1);
    }

    static String keepHeadTail(String raw, Integer keepHead, Integer keepTail, String maskChar) {
        String s = compactIfContact(raw);
        int head = keepHead == null ? 0 : Math.max(0, keepHead);
        int tail = keepTail == null ? 0 : Math.max(0, keepTail);
        if (s.length() <= head + tail) {
            return maskFull(raw);
        }
        int mid = s.length() - head - tail;
        return s.substring(0, head) + repeatMask(maskChar, mid) + s.substring(s.length() - tail);
    }

    static String keepHead(String raw, Integer keepHead, String maskChar) {
        String s = compactIfContact(raw);
        int head = keepHead == null ? 0 : Math.max(0, keepHead);
        if (s.length() <= head) {
            return maskFull(raw);
        }
        return s.substring(0, head) + repeatMask(maskChar, s.length() - head);
    }

    static String keepTail(String raw, Integer keepTail, String maskChar) {
        String s = compactIfContact(raw);
        int tail = keepTail == null ? 0 : Math.max(0, keepTail);
        if (s.length() <= tail) {
            return maskFull(raw);
        }
        return repeatMask(maskChar, s.length() - tail) + s.substring(s.length() - tail);
    }

    static String maskFull(String raw) {
        String s = raw == null ? "" : raw;
        int n = s.codePointCount(0, s.length());
        if (n <= 0) {
            return placeholder();
        }
        return "*".repeat(n);
    }

    private static String compactIfContact(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        String compact = trimmed.replaceAll("\\s+", "");
        if (compact.length() >= 7 && compact.chars().allMatch(ch -> Character.isDigit(ch) || ch == '+' || ch == '-')) {
            return compact.replace("-", "");
        }
        return trimmed;
    }

    private static String repeatMask(String maskChar, int n) {
        char c = (maskChar == null || maskChar.isEmpty()) ? '*' : maskChar.charAt(0);
        return String.valueOf(c).repeat(Math.max(1, n));
    }
}
