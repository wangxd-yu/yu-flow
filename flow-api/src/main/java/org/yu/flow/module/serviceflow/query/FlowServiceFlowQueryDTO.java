package org.yu.flow.module.serviceflow.query;

import lombok.Data;

@Data
public class FlowServiceFlowQueryDTO {

    private String directoryId;
    private String name;
    private Boolean enabled;
    /** 发布状态：0=未发布，1=已发布 */
    private Integer publishStatus;
    private int page = 0;
    private int size = 10;
}
