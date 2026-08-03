package org.yu.flow.module.oss.query;

import lombok.Data;

@Data
public class OssConnectionQueryDTO {

    private String name;
    private String code;
    private Boolean enabled;
    private int page = 0;
    private int size = 10;
}
