package org.yu.flow.module.oss.query;

import lombok.Data;

@Data
public class OssDownloadLogQueryDTO {

    private String objectId;
    private String downloadedBy;
    private String result;
    private int page = 0;
    private int size = 10;
}
