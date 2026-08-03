package org.yu.flow.module.host;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

/**
 * 数据范围（行级过滤语义）。
 */
@Value
@Builder
public class FlowHostDataScope {

    FlowHostScopeType type;
    @Builder.Default
    Set<String> deptIds = Collections.emptySet();
    @Builder.Default
    Set<String> userIds = Collections.emptySet();

    public static FlowHostDataScope all() {
        return FlowHostDataScope.builder().type(FlowHostScopeType.ALL).build();
    }

    public static FlowHostDataScope self() {
        return FlowHostDataScope.builder().type(FlowHostScopeType.SELF).build();
    }

    public static FlowHostDataScope deny() {
        return FlowHostDataScope.builder().type(FlowHostScopeType.DENY).build();
    }
}
