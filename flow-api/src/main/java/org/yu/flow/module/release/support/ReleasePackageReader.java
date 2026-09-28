package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 解析并校验发布包。
 *
 * <p>包经人手流转，按不可信输入处理：只接受白名单条目、限制条目数与解压后大小（防压缩炸弹 / Zip Slip），
 * 逐文件核对 manifest 里的 SHA-256，包头与明细字段按格式与列宽校验。</p>
 */
public final class ReleasePackageReader {

    public static final long MAX_PACKAGE_BYTES = 50L * 1024 * 1024;
    /** 解析后的对象常驻预检缓存，按解压后大小计入缓存额度 */
    static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;
    /** 版本单上限 500 项，留出余量 */
    static final int MAX_RELEASE_ITEMS = 1000;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper().copy()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * @param manifestBytes 原始 manifest.json，签名校验用
     * @param signature     SIGNATURE 条目内容；未签名为 null
     * @param size          各条目解压后的总字节数
     */
    public record Parsed(ReleasePackageFormat.Manifest manifest, AssetBundle bundle,
                         ReleasePackageFormat.ReleaseInfo release, String changelog, String digest,
                         byte[] manifestBytes, String signature, long size) {
    }

    private ReleasePackageReader() {
    }

    public static Parsed read(byte[] content) {
        if (content == null || content.length == 0) {
            throw bad("发布包为空");
        }
        if (content.length > MAX_PACKAGE_BYTES) {
            throw bad("发布包超过 " + (MAX_PACKAGE_BYTES / 1024 / 1024) + "MB");
        }
        Map<String, byte[]> entries = unzip(content);
        for (String required : ReleasePackageFormat.ENTRIES) {
            if (!entries.containsKey(required)) {
                throw bad("发布包缺少 " + required + "，文件可能不完整或不是 Yu Flow 发布包");
            }
        }
        byte[] manifestBytes = entries.get(ReleasePackageFormat.MANIFEST);
        ReleasePackageFormat.Manifest manifest = parse(manifestBytes, ReleasePackageFormat.Manifest.class, "manifest.json");
        if (!ReleasePackageFormat.KIND.equals(manifest.getKind())) {
            throw bad("文件不是 Yu Flow 发布包");
        }
        if (manifest.getFormatVersion() > ReleasePackageFormat.FORMAT_VERSION) {
            throw bad("发布包格式版本(" + manifest.getFormatVersion() + ")高于当前系统支持的版本("
                    + ReleasePackageFormat.FORMAT_VERSION + ")，请先升级目标环境程序");
        }
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (ReleasePackageFormat.MANIFEST.equals(e.getKey()) || ReleasePackageFormat.SIGNATURE.equals(e.getKey())) {
                continue;
            }
            String expected = manifest.getFiles() == null ? null : manifest.getFiles().get(e.getKey());
            if (!Objects.equals(expected, ReleasePackageWriter.sha256(e.getValue()))) {
                throw bad("发布包内容校验失败（" + e.getKey() + " 与 manifest 摘要不一致），文件可能被修改或损坏");
            }
        }
        AssetBundle bundle = parse(entries.get(ReleasePackageFormat.BUNDLE), AssetBundle.class, "bundle.json");
        ReleasePackageFormat.ReleaseInfo release = parse(entries.get(ReleasePackageFormat.RELEASE),
                ReleasePackageFormat.ReleaseInfo.class, "release.json");
        validate(manifest, release);
        String changelog = new String(entries.get(ReleasePackageFormat.CHANGELOG), StandardCharsets.UTF_8);
        byte[] sig = entries.get(ReleasePackageFormat.SIGNATURE);
        long size = entries.values().stream().mapToLong(b -> b.length).sum();
        return new Parsed(manifest, bundle, release, changelog, ReleasePackageWriter.sha256(manifestBytes),
                manifestBytes, sig == null ? null : new String(sig, StandardCharsets.UTF_8), size);
    }

    /** 包头与明细会写进导入记录、审计与页面，格式不对的包直接拒收，免得导入做到最后才在落库时失败 */
    static void validate(ReleasePackageFormat.Manifest manifest, ReleasePackageFormat.ReleaseInfo release) {
        String code = release.getCode();
        if (code == null || !ReleasePackageFormat.RELEASE_CODE.matcher(code).matches()) {
            throw bad("release.json 中的版本号格式不正确");
        }
        if (StrUtil.isNotBlank(manifest.getReleaseCode()) && !code.equals(manifest.getReleaseCode())) {
            throw bad("manifest.json 与 release.json 的版本号不一致");
        }
        if (StrUtil.length(release.getName()) > ReleasePackageFormat.MAX_RELEASE_NAME) {
            throw bad("版本名称超过 " + ReleasePackageFormat.MAX_RELEASE_NAME + " 个字符");
        }
        if (StrUtil.isNotBlank(manifest.getSourceEnv()) && !ReleasePackageFormat.ENV_CODE.matcher(manifest.getSourceEnv()).matches()) {
            throw bad("来源环境编码格式不正确");
        }
        if (StrUtil.length(manifest.getExportedBy()) > 64 || StrUtil.length(manifest.getExportedAt()) > 32) {
            throw bad("manifest.json 中的导出人或导出时间格式不正确");
        }
        if (release.getItems() == null || release.getItems().size() > MAX_RELEASE_ITEMS) {
            throw bad("版本单明细缺失或超过 " + MAX_RELEASE_ITEMS + " 项");
        }
        for (ReleasePackageFormat.ReleaseEntry e : release.getItems()) {
            if (e == null || !ReleaseAssetResolver.TYPES.contains(e.getAssetType())) {
                throw bad("版本单明细包含未知的资产类型: " + (e == null ? null : StrUtil.maxLength(e.getAssetType(), 32)));
            }
            if (e.getAction() != null && !ReleasePackageFormat.ACTION_UPSERT.equals(e.getAction())
                    && !ReleasePackageFormat.ACTION_OFFLINE.equals(e.getAction())) {
                throw bad("版本单明细包含未知的动作: " + StrUtil.maxLength(e.getAction(), 32));
            }
            if (StrUtil.isBlank(e.getAssetId()) || e.getAssetId().length() > 64
                    || StrUtil.length(e.getAssetKey()) > 128 || StrUtil.length(e.getAssetName()) > 255) {
                throw bad("版本单明细的资产 ID / 编码 / 名称缺失或过长");
            }
            if (StrUtil.isNotBlank(e.getRegressionEnv()) && !ReleasePackageFormat.ENV_CODE.matcher(e.getRegressionEnv()).matches()) {
                throw bad("回归证据的环境编码格式不正确");
            }
            if (StrUtil.isNotBlank(e.getRegressionPassedAt())) {
                try {
                    LocalDateTime.parse(e.getRegressionPassedAt(), DATE_TIME);
                } catch (Exception ex) {
                    throw bad("回归证据的通过时间格式不正确");
                }
            }
        }
    }

    private static Map<String, byte[]> unzip(byte[] content) {
        Map<String, byte[]> entries = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !ReleasePackageFormat.ALLOWED_ENTRIES.contains(name)) {
                    throw bad("发布包含有不允许的文件: " + StrUtil.maxLength(name, 64));
                }
                if (entries.containsKey(name)) {
                    throw bad("发布包含有重复文件: " + name);
                }
                byte[] data = readLimited(zip, MAX_UNCOMPRESSED_BYTES - total);
                total += data.length;
                entries.put(name, data);
            }
        } catch (FlowException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw bad("发布包不是有效的 zip 文件: " + e.getMessage());
        }
        return entries;
    }

    private static byte[] readLimited(InputStream in, long remaining) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long read = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            read += n;
            if (read > remaining) {
                throw bad("发布包解压后超过 " + (MAX_UNCOMPRESSED_BYTES / 1024 / 1024) + "MB");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static <T> T parse(byte[] json, Class<T> type, String name) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw bad(name + " 解析失败: " + e.getMessage());
        }
    }

    private static FlowException bad(String message) {
        return new FlowException("RELEASE_BAD_PACKAGE", message);
    }
}
