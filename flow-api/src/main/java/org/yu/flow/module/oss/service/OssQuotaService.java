package org.yu.flow.module.oss.service;

import org.yu.flow.module.oss.domain.OssUploadProfileDO;

/**
 * OSS 上传配额校验（场景 Profile + 可选全局用户配额）。
 */
public interface OssQuotaService {

    /**
     * 上传前校验配额（文件数 + 容量）。
     *
     * @param profile          上传场景
     * @param uploadedBy       上传者 ID（可空）
     * @param additionalBytes  本次新增字节数
     * @param additionalFiles  本次新增文件数
     */
    void checkBeforeUpload(OssUploadProfileDO profile, String uploadedBy,
                           long additionalBytes, int additionalFiles);
}
