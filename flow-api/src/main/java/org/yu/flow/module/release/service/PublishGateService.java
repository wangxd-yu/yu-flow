package org.yu.flow.module.release.service;

import org.yu.flow.module.release.dto.PublishGateResultDTO;

public interface PublishGateService {

    /**
     * 预检门禁（不抛异常）。
     */
    PublishGateResultDTO check(String assetType, String assetId, String envCode);

    /**
     * 发布前强制门禁；未通过抛 {@link org.yu.flow.module.release.support.PublishGateException}。
     */
    void assertCanPublish(String assetType, String assetId, String envCode);
}
