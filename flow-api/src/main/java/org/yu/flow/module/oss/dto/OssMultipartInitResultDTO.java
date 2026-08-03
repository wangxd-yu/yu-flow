package org.yu.flow.module.oss.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OssMultipartInitResultDTO {

    private String uploadId;
    private String bucket;
    private String objectKey;
}
