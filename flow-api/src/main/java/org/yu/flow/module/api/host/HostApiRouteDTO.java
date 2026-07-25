package org.yu.flow.module.api.host;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HostApiRouteDTO {
    private String method;
    private String path;
    private String handlerClass;
    private String handlerMethod;
    /** 是否已被 Yu Flow 纳管（已有草稿或已发布同 method+path） */
    private boolean managed;
    private String managedApiId;
    private String managedApiName;
}
