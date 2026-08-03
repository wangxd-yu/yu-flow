package org.yu.flow.module.oss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.oss.domain.OssConnectionDO;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OssConnectionDTO {

    private String id;
    private String name;
    private String code;
    private String endpoint;
    private String accessKey;
    private Boolean hasSecretKey;
    private String region;
    private Boolean pathStyle;
    private String publicBucket;
    private String privateBucket;
    private String publicBaseUrl;
    private String keyPrefix;
    private String publicAccessMode;
    private String privateDownloadMode;
    private Integer presignExpireSeconds;
    private Boolean enabled;
    private String healthStatus;
    private String lastErrorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastTestTime;

    private String info;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static OssConnectionDTO fromDO(OssConnectionDO entity) {
        if (entity == null) {
            return null;
        }
        return new OssConnectionDTO()
                .setId(entity.getId())
                .setName(entity.getName())
                .setCode(entity.getCode())
                .setEndpoint(entity.getEndpoint())
                .setAccessKey(entity.getAccessKey())
                .setHasSecretKey(entity.getSecretKey() != null && !entity.getSecretKey().isEmpty())
                .setRegion(entity.getRegion())
                .setPathStyle(entity.getPathStyle())
                .setPublicBucket(entity.getPublicBucket())
                .setPrivateBucket(entity.getPrivateBucket())
                .setPublicBaseUrl(entity.getPublicBaseUrl())
                .setKeyPrefix(entity.getKeyPrefix())
                .setPublicAccessMode(entity.getPublicAccessMode())
                .setPrivateDownloadMode(entity.getPrivateDownloadMode())
                .setPresignExpireSeconds(entity.getPresignExpireSeconds())
                .setEnabled(entity.getEnabled())
                .setHealthStatus(entity.getHealthStatus())
                .setLastErrorMsg(entity.getLastErrorMsg())
                .setLastTestTime(entity.getLastTestTime())
                .setInfo(entity.getInfo())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime());
    }
}
