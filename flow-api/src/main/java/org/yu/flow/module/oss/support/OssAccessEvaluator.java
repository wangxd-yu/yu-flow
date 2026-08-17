package org.yu.flow.module.oss.support;

import org.springframework.stereotype.Component;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.spi.FlowOssObjectAccessVoter;

import java.util.List;
import java.util.Optional;

/**
 * 访问规则 + SPI 投票器 + 管理端 downloadPerm 综合判定对象访问权限。
 */
@ConditionalOnOssEnabled
@Component
public class OssAccessEvaluator {

    private final List<FlowOssObjectAccessVoter> voters;
    private final OssAccessRulesEngine ossAccessRulesEngine;
    private final OssProfileCallerAuth ossProfileCallerAuth;

    public OssAccessEvaluator(List<FlowOssObjectAccessVoter> voters,
                              OssAccessRulesEngine ossAccessRulesEngine,
                              OssProfileCallerAuth ossProfileCallerAuth) {
        this.voters = voters != null ? voters : List.of();
        this.ossAccessRulesEngine = ossAccessRulesEngine;
        this.ossProfileCallerAuth = ossProfileCallerAuth;
    }

    public boolean canAccess(OssObjectDO object, FlowHostDataScope scope, FlowHostPrincipal principal,
                             String action) {
        boolean allowed = ossAccessRulesEngine.canAccessObject(object, null, scope, principal);
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
        if (!allowed) {
            OssUploadProfileDO profile = ossAccessRulesEngine.resolveProfile(object);
            allowed = ossProfileCallerAuth.grantedByDownloadPerm(profile, principal);
        }
        return allowed;
    }
}
