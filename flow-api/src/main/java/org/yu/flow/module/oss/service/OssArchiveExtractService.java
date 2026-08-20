package org.yu.flow.module.oss.service;

import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;

/**
 * 上传 zip 后按场景配置异步展开为子对象。
 */
public interface OssArchiveExtractService {

    String EXTRACT_NONE = "NONE";
    String EXTRACT_PENDING = "PENDING";
    String EXTRACT_EXTRACTING = "EXTRACTING";
    String EXTRACT_DONE = "DONE";
    String EXTRACT_FAILED = "FAILED";
    String EXTRACT_SKIPPED = "SKIPPED";

    /**
     * 上传成功后评估是否展开，必要时置 PENDING 并在事务提交后入队。
     */
    void scheduleIfNeeded(OssObjectDO object, OssUploadProfileDO profile);

    /**
     * 异步展开（由线程池调用）。不在长事务中持有连接。
     */
    void process(String objectId);
}
