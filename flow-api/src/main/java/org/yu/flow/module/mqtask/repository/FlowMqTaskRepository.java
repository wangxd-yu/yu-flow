package org.yu.flow.module.mqtask.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;

import java.util.List;

/**
 * MQ 任务 JPA Repository
 *
 * @author yu-flow
 */
public interface FlowMqTaskRepository extends JpaRepository<FlowMqTaskDO, String>, JpaSpecificationExecutor<FlowMqTaskDO> {

    /** 查询所有启用的任务（用于消费者管理器启动时加载） */
    List<FlowMqTaskDO> findByEnabled(Boolean enabled);

    /** 判断某个目录下是否有 MQ 任务（删除目录时校验） */
    boolean existsByDirectoryId(String directoryId);

    /** 查询配置了任务级日志保留天数的任务（[id, 天数]），供日志清理按任务覆盖 */
    @Query("SELECT t.id, t.logRetentionDays FROM FlowMqTaskDO t WHERE t.logRetentionDays IS NOT NULL")
    List<Object[]> findLogRetentionOverrides();

    /** 判断某个连接编码是否被任务引用（删除连接时校验） */
    boolean existsByConnectionCode(String connectionCode);

    /**
     * 已发布任务的订阅坐标（[id, connectionCode, topic, consumerGroup, publishedSnapshot]），供积压查询使用。
     * <p>只投影必要列，避免把每个任务的 MEDIUMTEXT 草稿 DSL 一并拉进内存。</p>
     */
    @Query("SELECT t.id, t.connectionCode, t.topic, t.consumerGroup, t.publishedSnapshot FROM FlowMqTaskDO t "
            + "WHERE t.publishStatus = 1 AND t.publishedSnapshot IS NOT NULL")
    List<Object[]> findPublishedSubscribeTargets();

    @Modifying
    @Query("UPDATE FlowMqTaskDO t SET t.directoryId = :directoryId WHERE t.id IN :ids")
    int updateDirectoryIdByIds(@Param("directoryId") String directoryId, @Param("ids") List<String> ids);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FlowMqTaskDO t SET t.deleted = 1 WHERE t.id IN :ids")
    int logicDeleteByIds(@Param("ids") List<String> ids);
}
