package org.yu.flow.module.oss.query;

import lombok.Data;

@Data
public class OssUploadProfileQueryDTO {

    private String name;
    private String code;
    private String connectionCode;
    private String visibility;
    private Boolean enabled;
    private int page = 0;
    private int size = 10;
}
