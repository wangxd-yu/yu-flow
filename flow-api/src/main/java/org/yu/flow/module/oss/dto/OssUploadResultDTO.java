package org.yu.flow.module.oss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OssUploadResultDTO {

    private String id;
    private String visibility;
    private String originalName;
    private Long sizeBytes;
    private String contentType;
    private String publicUrl;
    private String publicPath;
    private String thumbStatus;
    private String thumbPublicPath;
    private Boolean hasThumbnail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expiresAt;
}
