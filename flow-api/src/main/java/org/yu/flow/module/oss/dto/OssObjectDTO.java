package org.yu.flow.module.oss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.oss.domain.OssObjectDO;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OssObjectDTO {

    private String id;
    private String profileCode;
    private String connectionCode;
    private String bucket;
    private String objectKey;
    private String visibility;
    private String publicPath;
    private String originalName;
    private String contentType;
    private String extension;
    private Long sizeBytes;
    private String checksumSha256;
    private String bizMeta;
    private String uploadedBy;
    private String uploadedByName;
    private String deptId;
    private String status;
    private String thumbStatus;
    private String thumbPublicPath;
    private Boolean hasThumbnail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static OssObjectDTO fromDO(OssObjectDO entity) {
        if (entity == null) {
            return null;
        }
        return new OssObjectDTO()
                .setId(entity.getId())
                .setProfileCode(entity.getProfileCode())
                .setConnectionCode(entity.getConnectionCode())
                .setBucket(entity.getBucket())
                .setObjectKey(entity.getObjectKey())
                .setVisibility(entity.getVisibility())
                .setPublicPath(entity.getPublicPath())
                .setOriginalName(entity.getOriginalName())
                .setContentType(entity.getContentType())
                .setExtension(entity.getExtension())
                .setSizeBytes(entity.getSizeBytes())
                .setChecksumSha256(entity.getChecksumSha256())
                .setBizMeta(entity.getBizMeta())
                .setUploadedBy(entity.getUploadedBy())
                .setUploadedByName(entity.getUploadedByName())
                .setDeptId(entity.getDeptId())
                .setStatus(entity.getStatus())
                .setThumbStatus(entity.getThumbStatus())
                .setThumbPublicPath(entity.getThumbPublicPath())
                .setHasThumbnail(org.yu.flow.module.oss.service.OssThumbnailService.THUMB_READY
                        .equals(entity.getThumbStatus()))
                .setExpiresAt(entity.getExpiresAt())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime());
    }
}
