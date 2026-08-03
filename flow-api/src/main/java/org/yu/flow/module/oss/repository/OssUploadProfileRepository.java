package org.yu.flow.module.oss.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;

import java.util.List;
import java.util.Optional;

public interface OssUploadProfileRepository extends JpaRepository<OssUploadProfileDO, String>,
        JpaSpecificationExecutor<OssUploadProfileDO> {

    Optional<OssUploadProfileDO> findByCode(String code);

    boolean existsByCode(String code);

    List<OssUploadProfileDO> findByEnabled(Boolean enabled);
}
