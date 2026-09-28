package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 生成发布包字节流。
 */
public final class ReleasePackageWriter {

    private static final ObjectWriter JSON = FlowObjectMapperUtil.flowObjectMapper().writerWithDefaultPrettyPrinter();

    private static final Map<String, String> TYPE_LABELS = ReleaseAssetResolver.LABELS;

    public static final Map<String, String> REQUIREMENT_LABELS = Map.of(
            TransferRequirementDTO.KIND_DATASOURCE, "数据源",
            TransferRequirementDTO.KIND_MQ, "MQ 连接",
            TransferRequirementDTO.KIND_OSS, "OSS 连接",
            TransferRequirementDTO.KIND_TEMPLATE, "响应模板",
            TransferRequirementDTO.KIND_ENV_VAR, "环境变量",
            TransferRequirementDTO.KIND_ALERT_CHANNEL, "告警通道");

    public record Result(byte[] bytes, String digest) {
    }

    private ReleasePackageWriter() {
    }

    public static Result write(ReleasePackageFormat.ReleaseInfo release, AssetBundle bundle) throws IOException {
        return write(release, bundle, null);
    }

    /**
     * @param signingKey 非空时附带 SIGNATURE 条目
     */
    public static Result write(ReleasePackageFormat.ReleaseInfo release, AssetBundle bundle, String signingKey)
            throws IOException {
        byte[] bundleJson = JSON.writeValueAsBytes(bundle);
        byte[] releaseJson = JSON.writeValueAsBytes(release);
        byte[] changelog = changelog(release, bundle).getBytes(StandardCharsets.UTF_8);

        ReleasePackageFormat.Manifest manifest = new ReleasePackageFormat.Manifest();
        manifest.setReleaseCode(release.getCode());
        manifest.setReleaseName(release.getName());
        manifest.setSourceEnv(bundle.getSourceEnv());
        manifest.setExportedBy(bundle.getExportedBy());
        manifest.setExportedAt(bundle.getExportedAt());
        manifest.setBundleSchemaVersion(bundle.getSchemaVersion());
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(ReleasePackageFormat.BUNDLE, bundleJson);
        files.put(ReleasePackageFormat.RELEASE, releaseJson);
        files.put(ReleasePackageFormat.CHANGELOG, changelog);
        files.forEach((name, content) -> manifest.getFiles().put(name, sha256(content)));
        byte[] manifestJson = JSON.writeValueAsBytes(manifest);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            putEntry(zip, ReleasePackageFormat.MANIFEST, manifestJson);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                putEntry(zip, e.getKey(), e.getValue());
            }
            if (signingKey != null) {
                putEntry(zip, ReleasePackageFormat.SIGNATURE,
                        ReleaseSigning.sign(manifestJson, signingKey).getBytes(StandardCharsets.UTF_8));
            }
        }
        return new Result(out.toByteArray(), sha256(manifestJson));
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    static String changelog(ReleasePackageFormat.ReleaseInfo release, AssetBundle bundle) {
        StringBuilder md = new StringBuilder();
        md.append("# 发布说明 ").append(release.getCode());
        if (StrUtil.isNotBlank(release.getName())) {
            md.append(" · ").append(release.getName());
        }
        md.append("\n\n");
        md.append("- 来源环境：").append(StrUtil.blankToDefault(bundle.getSourceEnv(), "-")).append('\n');
        md.append("- 导出人：").append(StrUtil.nullToEmpty(bundle.getExportedBy())).append('\n');
        md.append("- 导出时间：").append(StrUtil.nullToEmpty(bundle.getExportedAt())).append("\n\n");

        if (StrUtil.isNotBlank(release.getRemark())) {
            md.append("## 说明\n\n").append(release.getRemark().trim()).append("\n\n");
        }

        List<ReleasePackageFormat.ReleaseEntry> upserts = release.getItems().stream()
                .filter(i -> !"OFFLINE".equals(i.getAction())).toList();
        List<ReleasePackageFormat.ReleaseEntry> offlines = release.getItems().stream()
                .filter(i -> "OFFLINE".equals(i.getAction())).toList();
        md.append("## 资产清单（").append(upserts.size()).append(" 项，导入后立即发布生效）\n\n");
        md.append("| 类型 | 名称 | ID | 来源 |\n|------|------|----|------|\n");
        for (ReleasePackageFormat.ReleaseEntry item : upserts) {
            md.append("| ").append(TYPE_LABELS.getOrDefault(item.getAssetType(), item.getAssetType()))
                    .append(" | ").append(escape(item.getAssetName()))
                    .append(" | ").append(StrUtil.blankToDefault(item.getAssetKey(), item.getAssetId()))
                    .append(" | ").append(originLabel(item.getOrigin()))
                    .append(" |\n");
        }
        md.append('\n');
        if (!offlines.isEmpty()) {
            md.append("## 下线清单（").append(offlines.size()).append(" 项，撤销发布 / 停用，不删除数据）\n\n");
            md.append("| 类型 | 名称 | ID |\n|------|------|----|\n");
            for (ReleasePackageFormat.ReleaseEntry item : offlines) {
                md.append("| ").append(TYPE_LABELS.getOrDefault(item.getAssetType(), item.getAssetType()))
                        .append(" | ").append(escape(item.getAssetName()))
                        .append(" | ").append(StrUtil.blankToDefault(item.getAssetKey(), item.getAssetId()))
                        .append(" |\n");
            }
            md.append('\n');
        }

        List<TransferRequirementDTO> reqs = bundle.getRequirements();
        md.append("## 生产环境需预先准备\n\n");
        if (reqs == null || reqs.isEmpty()) {
            md.append("无。\n\n");
        } else {
            md.append("以下资源按名称引用，不随包迁移；导入预检会检查是否已存在。\n\n");
            md.append("| 类型 | 名称 | 说明 | 引用方 |\n|------|------|------|--------|\n");
            for (TransferRequirementDTO r : reqs) {
                md.append("| ").append(REQUIREMENT_LABELS.getOrDefault(r.getKind(), r.getKind()))
                        .append(" | ").append(escape(r.getKey()))
                        .append(" | ").append(escape(r.getRemark()))
                        .append(" | ").append(escape(String.join("、", r.getUsedBy())))
                        .append(" |\n");
            }
            md.append('\n');
        }

        if (bundle.getWarnings() != null && !bundle.getWarnings().isEmpty()) {
            md.append("## 导出提示\n\n");
            bundle.getWarnings().forEach(w -> md.append("- ").append(w).append('\n'));
        }
        return md.toString();
    }

    private static String originLabel(String origin) {
        return switch (StrUtil.nullToEmpty(origin)) {
            case "DEPENDENCY" -> "依赖补齐";
            case "SCAN" -> "变更扫描";
            default -> "手工加入";
        };
    }

    private static String escape(String s) {
        return StrUtil.nullToEmpty(s).replace("|", "\\|").replace("\n", " ");
    }
}
