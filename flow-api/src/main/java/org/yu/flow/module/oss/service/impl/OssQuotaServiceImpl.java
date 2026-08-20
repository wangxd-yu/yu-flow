package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.repository.OssObjectRepository;
import org.yu.flow.module.oss.service.OssQuotaService;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;
import org.yu.flow.module.oss.support.OssUploaderIdentity;

@ConditionalOnOssEnabled
@Service
public class OssQuotaServiceImpl implements OssQuotaService {

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Override
    public void checkBeforeUpload(OssUploadProfileDO profile, String uploadedBy, String uploadedByUserType,
                                  long additionalBytes, int additionalFiles) {
        checkBeforeUpload(profile, uploadedBy, uploadedByUserType, additionalBytes, additionalFiles, 0, 0);
    }

    @Override
    public void checkBeforeUpload(OssUploadProfileDO profile, String uploadedBy, String uploadedByUserType,
                                  long additionalBytes, int additionalFiles, long creditBytes, int creditFiles) {
        if (profile == null) {
            return;
        }
        String status = OssObjectDO.STATUS_ACTIVE;
        String profileCode = profile.getCode();
        long creditF = Math.max(0, creditFiles);
        long creditB = Math.max(0, creditBytes);

        if (profile.getQuotaMaxFiles() != null && profile.getQuotaMaxFiles() > 0) {
            long currentFiles = ossObjectRepository.countByProfileCodeAndStatus(profileCode, status);
            if (Math.max(0, currentFiles - creditF) + additionalFiles > profile.getQuotaMaxFiles()) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "场景文件数已达配额上限 " + profile.getQuotaMaxFiles());
            }
        }
        if (profile.getQuotaMaxBytes() != null && profile.getQuotaMaxBytes() > 0) {
            long currentBytes = ossObjectRepository.sumSizeBytesByProfileCodeAndStatus(profileCode, status);
            if (Math.max(0, currentBytes - creditB) + additionalBytes > profile.getQuotaMaxBytes()) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "场景容量已达配额上限 " + profile.getQuotaMaxBytes() + " 字节");
            }
        }

        long userMaxFiles = yuFlowProperties.getOss().getUserQuotaMaxFiles();
        long userMaxBytes = yuFlowProperties.getOss().getUserQuotaMaxBytes();
        String userType = OssUploaderIdentity.normalizeUserType(uploadedByUserType);
        if ((userMaxFiles <= 0 && userMaxBytes <= 0) || StrUtil.isBlank(uploadedBy) || StrUtil.isBlank(userType)) {
            return;
        }
        if (userMaxFiles > 0) {
            long currentFiles = ossObjectRepository.countByUploadedByAndUploadedByUserTypeAndStatus(
                    uploadedBy, userType, status);
            if (Math.max(0, currentFiles - creditF) + additionalFiles > userMaxFiles) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "用户文件数已达配额上限 " + userMaxFiles);
            }
        }
        if (userMaxBytes > 0) {
            long currentBytes = ossObjectRepository.sumSizeBytesByUploadedByAndUploadedByUserTypeAndStatus(
                    uploadedBy, userType, status);
            if (Math.max(0, currentBytes - creditB) + additionalBytes > userMaxBytes) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "用户容量已达配额上限 " + userMaxBytes + " 字节");
            }
        }
    }
}
