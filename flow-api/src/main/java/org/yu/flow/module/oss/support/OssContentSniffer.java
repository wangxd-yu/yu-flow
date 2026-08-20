package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 用文件头魔数判定 MIME；扩展名与魔数冲突时视为不合格。
 */
public final class OssContentSniffer {

    private static final Map<String, String> EXT_MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("doc", "application/msword"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("zip", "application/zip")
    );

    private OssContentSniffer() {
    }

    public static String mimeForExt(String extension) {
        String ext = OssArchiveExtractSupport.normalizeExt(extension);
        return EXT_MIME.getOrDefault(ext, "application/octet-stream");
    }

    public static String sniff(byte[] header) {
        if (header == null || header.length == 0) {
            return "application/octet-stream";
        }
        if (startsWith(header, new byte[]{0x25, 0x50, 0x44, 0x46})) {
            return "application/pdf";
        }
        if (startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})) {
            return "image/png";
        }
        if (header.length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return "image/gif";
        }
        if (header.length >= 12
                && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        if (header.length >= 2 && header[0] == 'B' && header[1] == 'M') {
            return "image/bmp";
        }
        if (startsWith(header, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(header, new byte[]{0x50, 0x4B, 0x05, 0x06})
                || startsWith(header, new byte[]{0x50, 0x4B, 0x07, 0x08})) {
            return "application/zip";
        }
        if (startsWith(header, new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07})) {
            return "application/x-rar-compressed";
        }
        if (startsWith(header, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C})) {
            return "application/x-7z-compressed";
        }
        if (startsWith(header, new byte[]{0x1F, (byte) 0x8B})) {
            return "application/gzip";
        }
        return "application/octet-stream";
    }

    /**
     * 扩展名有明确魔数时必须匹配；Office Open XML 允许 PK 头。
     * 无明确魔数的类型（txt/csv/json/svg）不因魔数失败。
     */
    public static boolean magicMatchesExtension(String extension, byte[] header) {
        String ext = OssArchiveExtractSupport.normalizeExt(extension);
        if (StrUtil.isBlank(ext) || header == null || header.length == 0) {
            return true;
        }
        String sniffed = sniff(header);
        return switch (ext) {
            case "pdf" -> "application/pdf".equals(sniffed);
            case "png" -> "image/png".equals(sniffed);
            case "jpg", "jpeg" -> "image/jpeg".equals(sniffed);
            case "gif" -> "image/gif".equals(sniffed);
            case "webp" -> "image/webp".equals(sniffed);
            case "bmp" -> "image/bmp".equals(sniffed);
            case "zip" -> "application/zip".equals(sniffed);
            case "rar" -> sniffed.contains("rar");
            case "7z" -> sniffed.contains("7z");
            case "gz", "tgz" -> "application/gzip".equals(sniffed);
            case "docx", "xlsx", "pptx" -> "application/zip".equals(sniffed);
            default -> true;
        };
    }

    public static String resolveContentType(String extension, byte[] header) {
        String ext = OssArchiveExtractSupport.normalizeExt(extension);
        if (StrUtil.isNotBlank(ext) && magicMatchesExtension(ext, header) && EXT_MIME.containsKey(ext)) {
            return EXT_MIME.get(ext);
        }
        String sniffed = sniff(header);
        if (!"application/octet-stream".equals(sniffed)) {
            if (("docx".equals(ext) || "xlsx".equals(ext) || "pptx".equals(ext))
                    && "application/zip".equals(sniffed)) {
                return mimeForExt(ext);
            }
            return sniffed;
        }
        return mimeForExt(ext);
    }

    public static boolean looksLikeNestedArchive(String extension, byte[] header) {
        String ext = OssArchiveExtractSupport.normalizeExt(extension);
        if ("docx".equals(ext) || "xlsx".equals(ext) || "pptx".equals(ext)) {
            return false;
        }
        if (OssArchiveExtractSupport.NESTED_ARCHIVE_EXTS.contains(ext)) {
            return true;
        }
        String sniffed = sniff(header);
        if ("application/zip".equals(sniffed)) {
            return StrUtil.isBlank(ext) || "zip".equals(ext);
        }
        return sniffed.contains("rar") || sniffed.contains("7z") || sniffed.contains("gzip");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
