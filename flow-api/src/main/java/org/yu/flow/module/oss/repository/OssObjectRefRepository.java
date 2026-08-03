package org.yu.flow.module.oss.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.oss.domain.OssObjectRefDO;

import java.util.List;
import java.util.Optional;

public interface OssObjectRefRepository extends JpaRepository<OssObjectRefDO, String> {

    boolean existsByObjectIdAndBizTypeAndBizId(String objectId, String bizType, String bizId);

    long countByObjectId(String objectId);

    List<OssObjectRefDO> findByObjectId(String objectId);

    Optional<OssObjectRefDO> findByObjectIdAndBizTypeAndBizId(String objectId, String bizType, String bizId);

    void deleteByObjectIdAndBizTypeAndBizId(String objectId, String bizType, String bizId);
}
