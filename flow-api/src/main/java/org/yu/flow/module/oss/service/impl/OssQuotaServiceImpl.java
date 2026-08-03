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

@Service
public class OssQuotaServiceImpl implements OssQuotaService {

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Override
    public void checkBeforeUpload(OssUploadProfileDO profile, String uploadedBy,
                                  long additionalBytes, int additionalFiles) {
        if (profile == null) {
            return;
        }
        String status = OssObjectDO.STATUS_ACTIVE;
        String profileCode = profile.getCode();

        if (profile.getQuotaMaxFiles() != null && profile.getQuotaMaxFiles() > 0) {
            long currentFiles = ossObjectRepository.countByProfileCodeAndStatus(profileCode, status);
            if (currentFiles + additionalFiles > profile.getQuotaMaxFiles()) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "场景文件数已达配额上限 " + profile.getQuotaMaxFiles());
            }
        }
        if (profile.getQuotaMaxBytes() != null && profile.getQuotaMaxBytes() > 0) {
            long currentBytes = ossObjectRepository.sumSizeBytesByProfileCodeAndStatus(profileCode, status);
            if (currentBytes + additionalBytes > profile.getQuotaMaxBytes()) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "场景容量已达配额上限 " + profile.getQuotaMaxBytes() + " 字节");
            }
        }

        long userMaxFiles = yuFlowProperties.getOss().getUserQuotaMaxFiles();
        long userMaxBytes = yuFlowProperties.getOss().getUserQuotaMaxBytes();
        if ((userMaxFiles <= 0 && userMaxBytes <= 0) || StrUtil.isBlank(uploadedBy)) {
            return;
        }
        if (userMaxFiles > 0) {
            long currentFiles = ossObjectRepository.countByUploadedByAndStatus(uploadedBy, status);
            if (currentFiles + additionalFiles > userMaxFiles) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "用户文件数已达配额上限 " + userMaxFiles);
            }
        }
        if (userMaxBytes > 0) {
            long currentBytes = ossObjectRepository.sumSizeBytesByUploadedByAndStatus(uploadedBy, status);
            if (currentBytes + additionalBytes > userMaxBytes) {
                throw new FlowException("OSS_QUOTA_EXCEEDED",
                        "用户容量已达配额上限 " + userMaxBytes + " 字节");
            }
        }
    }
}
