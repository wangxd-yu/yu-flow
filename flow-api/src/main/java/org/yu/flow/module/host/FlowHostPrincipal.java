package org.yu.flow.module.host;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 当前请求主体（宿主 SPI 或内置 JWT 解析结果）。
 */
@Value
@Builder
public class FlowHostPrincipal {

    String userId;
    String username;
    String deptId;
    @Builder.Default
    Set<String> roles = Collections.emptySet();
    @Builder.Default
    Map<String, String> attributes = Collections.emptyMap();
}
