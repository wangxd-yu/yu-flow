package org.yu.flow.module.oss.query;

import lombok.Data;

@Data
public class OssObjectQueryDTO {

    private String profileCode;
    private String originalName;
    private String uploadedBy;
    private String visibility;
    private String status;
    private Boolean expiredOnly;
    private int page = 0;
    private int size = 10;
}
