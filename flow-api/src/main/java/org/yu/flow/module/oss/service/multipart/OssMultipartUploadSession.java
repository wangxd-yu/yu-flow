package org.yu.flow.module.oss.service.multipart;

import lombok.Data;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 服务端分片会话（分片先落本地临时目录，complete 时合并 putObject）。
 *
 * <p>MinIO Java SDK 8.5 未公开 CreateMultipartUpload 高层 API，故采用「网关侧分片」策略。</p>
 */
@Data
public class OssMultipartUploadSession {

    private String sessionId;
    private String connectionCode;
    private String bucket;
    private String objectKey;
    private String profileCode;
    /** 创建会话的真实主体，与管理员代传的 uploadedBy 分离。 */
    private String ownerId;
    private String ownerAuthChannel;
    private String uploadedBy;
    private String uploadedByUserType;
    private String uploadedByName;
    private String deptId;
    private String contentType;
    private String originalName;
    private String bizMeta;
    private LocalDateTime expiresAt;
    private long totalSizeBytes;
    private LocalDateTime createdAt;
    private Path workDir;
    private final List<PartRecord> parts = new CopyOnWriteArrayList<>();

    @Data
    public static class PartRecord {
        private int partNumber;
        private Path filePath;
        private long sizeBytes;
    }
}
