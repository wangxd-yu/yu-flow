package org.yu.flow.module.open.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenGrantItemDTO {
    private String apiId;
    private String apiName;
    private String method;
    private String url;
    /** 空=跟随接口 method；否则如 GET,POST */
    private String allowMethods;
    /** 1=已发布 0=未发布/已下线 */
    private Integer publishStatus;
    /** 是否仍可被开放入口调用（已发布） */
    private boolean valid;
}
