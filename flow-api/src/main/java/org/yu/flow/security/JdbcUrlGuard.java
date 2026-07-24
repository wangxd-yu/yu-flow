package org.yu.flow.security;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JDBC URL 白名单：仅允许本产品支持的数据库协议，拦截 H2/LDAP 等可被滥用的 scheme。
 */
public final class JdbcUrlGuard {

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "jdbc:mysql:",
            "jdbc:mariadb:",
            "jdbc:postgresql:",
            "jdbc:pgsql:",
            "jdbc:highgo:"
    );

    /** 常见危险片段（H2 INIT/RUNSCRIPT、嵌入式脚本等） */
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)(INIT\\s*=|RUNSCRIPT|javascript:|jdbc:h2:|jdbc:derby:|jdbc:hsqldb:|"
                    + "jdbc:sqlite:|jdbc:ldap:|jdbc:rmi:|jdbc:jndi:|allowLoadLocalInfile\\s*=\\s*true)");

    private JdbcUrlGuard() {
    }

    public static String validate(String url) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("JDBC URL 不能为空");
        }
        String raw = url.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("jdbc:")) {
            throw new IllegalArgumentException("仅允许 jdbc: 协议的连接串");
        }
        boolean allowed = false;
        for (String prefix : ALLOWED_PREFIXES) {
            if (lower.startsWith(prefix)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IllegalArgumentException(
                    "不支持的 JDBC 协议。仅允许 mysql / mariadb / postgresql / highgo");
        }
        if (DANGEROUS.matcher(raw).find()) {
            throw new IllegalArgumentException("JDBC URL 包含禁止的危险参数或协议");
        }
        // 要求至少带 host 形态：jdbc:xxx://host...
        int schemeEnd = lower.indexOf("://");
        if (schemeEnd < 0) {
            throw new IllegalArgumentException("JDBC URL 须包含主机地址（jdbc:…://host/…）");
        }
        String after = raw.substring(schemeEnd + 3);
        if (StrUtil.isBlank(after) || after.startsWith("/") || after.startsWith("?")) {
            throw new IllegalArgumentException("JDBC URL 缺少 host");
        }
        return raw;
    }
}
