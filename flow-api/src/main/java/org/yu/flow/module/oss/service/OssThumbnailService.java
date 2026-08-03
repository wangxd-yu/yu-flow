package org.yu.flow.module.oss.service;

import jakarta.servlet.http.HttpServletResponse;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;

public interface OssThumbnailService {

    String THUMB_NONE = "NONE";
    String THUMB_PENDING = "PENDING";
    String THUMB_READY = "READY";
    String THUMB_FAILED = "FAILED";
    String THUMB_SKIPPED = "SKIPPED";

    /**
     * 上传成功后评估是否生成缩略图，必要时置 PENDING 并入队。
     */
    void scheduleIfNeeded(OssObjectDO object, OssUploadProfileDO profile);

    /**
     * 异步生成缩略图（由线程池调用）。
     */
    void process(String objectId);

    void streamThumbnail(String id, FlowHostPrincipal principal, FlowHostDataScope scope,
                         HttpServletResponse response);

    void rebuild(String id, FlowHostPrincipal principal, FlowHostDataScope scope);
}
