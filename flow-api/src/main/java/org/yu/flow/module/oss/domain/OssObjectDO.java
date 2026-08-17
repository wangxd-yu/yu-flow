package org.yu.flow.module.oss.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OSS 文件台账（flow_oss_object）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_oss_object")
public class OssObjectDO implements Serializable {

    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 预签名直传已签发、待 complete 确认 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DELETED = "DELETED";

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "profile_code", length = 64)
    private String profileCode;

    @Column(name = "connection_code", length = 64)
    private String connectionCode;

    @Column(length = 128)
    private String bucket;

    @Column(name = "object_key", length = 1024)
    private String objectKey;

    @Column(length = 16)
    private String visibility;

    @Column(name = "public_path", length = 1024)
    private String publicPath;

    @Column(name = "original_name", length = 512)
    private String originalName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(length = 32)
    private String extension;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "biz_meta", columnDefinition = "text")
    private String bizMeta;

    @Column(name = "uploaded_by", length = 64)
    private String uploadedBy;

    /** 与 {@link org.yu.flow.module.host.FlowHostPrincipal#getUserType()} 相同：ADMIN / END_USER / OPEN_APP 等 */
    @Column(name = "uploaded_by_user_type", length = 32)
    private String uploadedByUserType;

    @Column(name = "uploaded_by_name", length = 128)
    private String uploadedByName;

    @Column(name = "dept_id", length = 64)
    private String deptId;

    @Column(length = 16)
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "object_purged", nullable = false)
    private Boolean objectPurged;

    @Column(name = "thumb_status", length = 16, columnDefinition = "varchar(16) default 'NONE'")
    private String thumbStatus;

    @Column(name = "thumb_object_key", length = 1024)
    private String thumbObjectKey;

    @Column(name = "thumb_public_path", length = 1024)
    private String thumbPublicPath;

    @Column(name = "thumb_content_type", length = 128)
    private String thumbContentType;

    @Column(name = "thumb_size_bytes")
    private Long thumbSizeBytes;

    @Column(name = "thumb_error", length = 512)
    private String thumbError;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
