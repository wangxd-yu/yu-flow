package org.yu.flow.module.oss.support;

import org.springframework.stereotype.Component;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.spi.FlowOssObjectAccessVoter;

import java.util.List;
import java.util.Optional;

/**
 * 数据范围 + SPI 投票器综合判定对象访问权限。
 */
@Component
public class OssAccessEvaluator {

    private final List<FlowOssObjectAccessVoter> voters;

    public OssAccessEvaluator(List<FlowOssObjectAccessVoter> voters) {
        this.voters = voters != null ? voters : List.of();
    }

    public boolean canAccess(OssObjectDO object, FlowHostDataScope scope, FlowHostPrincipal principal,
                             String action) {
        boolean allowed = OssDataScopeSpecification.canAccessObject(object, scope, principal);
        if (!allowed) {
            for (FlowOssObjectAccessVoter voter : voters) {
                Optional<Boolean> v = voter.canAccess(principal, object, action);
                if (v.isPresent() && v.get()) {
                    allowed = true;
                    break;
                }
            }
        } else {
            for (FlowOssObjectAccessVoter voter : voters) {
                Optional<Boolean> v = voter.canAccess(principal, object, action);
                if (v.isPresent() && !v.get()) {
                    allowed = false;
                    break;
                }
            }
        }
        return allowed;
    }
}
