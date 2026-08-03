package org.yu.flow.module.oss.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OssUploadOptions {

    private LocalDateTime expiresAt;
    private Long expiresInSeconds;
    private boolean overwrite;
    private String uploadedByOverride;
    private String uploadedByNameOverride;
}
