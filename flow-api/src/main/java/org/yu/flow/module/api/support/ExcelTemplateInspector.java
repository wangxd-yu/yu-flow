package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Excel 导出模板轻量检查（不解压到磁盘，仅扫 ZIP 内 XML）。
 */
public final class ExcelTemplateInspector {

    private static final Pattern LIST_PLACEHOLDER = Pattern.compile("\\{\\.([A-Za-z_][A-Za-z0-9_]*)\\}");
    private static final Pattern SINGLE_PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private ExcelTemplateInspector() {
    }

    public static void assertSafeXlsx(byte[] bytes) {
        if (bytes == null || bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new IllegalArgumentException("文件不是合法的 xlsx（ZIP）格式");
        }
        boolean hasWorkbook = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = StrUtil.blankToDefault(entry.getName(), "").toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject.bin") || name.endsWith(".xlsm") || name.contains("macrosheets/")) {
                    throw new IllegalArgumentException("检测到宏相关内容，禁止上传 .xlsm / 含 VBA 的文件");
                }
                if (name.equals("xl/workbook.xml") || name.endsWith("/workbook.xml")) {
                    hasWorkbook = true;
                }
            }
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 xlsx 压缩包: "
                    + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
        if (!hasWorkbook) {
            throw new IllegalArgumentException("不是有效的 Excel 工作簿（缺少 workbook.xml）");
        }
    }

    public static ScanResult scan(byte[] bytes) {
        Set<String> listKeys = new LinkedHashSet<>();
        Set<String> singleKeys = new LinkedHashSet<>();
        if (bytes == null || bytes.length == 0) {
            return new ScanResult(listKeys, singleKeys);
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = StrUtil.blankToDefault(entry.getName(), "").toLowerCase(Locale.ROOT);
                if (!name.endsWith(".xml") && !name.endsWith(".rels")) {
                    continue;
                }
                // 控制扫描体积，避免超大 XML 拖垮上传
                byte[] chunk = zis.readNBytes(2 * 1024 * 1024);
                String xml = new String(chunk, StandardCharsets.UTF_8);
                Matcher list = LIST_PLACEHOLDER.matcher(xml);
                while (list.find()) {
                    listKeys.add(list.group(1));
                }
                Matcher single = SINGLE_PLACEHOLDER.matcher(xml);
                while (single.find()) {
                    singleKeys.add(single.group(1));
                }
            }
        } catch (Exception ignored) {
            // 扫描失败不阻断上传，由调用方决定 warning
        }
        singleKeys.removeAll(listKeys);
        return new ScanResult(listKeys, singleKeys);
    }

    public record ScanResult(Set<String> listKeys, Set<String> singleKeys) {
        public boolean hasListPlaceholder() {
            return listKeys != null && !listKeys.isEmpty();
        }
    }
}
