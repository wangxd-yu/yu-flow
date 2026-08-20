package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * zip 展开：路径消毒、垃圾条目、内外层白名单、子对象键。纯函数，便于单测。
 */
public final class OssArchiveExtractSupport {

    public static final String POLICY_SKIP_ZERO_FAIL = "SKIP_ZERO_FAIL";
    public static final String POLICY_FAIL_PACK = "FAIL_PACK";

    public static final Set<String> NESTED_ARCHIVE_EXTS = Set.of(
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz");

    private static final Set<String> JUNK_BASENAMES = Set.of(
            ".ds_store", "thumbs.db", "desktop.ini");

    /** 对象键段：去掉控制字符与 Windows 非法字符，保留中文等 Unicode。 */
    private static final Pattern UNSAFE_SEGMENT = Pattern.compile("[\\u0000-\\u001f<>:\"|?*]");

    private OssArchiveExtractSupport() {
    }

    public static boolean isZipObject(String extension, String contentType) {
        String ext = normalizeExt(extension);
        if ("zip".equals(ext)) {
            return true;
        }
        String ct = StrUtil.blankToDefault(contentType, "").toLowerCase(Locale.ROOT);
        return ct.equals("application/zip")
                || ct.equals("application/x-zip")
                || ct.equals("application/x-zip-compressed")
                || ct.equals("application/zip-compressed");
    }

    public static boolean failPack(String policy) {
        return POLICY_FAIL_PACK.equalsIgnoreCase(StrUtil.trim(policy));
    }

    public static String normalizePolicy(String policy) {
        if (failPack(policy)) {
            return POLICY_FAIL_PACK;
        }
        return POLICY_SKIP_ZERO_FAIL;
    }

    public static boolean outerAllowlistAllowsZip(String allowedExtensions, String allowedContentTypes) {
        boolean extEmpty = StrUtil.isBlank(allowedExtensions);
        boolean mimeEmpty = StrUtil.isBlank(allowedContentTypes);
        if (extEmpty && mimeEmpty) {
            return true;
        }
        if (!extEmpty && !parseExtAllowlist(allowedExtensions).contains("zip")) {
            return false;
        }
        if (!mimeEmpty) {
            Set<String> mimes = parseMimeAllowlist(allowedContentTypes);
            boolean zipMime = mimes.contains("application/zip")
                    || mimes.contains("application/x-zip-compressed")
                    || mimes.contains("application/x-zip");
            if (!zipMime) {
                return false;
            }
        }
        return true;
    }

    /**
     * 消毒包内相对路径。Zip Slip / 绝对路径返回 {@code null}。
     */
    public static String sanitizeEntryPath(String rawName) {
        if (StrUtil.isBlank(rawName)) {
            return null;
        }
        String name = rawName.replace('\\', '/').replace("\0", "");
        if (name.startsWith("/")) {
            return null;
        }
        if (name.matches("^[a-zA-Z]:/.*")) {
            return null;
        }
        String[] parts = name.split("/");
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                return null;
            }
            String safe = UNSAFE_SEGMENT.matcher(part).replaceAll("_").trim();
            if (safe.isBlank() || "..".equals(safe) || ".".equals(safe)) {
                return null;
            }
            if (safe.length() > 200) {
                safe = safe.substring(0, 200);
            }
            kept.add(safe);
        }
        if (kept.isEmpty()) {
            return null;
        }
        String joined = String.join("/", kept);
        if (joined.length() > 480) {
            return null;
        }
        return joined;
    }

    public static boolean isDirectoryName(String rawName, boolean zipDirectoryFlag) {
        if (zipDirectoryFlag) {
            return true;
        }
        return rawName != null && (rawName.endsWith("/") || rawName.endsWith("\\"));
    }

    public static boolean isJunkPath(String relativePath) {
        if (StrUtil.isBlank(relativePath)) {
            return true;
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.startsWith("__macosx/") || lower.contains("/__macosx/") || "__macosx".equals(lower)) {
            return true;
        }
        int slash = relativePath.lastIndexOf('/');
        String base = slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        return JUNK_BASENAMES.contains(base.toLowerCase(Locale.ROOT));
    }

    public static boolean isNestedArchive(String extension, String contentType) {
        String ext = normalizeExt(extension);
        if (NESTED_ARCHIVE_EXTS.contains(ext)) {
            return true;
        }
        String ct = StrUtil.blankToDefault(contentType, "").toLowerCase(Locale.ROOT);
        return ct.contains("zip") || ct.contains("rar") || ct.contains("7z")
                || ct.contains("tar") || ct.contains("gzip") || ct.contains("x-bzip");
    }

    public static String extensionOf(String relativePath) {
        if (StrUtil.isBlank(relativePath)) {
            return "";
        }
        int slash = relativePath.lastIndexOf('/');
        String base = slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        return normalizeExt(base.substring(dot + 1));
    }

    public static String childObjectKey(String parentObjectKey, String relativePath) {
        String prefix = stripZipExtension(parentObjectKey);
        String rel = sanitizeEntryPath(relativePath);
        if (StrUtil.isBlank(prefix) || StrUtil.isBlank(rel)) {
            return null;
        }
        String key = prefix + "/" + rel;
        if (key.length() > 1000) {
            return null;
        }
        return key;
    }

    public static String stripZipExtension(String objectKey) {
        String key = StrUtil.blankToDefault(objectKey, "").replace('\\', '/');
        if (key.toLowerCase(Locale.ROOT).endsWith(".zip") && key.length() > 4) {
            return key.substring(0, key.length() - 4);
        }
        if (StrUtil.isBlank(key)) {
            return "extracted";
        }
        return key + "_extracted";
    }

    public static boolean allowedByInnerList(String extension, String contentType,
                                             String allowedExtensions, String allowedContentTypes) {
        Set<String> exts = parseExtAllowlist(allowedExtensions);
        Set<String> mimes = parseMimeAllowlist(allowedContentTypes);
        if (exts.isEmpty() && mimes.isEmpty()) {
            return true;
        }
        String ext = normalizeExt(extension);
        String ct = StrUtil.blankToDefault(contentType, "").toLowerCase(Locale.ROOT);
        if (!exts.isEmpty() && !exts.contains(ext)) {
            return false;
        }
        if (!mimes.isEmpty() && (StrUtil.isBlank(ct) || !mimes.contains(ct))) {
            return false;
        }
        return true;
    }

    public static Set<String> parseExtAllowlist(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String ext = normalizeExt(part);
            if (!ext.isEmpty()) {
                out.add(ext);
            }
        }
        return out;
    }

    public static Set<String> parseMimeAllowlist(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String mime = StrUtil.trim(part).toLowerCase(Locale.ROOT);
            if (!mime.isEmpty()) {
                out.add(mime);
            }
        }
        return out;
    }

    public static String normalizeExt(String raw) {
        if (raw == null) {
            return "";
        }
        String ext = raw.trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        return ext;
    }

    public static String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    public static String summary(int kept, int skipped, int failedReasonCount) {
        return "已落库 " + kept + "，跳过 " + skipped
                + (failedReasonCount > 0 ? "，不合格 " + failedReasonCount : "");
    }
}
