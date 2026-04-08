package org.yu.flow.module.sysmacro.repository;

import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 系统全局宏定义 Repository
 */
public interface SysMacroRepository extends JpaRepository<SysMacroDO, String>,
        JpaSpecificationExecutor<SysMacroDO> {

    /**
     * 判断宏编码是否已存在（新增时唯一性校验）
     */
    boolean existsByMacroCode(String macroCode);

    /**
     * 判断宏编码是否已被其他记录占用（更新时唯一性校验，排除自身）
     */
    boolean existsByMacroCodeAndIdNot(String macroCode, String id);

    /**
     * 根据状态批量查询宏定义（status=1 表示启用）
     * 用于缓存管理器全量加载
     */
    List<SysMacroDO> findAllByStatus(Integer status);
}
