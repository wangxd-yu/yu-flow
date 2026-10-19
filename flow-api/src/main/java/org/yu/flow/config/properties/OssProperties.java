package org.yu.flow.config.properties;

/**
 * 对象存储配置。对应 YAML：{@code yu.flow.oss.*}
 */
public class OssProperties {

    /**
     * 是否启用 OSS 模块自动装配。
     * <p>依赖 {@code io.minio:minio}；宿主未引入该依赖或设为 {@code false} 时跳过 OSS Bean。</p>
     */
    private boolean enabled = true;

    /** 全局单文件上传硬顶（字节），默认 50MB */
    private long maxUploadBytes = 50L * 1024 * 1024;

    /** 连接未配置时的隐私下载模式：STREAM / PRESIGN */
    private String privateDownloadMode = "STREAM";

    /** 连接未配置时的预签名有效期（秒） */
    private int presignExpireSeconds = 300;

    /** 软删对象物理清理间隔（分钟） */
    private int cleanupIntervalMinutes = 30;

    /** 打包下载最大文件数 */
    private int packMaxFiles = 50;

    /** 打包下载总大小上限（字节），默认 200MB */
    private long packMaxBytes = 200L * 1024 * 1024;

    /** 分片上传会话 TTL（分钟） */
    private int multipartSessionTtlMinutes = 120;

    /** 预签名直传总开关：关闭后所有场景的 presign 接口直接拒绝 */
    private boolean presignUploadEnabled = true;

    /** 预签名上传 URL 有效期（秒），默认 1 小时 */
    private int presignPutExpireSeconds = 3600;

    /** 预签名直传单文件硬顶（字节），默认 5GB；直传不经过网关，故不受 max-upload-bytes 约束 */
    private long presignMaxUploadBytes = 5L * 1024 * 1024 * 1024;

    /** 预签名待确认（PENDING）台账的存活时长（分钟），超时按放弃处理并清理 */
    private int presignPendingTtlMinutes = 120;

    /** 全局用户容量配额（字节），0=关闭 */
    private long userQuotaMaxBytes = 0;

    /** 全局用户文件数配额，0=关闭 */
    private int userQuotaMaxFiles = 0;

    /** 缩略图生成配置 */
    private Thumbnail thumbnail = new Thumbnail();

    /** zip 异步展开配置 */
    private Extract extract = new Extract();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public String getPrivateDownloadMode() {
        return privateDownloadMode;
    }

    public void setPrivateDownloadMode(String privateDownloadMode) {
        this.privateDownloadMode = privateDownloadMode;
    }

    public int getPresignExpireSeconds() {
        return presignExpireSeconds;
    }

    public void setPresignExpireSeconds(int presignExpireSeconds) {
        this.presignExpireSeconds = presignExpireSeconds;
    }

    public int getCleanupIntervalMinutes() {
        return cleanupIntervalMinutes;
    }

    public void setCleanupIntervalMinutes(int cleanupIntervalMinutes) {
        this.cleanupIntervalMinutes = cleanupIntervalMinutes;
    }

    public int getPackMaxFiles() {
        return packMaxFiles;
    }

    public void setPackMaxFiles(int packMaxFiles) {
        this.packMaxFiles = packMaxFiles;
    }

    public long getPackMaxBytes() {
        return packMaxBytes;
    }

    public void setPackMaxBytes(long packMaxBytes) {
        this.packMaxBytes = packMaxBytes;
    }

    public int getMultipartSessionTtlMinutes() {
        return multipartSessionTtlMinutes;
    }

    public void setMultipartSessionTtlMinutes(int multipartSessionTtlMinutes) {
        this.multipartSessionTtlMinutes = multipartSessionTtlMinutes;
    }

    public boolean isPresignUploadEnabled() {
        return presignUploadEnabled;
    }

    public void setPresignUploadEnabled(boolean presignUploadEnabled) {
        this.presignUploadEnabled = presignUploadEnabled;
    }

    public int getPresignPutExpireSeconds() {
        return presignPutExpireSeconds;
    }

    public void setPresignPutExpireSeconds(int presignPutExpireSeconds) {
        this.presignPutExpireSeconds = presignPutExpireSeconds;
    }

    public long getPresignMaxUploadBytes() {
        return presignMaxUploadBytes;
    }

    public void setPresignMaxUploadBytes(long presignMaxUploadBytes) {
        this.presignMaxUploadBytes = presignMaxUploadBytes;
    }

    public int getPresignPendingTtlMinutes() {
        return presignPendingTtlMinutes;
    }

    public void setPresignPendingTtlMinutes(int presignPendingTtlMinutes) {
        this.presignPendingTtlMinutes = presignPendingTtlMinutes;
    }

    public long getUserQuotaMaxBytes() {
        return userQuotaMaxBytes;
    }

    public void setUserQuotaMaxBytes(long userQuotaMaxBytes) {
        this.userQuotaMaxBytes = userQuotaMaxBytes;
    }

    public int getUserQuotaMaxFiles() {
        return userQuotaMaxFiles;
    }

    public void setUserQuotaMaxFiles(int userQuotaMaxFiles) {
        this.userQuotaMaxFiles = userQuotaMaxFiles;
    }

    public Thumbnail getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Thumbnail thumbnail) {
        this.thumbnail = thumbnail != null ? thumbnail : new Thumbnail();
    }

    public Extract getExtract() {
        return extract;
    }

    public void setExtract(Extract extract) {
        this.extract = extract != null ? extract : new Extract();
    }

    /**
     * 缩略图生成配置。对应 YAML：{@code yu.flow.oss.thumbnail.*}
     */
    public static class Thumbnail {

        /** 全局开关，默认开启 */
        private boolean enabled = true;

        /** 缩略图最长边（像素） */
        private int maxEdge = 256;

        /** 参与缩略图的源文件大小上限（字节），默认 20MB */
        private long maxSourceBytes = 20L * 1024 * 1024;

        /** JPEG 压缩质量 0~1 */
        private float jpegQuality = 0.85f;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxEdge() {
            return maxEdge;
        }

        public void setMaxEdge(int maxEdge) {
            this.maxEdge = maxEdge;
        }

        public long getMaxSourceBytes() {
            return maxSourceBytes;
        }

        public void setMaxSourceBytes(long maxSourceBytes) {
            this.maxSourceBytes = maxSourceBytes;
        }

        public float getJpegQuality() {
            return jpegQuality;
        }

        public void setJpegQuality(float jpegQuality) {
            this.jpegQuality = jpegQuality;
        }
    }

    /**
     * zip 异步展开。对应 YAML：{@code yu.flow.oss.extract.*}
     */
    public static class Extract {

        /** 全局开关，默认开启；场景仍需单独打开 */
        private boolean enabled = true;

        /** 单包最多处理的文件条目（不含目录） */
        private int maxEntries = 200;

        /** 单包解压后总字节上限，默认 512MB */
        private long maxUncompressedBytes = 512L * 1024 * 1024;

        /** 未压缩/压缩包体积比上限，用于抑制 zip bomb */
        private double maxRatio = 100d;

        /** 展开线程池大小 */
        private int poolSize = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public long getMaxUncompressedBytes() {
            return maxUncompressedBytes;
        }

        public void setMaxUncompressedBytes(long maxUncompressedBytes) {
            this.maxUncompressedBytes = maxUncompressedBytes;
        }

        public double getMaxRatio() {
            return maxRatio;
        }

        public void setMaxRatio(double maxRatio) {
            this.maxRatio = maxRatio;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }
}
