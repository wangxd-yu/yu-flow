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
 * OSS 连接配置（flow_oss_connection）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@Table(name = "flow_oss_connection")
@org.hibernate.annotations.SQLDelete(sql = "update flow_oss_connection set deleted = 1 where id = ?")
@org.hibernate.annotations.Where(clause = "deleted = 0 OR deleted IS NULL")
public class OssConnectionDO implements Serializable {

    public static final String HEALTH_HEALTHY = "HEALTHY";
    public static final String HEALTH_UNHEALTHY = "UNHEALTHY";
    public static final String HEALTH_UNKNOWN = "UNKNOWN";

    public static final String ACCESS_MODE_ANON = "ANON";
    public static final String ACCESS_MODE_NGINX_PROXY = "NGINX_PROXY";

    public static final String PRIVATE_DOWNLOAD_STREAM = "STREAM";
    public static final String PRIVATE_DOWNLOAD_PRESIGN = "PRESIGN";

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 512)
    private String endpoint;

    @Column(name = "access_key", length = 128)
    private String accessKey;

    /** AES 密文 */
    @Column(name = "secret_key", length = 512)
    private String secretKey;

    @Column(length = 64)
    private String region;

    @Column(name = "path_style", nullable = false)
    private Boolean pathStyle;

    @Column(name = "public_bucket", length = 128)
    private String publicBucket;

    @Column(name = "private_bucket", length = 128)
    private String privateBucket;

    @Column(name = "public_base_url", length = 512)
    private String publicBaseUrl;

    @Column(name = "key_prefix", length = 256)
    private String keyPrefix;

    @Column(name = "public_access_mode", length = 32)
    private String publicAccessMode;

    @Column(name = "private_download_mode", length = 16)
    private String privateDownloadMode;

    @Column(name = "presign_expire_seconds")
    private Integer presignExpireSeconds;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "health_status", length = 32)
    private String healthStatus;

    @Column(name = "last_error_msg", length = 1024)
    private String lastErrorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "last_test_time")
    private LocalDateTime lastTestTime;

    private String info;

    @Column(name = "deleted", columnDefinition = "int default 0")
    private Integer deleted = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
