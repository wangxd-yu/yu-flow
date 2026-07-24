package org.yu.flow.module.alert.repository;

import org.yu.flow.module.alert.domain.AlertChannelDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AlertChannelRepository extends JpaRepository<AlertChannelDO, String>,
        JpaSpecificationExecutor<AlertChannelDO> {

    List<AlertChannelDO> findByEnabled(Integer enabled);
}
