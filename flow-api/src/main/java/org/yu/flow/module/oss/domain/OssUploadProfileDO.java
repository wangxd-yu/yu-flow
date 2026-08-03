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
 * OSS 上传场景（flow_oss_upload_profile）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_oss_upload_profile")
@org.hibernate.annotations.SQLDelete(sql = "update flow_oss_upload_profile set deleted = 1 where id = ?")
@org.hibernate.annotations.Where(clause = "deleted = 0 OR deleted IS NULL")
public class OssUploadProfileDO implements Serializable {

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "connection_code", nullable = false, length = 64)
    private String connectionCode;

    @Column(nullable = false, length = 16)
    private String visibility;

    @Column(name = "bucket_override", length = 128)
    private String bucketOverride;

    @Column(name = "key_pattern", length = 512)
    private String keyPattern;

    @Column(name = "allowed_content_types", columnDefinition = "text")
    private String allowedContentTypes;

    @Column(name = "allowed_extensions", length = 512)
    private String allowedExtensions;

    @Column(name = "max_size_bytes")
    private Long maxSizeBytes;

    @Column(name = "max_files_per_request")
    private Integer maxFilesPerRequest;

    @Column(name = "quota_max_bytes")
    private Long quotaMaxBytes;

    @Column(name = "quota_max_files")
    private Integer quotaMaxFiles;

    @Column(name = "thumbnail_enabled", columnDefinition = "tinyint(1) default 0")
    private Boolean thumbnailEnabled;

    /** 缩略图最长边（像素），空=用全局 yu.flow.oss.thumbnail.max-edge */
    @Column(name = "thumbnail_max_edge")
    private Integer thumbnailMaxEdge;

    /** 参与生成的源文件上限（字节），空=用全局 */
    @Column(name = "thumbnail_max_source_bytes")
    private Long thumbnailMaxSourceBytes;

    /** JPEG 质量 0~1，空=用全局 */
    @Column(name = "thumbnail_jpeg_quality")
    private Double thumbnailJpegQuality;

    @Column(name = "require_auth", columnDefinition = "tinyint(1) default 1")
    private Boolean requireAuth;

    @Column(name = "biz_fields_schema", columnDefinition = "text")
    private String bizFieldsSchema;

    @Column(name = "access_perm", length = 128)
    private String accessPerm;

    @Column(columnDefinition = "tinyint(1) default 1")
    private Boolean enabled;

    private String remark;

    @Column(name = "deleted", columnDefinition = "int default 0")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
