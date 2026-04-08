package org.yu.flow.module.sysmacro.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.dto.SysMacroDTO;
import org.yu.flow.module.sysmacro.dto.SaveSysMacroDTO;
import org.yu.flow.module.sysmacro.query.SysMacroQueryDTO;

import java.util.List;

/**
 * 系统全局宏定义 Service 接口
 */
public interface SysMacroService {

    /**
     * 分页查询宏定义列表
     * 支持按 macroCode、macroName、macroType、scope、status 过滤
     */
    PageBean<SysMacroDTO> findPage(SysMacroQueryDTO queryDTO);

    /**
     * 根据 ID 获取宏定义详情
     */
    SysMacroDTO findById(String id);

    /**
     * 新建宏定义
     */
    SysMacroDO create(SaveSysMacroDTO dto);

    /**
     * 更新宏定义
     */
    SysMacroDO update(String id, SaveSysMacroDTO dto);

    /**
     * 删除宏定义
     */
    void delete(String id);

    /**
     * 获取所有启用状态的宏定义（status=1）
     * 供缓存管理器批量加载使用
     *
     * @return 启用状态的宏定义列表
     */
    List<SysMacroDO> getAllActiveMacros();
}
