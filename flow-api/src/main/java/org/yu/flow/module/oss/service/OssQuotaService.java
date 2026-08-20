package org.yu.flow.module.oss.service;

import org.yu.flow.module.oss.domain.OssUploadProfileDO;

/**
 * OSS 上传配额校验（场景 Profile + 可选全局用户配额）。
 */
public interface OssQuotaService {

    /**
     * 上传前校验配额（文件数 + 容量）。
     *
     * @param profile             上传场景
     * @param uploadedBy          上传者 ID（可空）
     * @param uploadedByUserType  上传者 userType（可空；与 ID 一起构成用户配额键）
     * @param additionalBytes     本次新增字节数
     * @param additionalFiles     本次新增文件数
     */
    void checkBeforeUpload(OssUploadProfileDO profile, String uploadedBy, String uploadedByUserType,
                           long additionalBytes, int additionalFiles);

    /**
     * 上传前校验配额。{@code credit*} 从当前占用中抵扣（例如即将软删的原 zip）。
     */
    void checkBeforeUpload(OssUploadProfileDO profile, String uploadedBy, String uploadedByUserType,
                           long additionalBytes, int additionalFiles, long creditBytes, int creditFiles);
}
