package org.yu.flow.module.sysconfig.repository;

import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置 Repository
 */
public interface SysConfigRepository extends JpaRepository<SysConfigDO, String>,
        JpaSpecificationExecutor<SysConfigDO> {

    /**
     * 根据配置键查询（精确匹配）
     */
    Optional<SysConfigDO> findByConfigKey(String configKey);

    /**
     * 判断配置键是否已存在（新增时唯一性校验）
     */
    boolean existsByConfigKey(String configKey);

    /**
     * 判断配置键是否已被其他记录占用（更新时唯一性校验，排除自身）
     */
    boolean existsByConfigKeyAndIdNot(String configKey, String id);

    /**
     * 根据状态批量查询配置项（status=1 表示启用）
     * 用于缓存管理器全量加载
     */
    List<SysConfigDO> findAllByStatus(Integer status);
}
