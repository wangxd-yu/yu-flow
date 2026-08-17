package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.oss.domain.OssObjectDO;
import org.yu.flow.module.oss.domain.OssObjectRefDO;
import org.yu.flow.module.oss.dto.OssObjectRefDTO;
import org.yu.flow.module.oss.repository.OssObjectRefRepository;
import org.yu.flow.module.oss.repository.OssObjectRepository;
import org.yu.flow.module.oss.service.OssObjectRefService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@ConditionalOnOssEnabled
@Service
public class OssObjectRefServiceImpl implements OssObjectRefService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private OssObjectRefRepository ossObjectRefRepository;

    @Resource
    private OssObjectRepository ossObjectRepository;

    @Override
    @Transactional
    public OssObjectRefDTO bind(String objectId, String bizType, String bizId) {
        requireActiveObject(objectId);
        if (StrUtil.isBlank(bizType) || StrUtil.isBlank(bizId)) {
            throw new FlowException("OSS_REF_PARAM_REQUIRED", "bizType 与 bizId 不能为空");
        }
        if (ossObjectRefRepository.existsByObjectIdAndBizTypeAndBizId(objectId, bizType, bizId)) {
            return ossObjectRefRepository.findByObjectIdAndBizTypeAndBizId(objectId, bizType, bizId)
                    .map(OssObjectRefDTO::fromDO)
                    .orElse(null);
        }
        OssObjectRefDO entity = OssObjectRefDO.builder()
                .objectId(objectId)
                .bizType(bizType.trim())
                .bizId(bizId.trim())
                .createTime(LocalDateTime.now(ZONE_SH))
                .build();
        entity = ossObjectRefRepository.save(entity);
        return OssObjectRefDTO.fromDO(entity);
    }

    @Override
    @Transactional
    public void unbind(String objectId, String bizType, String bizId) {
        requireActiveObject(objectId);
        if (StrUtil.isBlank(bizType) || StrUtil.isBlank(bizId)) {
            throw new FlowException("OSS_REF_PARAM_REQUIRED", "bizType 与 bizId 不能为空");
        }
        ossObjectRefRepository.deleteByObjectIdAndBizTypeAndBizId(objectId, bizType, bizId);
    }

    @Override
    public List<OssObjectRefDTO> listByObjectId(String objectId) {
        requireActiveObject(objectId);
        return ossObjectRefRepository.findByObjectId(objectId).stream()
                .map(OssObjectRefDTO::fromDO)
                .collect(Collectors.toList());
    }

    @Override
    public long countByObjectId(String objectId) {
        return ossObjectRefRepository.countByObjectId(objectId);
    }

    private void requireActiveObject(String objectId) {
        OssObjectDO object = ossObjectRepository.findById(objectId).orElse(null);
        if (object == null || !OssObjectDO.STATUS_ACTIVE.equals(object.getStatus())) {
            throw new FlowException("OSS_OBJECT_NOT_FOUND", "文件不存在: " + objectId);
        }
    }
}
