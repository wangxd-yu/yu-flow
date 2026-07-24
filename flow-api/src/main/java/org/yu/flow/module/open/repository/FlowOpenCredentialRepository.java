package org.yu.flow.module.open.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yu.flow.module.open.domain.FlowOpenCredentialDO;

import java.util.List;
import java.util.Optional;

public interface FlowOpenCredentialRepository extends JpaRepository<FlowOpenCredentialDO, String> {

    Optional<FlowOpenCredentialDO> findByAppKey(String appKey);

    List<FlowOpenCredentialDO> findByPlatformIdOrderByCreateTimeDesc(String platformId);

    long countByPlatformIdAndStatus(String platformId, Integer status);
}
