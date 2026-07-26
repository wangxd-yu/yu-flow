package org.yu.flow.auto.util;

import java.util.regex.Pattern;

/**
 * SQL 标识符安全白名单工具类（Phase 2.3 安全加固）。
 *
 * <p>用于对动态拼接到 SQL 语句中的表名、列名、排序字段等标识符进行白名单校验，
 * 防止 SQL 注入攻击。项目中动态标识符来源有两类：</p>
 * <ol>
 *   <li>代码内部字面量（如 DemoModeGuard），无需校验（已分析：不受用户输入控制）</li>
 *   <li>运行时来自用户输入或配置的字段名、排序列等，必须通过本工具校验</li>
 * </ol>
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * // 校验排序字段（来自请求参数）
 * String safeCol = SqlIdentifierSanitizer.requireSafeIdentifier(sortField);
 * String sql = "SELECT * FROM t ORDER BY " + safeCol;
 *
 * // 带允许列表校验（更严格）
 * String safeCol = SqlIdentifierSanitizer.requireSafeIdentifier(
 *     sortField, Set.of("name", "create_time", "status"));
 * }</pre>
 *
 * @author yu-flow
 */
public final class SqlIdentifierSanitizer {

    /**
     * 合法标识符模式：仅允许字母、数字、下划线，且不能以数字开头。
     * 兼容 MySQL、PostgreSQL 标识符规范（ASCII 安全子集）。
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,127}$");

    /**
     * 最大标识符长度（MySQL/PG 规范上限）。
     */
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private SqlIdentifierSanitizer() {
    }

    /**
     * 校验并返回安全的 SQL 标识符（表名/列名/别名）。
     *
     * <p>若标识符不满足白名单规则，抛出 {@link IllegalArgumentException}，
     * 调用方应将该异常视为客户端非法请求（HTTP 400）。</p>
     *
     * @param identifier 待校验的标识符，来自用户输入或外部配置
     * @return 校验通过的原始标识符
     * @throws IllegalArgumentException 若标识符包含非法字符或格式不合规
     */
    public static String requireSafeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL 标识符不能为空");
        }
        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "SQL 标识符长度超限（最大 " + MAX_IDENTIFIER_LENGTH + " 字符）");
        }
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "SQL 标识符包含非法字符，仅允许字母、数字和下划线：" + sanitizeForLog(identifier));
        }
        return identifier;
    }

    /**
     * 校验标识符，同时验证其是否在允许列表中（双重防御）。
     *
     * <p>比 {@link #requireSafeIdentifier(String)} 更严格：即使通过正则校验，
     * 也必须在调用方声明的允许集合中，适用于排序字段、分组字段等场景。</p>
     *
     * @param identifier 待校验的标识符
     * @param allowedSet 业务层声明的允许字段集合，不能为 null
     * @return 校验通过的原始标识符
     * @throws IllegalArgumentException 若标识符不在允许列表中
     */
    public static String requireSafeIdentifier(String identifier,
                                               java.util.Set<String> allowedSet) {
        requireSafeIdentifier(identifier);
        if (allowedSet == null || allowedSet.isEmpty()) {
            throw new IllegalArgumentException("允许列表不能为空");
        }
        if (!allowedSet.contains(identifier)) {
            throw new IllegalArgumentException(
                    "SQL 标识符不在允许列表中：" + sanitizeForLog(identifier));
        }
        return identifier;
    }

    /**
     * 判断标识符是否安全（不抛异常版本，用于条件判断场景）。
     *
     * @param identifier 待校验的标识符
     * @return true 表示安全，false 表示不安全
     */
    public static boolean isSafeIdentifier(String identifier) {
        return identifier != null
                && !identifier.isBlank()
                && identifier.length() <= MAX_IDENTIFIER_LENGTH
                && SAFE_IDENTIFIER.matcher(identifier).matches();
    }

    /**
     * 对日志输出中的标识符进行截断脱敏，防止日志注入。
     */
    private static String sanitizeForLog(String identifier) {
        if (identifier == null) {
            return "(null)";
        }
        String truncated = identifier.length() > 32
                ? identifier.substring(0, 32) + "..."
                : identifier;
        // 移除换行符防止日志注入
        return truncated.replace("\r", "").replace("\n", "").replace("\t", "");
    }
}
