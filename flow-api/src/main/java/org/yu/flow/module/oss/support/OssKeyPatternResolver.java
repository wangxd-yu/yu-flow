package org.yu.flow.module.oss.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 对象键 pattern 解析。
 *
 * <p>支持占位符：
 * <ul>
 *   <li>{@code {profile}} — 场景编码</li>
 *   <li>{@code {yyyy}} — 年</li>
 *   <li>{@code {MM}} — 月（两位）</li>
 *   <li>{@code {dd}} — 日（两位）</li>
 *   <li>{@code {HH}} — 时（24 小时，两位）</li>
 *   <li>{@code {mm}} — 分（两位）</li>
 *   <li>{@code {ss}} — 秒（两位）</li>
 *   <li>{@code {uuid}} — 无横线 UUID</li>
 *   <li>{@code {filename}} — 原始文件名（已净化）</li>
 *   <li>{@code {name}} — 不含扩展名的文件名</li>
 *   <li>{@code {ext}} — 扩展名（小写，不含点）</li>
 * </ul>
 * 默认：{@code {profile}/{yyyy}/{MM}/{uuid}_{filename}}
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
        String baseName = stripExtension(safeName);
        String ext = extractExtension(originalFilename);
        String yyyy = now.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = now.format(DateTimeFormatter.ofPattern("MM"));
        String day = now.format(DateTimeFormatter.ofPattern("dd"));
        String hour = now.format(DateTimeFormatter.ofPattern("HH"));
        String minute = now.format(DateTimeFormatter.ofPattern("mm"));
        String second = now.format(DateTimeFormatter.ofPattern("ss"));
        String uuid = IdUtil.fastSimpleUUID();

        String resolved = p
                .replace("{profile}", StrUtil.blankToDefault(profileCode, "default"))
                .replace("{yyyy}", yyyy)
                .replace("{MM}", month)
                .replace("{dd}", day)
                .replace("{HH}", hour)
                .replace("{mm}", minute)
                .replace("{ss}", second)
                .replace("{uuid}", uuid)
                .replace("{filename}", safeName)
                .replace("{name}", baseName)
                .replace("{ext}", ext);

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

    private static String stripExtension(String safeName) {
        if (StrUtil.isBlank(safeName)) {
            return "file";
        }
        int dot = safeName.lastIndexOf('.');
        if (dot <= 0) {
            return safeName;
        }
        return safeName.substring(0, dot);
    }

    /** 小写扩展名，不含点；无扩展名时返回空串 */
    private static String extractExtension(String originalFilename) {
        if (StrUtil.isBlank(originalFilename)) {
            return "";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).toLowerCase();
        return UNSAFE.matcher(ext).replaceAll("");
    }
}
