package org.yu.flow.module.oss.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.oss.domain.OssConnectionDO;

import java.util.List;
import java.util.Optional;

public interface OssConnectionRepository extends JpaRepository<OssConnectionDO, String>,
        JpaSpecificationExecutor<OssConnectionDO> {

    Optional<OssConnectionDO> findByCode(String code);

    boolean existsByCode(String code);

    List<OssConnectionDO> findByEnabled(Boolean enabled);
}
