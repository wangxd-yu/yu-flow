package org.yu.flow.module.rbac.support;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 登录密码复杂度策略（产品化常规要求）。
 * <ul>
 *   <li>长度 8–64</li>
 *   <li>至少包含大写、小写、数字、特殊字符各一类</li>
 * </ul>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    /** 常规特殊字符集 */
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>/?`~\\\\]");

    private PasswordPolicy() {
    }

    /**
     * @throws IllegalArgumentException 不满足时抛出中文原因
     */
    public static void validate(String password) {
        List<String> errors = collectErrors(password);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", errors));
        }
    }

    public static List<String> collectErrors(String password) {
        List<String> errors = new ArrayList<>();
        if (StrUtil.isBlank(password)) {
            errors.add("密码不能为空");
            return errors;
        }
        if (password.length() < MIN_LENGTH) {
            errors.add("密码至少 " + MIN_LENGTH + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            errors.add("密码不能超过 " + MAX_LENGTH + " 位");
        }
        if (!UPPER.matcher(password).find()) {
            errors.add("需包含大写字母");
        }
        if (!LOWER.matcher(password).find()) {
            errors.add("需包含小写字母");
        }
        if (!DIGIT.matcher(password).find()) {
            errors.add("需包含数字");
        }
        if (!SPECIAL.matcher(password).find()) {
            errors.add("需包含特殊字符（如 !@#$%^&* 等）");
        }
        return errors;
    }

    public static boolean isValid(String password) {
        return collectErrors(password).isEmpty();
    }
}
