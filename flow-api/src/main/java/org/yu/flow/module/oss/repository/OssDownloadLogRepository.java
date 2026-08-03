package org.yu.flow.module.oss.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.oss.domain.OssDownloadLogDO;

public interface OssDownloadLogRepository extends JpaRepository<OssDownloadLogDO, String>,
        JpaSpecificationExecutor<OssDownloadLogDO> {
}
