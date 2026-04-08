package org.yu.flow.module.model.repository;

import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 数据模型信息 Repository
 */
public interface FlowModelInfoRepository extends JpaRepository<FlowModelInfoDO, String>, JpaSpecificationExecutor<FlowModelInfoDO> {

    /**
     * 判断表名是否已存在
     */
    boolean existsByTableName(String tableName);

    /**
     * 判断表名是否已被其他记录占用（排除自身，用于更新校验）
     */
    boolean existsByTableNameAndIdNot(String tableName, String id);

    /**
     * 判断某个目录下是否有模型（删除目录时校验）
     */
    boolean existsByDirectoryId(String directoryId);

    @Modifying
    @Query("UPDATE FlowModelInfoDO m SET m.directoryId = :directoryId WHERE m.id IN :ids")
    int updateDirectoryIdByIds(@Param("directoryId") String directoryId, @Param("ids") List<String> ids);
}
