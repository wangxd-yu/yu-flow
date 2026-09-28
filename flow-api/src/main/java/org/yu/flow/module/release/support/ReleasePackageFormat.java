package org.yu.flow.module.release.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 发布包（.yfpkg，zip）格式定义，导出与导入共用。
 *
 * <pre>
 * manifest.json   包头 + 各文件 SHA-256；整包摘要 = manifest.json 的 SHA-256
 * bundle.json     资产包（{@link org.yu.flow.module.transfer.dto.AssetBundle}）
 * release.json    版本单与明细
 * CHANGELOG.md    给运维看的发布说明
 * </pre>
 */
public final class ReleasePackageFormat {

    public static final String KIND = "yu-flow/release-package";
    public static final int FORMAT_VERSION = 1;
    public static final String EXTENSION = ".yfpkg";

    public static final String MANIFEST = "manifest.json";
    public static final String BUNDLE = "bundle.json";
    public static final String RELEASE = "release.json";
    public static final String CHANGELOG = "CHANGELOG.md";
    /** 可选：HMAC-SHA256(manifest.json)，十六进制 */
    public static final String SIGNATURE = "SIGNATURE";

    /** 必须存在的条目 */
    public static final List<String> ENTRIES = List.of(MANIFEST, BUNDLE, RELEASE, CHANGELOG);

    /** 导入时只接受这些条目，其余一律拒绝（防 Zip Slip / 夹带） */
    public static final List<String> ALLOWED_ENTRIES = List.of(MANIFEST, BUNDLE, RELEASE, CHANGELOG, SIGNATURE);

    /** 版本号：版本单创建与导入读包共用 */
    public static final Pattern RELEASE_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._\\-]{0,63}$");

    /** 环境编码（与 flow_env.code 一致） */
    public static final Pattern ENV_CODE = Pattern.compile("^[A-Za-z0-9_\\-]{1,32}$");

    /** 与导入记录列宽一致 */
    public static final int MAX_RELEASE_NAME = 128;

    public static final String ACTION_UPSERT = "UPSERT";
    public static final String ACTION_OFFLINE = "OFFLINE";

    private ReleasePackageFormat() {
    }

    @Data
    public static class Manifest {
        private String kind = KIND;
        private int formatVersion = FORMAT_VERSION;
        private String releaseCode;
        private String releaseName;
        private String sourceEnv;
        private String exportedBy;
        private String exportedAt;
        private Integer bundleSchemaVersion;
        /** 文件名 → SHA-256 */
        private Map<String, String> files = new LinkedHashMap<>();
    }

    @Data
    public static class ReleaseInfo {
        private String code;
        private String name;
        private String remark;
        private List<ReleaseEntry> items = new ArrayList<>();
    }

    @Data
    public static class ReleaseEntry {
        private String assetType;
        private String assetId;
        private String assetName;
        /** 按编码匹配的类型的匹配键 */
        private String assetKey;
        private String action;
        private String origin;
        private String contentHash;

        /**
         * 来源环境回归证据：该资产在当前线上版本发布之后最近一次回归通过的记录。
         * 目标环境要求回归通过时，导入发布认可这份证据。
         */
        private String regressionEnv;
        private String regressionRunId;
        private String regressionPassedAt;
    }
}
