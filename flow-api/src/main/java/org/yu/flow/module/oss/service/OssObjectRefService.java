package org.yu.flow.module.oss.service;

import org.yu.flow.module.oss.dto.OssObjectRefDTO;

import java.util.List;

public interface OssObjectRefService {

    OssObjectRefDTO bind(String objectId, String bizType, String bizId);

    void unbind(String objectId, String bizType, String bizId);

    List<OssObjectRefDTO> listByObjectId(String objectId);

    long countByObjectId(String objectId);
}
