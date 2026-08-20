package org.yu.flow.module.oss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OssUploadProfileDTO {

    private String id;
    private String name;
    private String code;
    private String connectionCode;
    private String visibility;
    private String bucketOverride;
    private String keyPattern;
    private String allowedContentTypes;
    private String allowedExtensions;
    private Long maxSizeBytes;
    private Integer maxFilesPerRequest;
    private Long quotaMaxBytes;
    private Integer quotaMaxFiles;
    private Boolean thumbnailEnabled;
    private Integer thumbnailMaxEdge;
    private Long thumbnailMaxSourceBytes;
    private Double thumbnailJpegQuality;
    private Boolean extractArchiveEnabled;
    private Boolean extractKeepArchive;
    private String extractRejectPolicy;
    private String extractAllowedExtensions;
    private String extractAllowedContentTypes;
    private Integer extractMaxEntries;
    private Long extractMaxUncompressedBytes;
    private Boolean requireAuth;
    private Boolean presignUploadEnabled;
    private String bizFieldsSchema;
    private String uploadPerm;
    private String downloadPerm;
    /** 访问规则 JSON：{"rules":[...]} */
    private String callerPolicy;
    private Boolean enabled;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static OssUploadProfileDTO fromDO(OssUploadProfileDO entity) {
        if (entity == null) {
            return null;
        }
        return new OssUploadProfileDTO()
                .setId(entity.getId())
                .setName(entity.getName())
                .setCode(entity.getCode())
                .setConnectionCode(entity.getConnectionCode())
                .setVisibility(entity.getVisibility())
                .setBucketOverride(entity.getBucketOverride())
                .setKeyPattern(entity.getKeyPattern())
                .setAllowedContentTypes(entity.getAllowedContentTypes())
                .setAllowedExtensions(entity.getAllowedExtensions())
                .setMaxSizeBytes(entity.getMaxSizeBytes())
                .setMaxFilesPerRequest(entity.getMaxFilesPerRequest())
                .setQuotaMaxBytes(entity.getQuotaMaxBytes())
                .setQuotaMaxFiles(entity.getQuotaMaxFiles())
                .setThumbnailEnabled(entity.getThumbnailEnabled())
                .setThumbnailMaxEdge(entity.getThumbnailMaxEdge())
                .setThumbnailMaxSourceBytes(entity.getThumbnailMaxSourceBytes())
                .setThumbnailJpegQuality(entity.getThumbnailJpegQuality())
                .setExtractArchiveEnabled(entity.getExtractArchiveEnabled())
                .setExtractKeepArchive(entity.getExtractKeepArchive())
                .setExtractRejectPolicy(entity.getExtractRejectPolicy())
                .setExtractAllowedExtensions(entity.getExtractAllowedExtensions())
                .setExtractAllowedContentTypes(entity.getExtractAllowedContentTypes())
                .setExtractMaxEntries(entity.getExtractMaxEntries())
                .setExtractMaxUncompressedBytes(entity.getExtractMaxUncompressedBytes())
                .setRequireAuth(entity.getRequireAuth())
                .setPresignUploadEnabled(entity.getPresignUploadEnabled())
                .setBizFieldsSchema(entity.getBizFieldsSchema())
                .setUploadPerm(entity.getUploadPerm())
                .setDownloadPerm(entity.getDownloadPerm())
                .setCallerPolicy(entity.getCallerPolicy())
                .setEnabled(entity.getEnabled())
                .setRemark(entity.getRemark())
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime());
    }
}
