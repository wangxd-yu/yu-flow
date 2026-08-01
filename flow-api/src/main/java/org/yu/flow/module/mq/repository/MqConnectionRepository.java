package org.yu.flow.module.mq.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yu.flow.module.mq.domain.MqConnectionDO;

import java.util.List;
import java.util.Optional;

/**
 * MQ 连接配置 JPA Repository
 *
 * @author yu-flow
 */
public interface MqConnectionRepository extends JpaRepository<MqConnectionDO, String>,
        JpaSpecificationExecutor<MqConnectionDO> {

    /** 按编码查询（流程节点 / MQ 任务通过 code 引用连接） */
    Optional<MqConnectionDO> findByCode(String code);

    /** 编码唯一性校验 */
    boolean existsByCode(String code);

    /** 全部启用的连接（下拉选项 / 消费管理器加载） */
    List<MqConnectionDO> findByEnabled(Boolean enabled);
}
