package org.yu.flow.module.oss.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 对象键 pattern 解析：{profile}/{yyyy}/{MM}/{uuid}_{filename}
 */
public final class OssKeyPatternResolver {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._\\-]");
    private static final String DEFAULT_PATTERN = "{profile}/{yyyy}/{MM}/{uuid}_{filename}";

    private OssKeyPatternResolver() {
    }

    public static String resolve(String pattern, String profileCode, String originalFilename, String keyPrefix) {
        String p = StrUtil.isNotBlank(pattern) ? pattern.trim() : DEFAULT_PATTERN;
        LocalDateTime now = LocalDateTime.now(ZONE_SH);
        String safeName = sanitizeFilename(originalFilename);
        String resolved = p
                .replace("{profile}", StrUtil.blankToDefault(profileCode, "default"))
                .replace("{yyyy}", now.format(DateTimeFormatter.ofPattern("yyyy")))
                .replace("{MM}", now.format(DateTimeFormatter.ofPattern("MM")))
                .replace("{dd}", now.format(DateTimeFormatter.ofPattern("dd")))
                .replace("{uuid}", IdUtil.fastSimpleUUID())
                .replace("{filename}", safeName);

        resolved = resolved.replace('\\', '/');
        while (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }

        if (StrUtil.isNotBlank(keyPrefix)) {
            String prefix = keyPrefix.trim().replace('\\', '/');
            while (prefix.startsWith("/")) {
                prefix = prefix.substring(1);
            }
            while (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            if (StrUtil.isNotBlank(prefix)) {
                resolved = prefix + "/" + resolved;
            }
        }

        if (resolved.contains("..")) {
            throw new IllegalArgumentException("object key 不允许包含 ..");
        }
        return resolved;
    }

    public static String buildPublicPath(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return "/";
        }
        String path = objectKey.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return "/" + path;
    }

    public static String buildPublicUrl(String publicBaseUrl, String publicPath) {
        if (StrUtil.isBlank(publicBaseUrl)) {
            return null;
        }
        String base = publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = StrUtil.blankToDefault(publicPath, "/");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private static String sanitizeFilename(String originalFilename) {
        if (StrUtil.isBlank(originalFilename)) {
            return "file";
        }
        String name = originalFilename.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = UNSAFE.matcher(name).replaceAll("_");
        if (name.isBlank()) {
            return "file";
        }
        return StrUtil.maxLength(name, 200);
    }
}
