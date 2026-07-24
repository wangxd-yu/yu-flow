package org.yu.flow.module.open.query;

import lombok.Data;

@Data
public class FlowOpenPlatformQueryDTO {
    private String name;
    private String code;
    private Integer status;
    private Integer page = 0;
    private Integer size = 20;
}
