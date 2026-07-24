package org.yu.flow.security;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Pattern;

/**
 * SQL 标识符白名单（表名/列名/schema），防止拼接进 DDL/元数据 SQL。
 */
public final class SqlIdentifierGuard {

    /** 允许 schema.table 或 table；段内仅字母数字下划线 */
    private static final Pattern IDENT =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}(\\.[A-Za-z_][A-Za-z0-9_]{0,63})?$");

    private SqlIdentifierGuard() {
    }

    public static String requireTableName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            throw new IllegalArgumentException("表名不能为空");
        }
        String name = tableName.trim();
        if (!IDENT.matcher(name).matches()) {
            throw new IllegalArgumentException("非法表名（仅允许字母/数字/下划线，可选 schema.table）: " + tableName);
        }
        return name;
    }

    public static String requireSchema(String schema) {
        if (StrUtil.isBlank(schema)) {
            return "public";
        }
        String name = schema.trim();
        if (!Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$").matcher(name).matches()) {
            throw new IllegalArgumentException("非法 schema 名: " + schema);
        }
        return name;
    }
}
