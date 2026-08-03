package org.yu.flow.module.oss.spi;

import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssObjectDO;

import java.util.Optional;

/**
 * OSS 对象访问扩展投票器（宿主可注入实现）。
 */
public interface FlowOssObjectAccessVoter {

    String ACTION_VIEW = "VIEW";
    String ACTION_DOWNLOAD = "DOWNLOAD";
    String ACTION_DELETE = "DELETE";

    /**
     * @return true=允许, false=拒绝, empty=无意见
     */
    Optional<Boolean> canAccess(FlowHostPrincipal principal, OssObjectDO object, String action);
}
