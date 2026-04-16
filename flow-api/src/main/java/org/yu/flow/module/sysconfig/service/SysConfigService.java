package org.yu.flow.module.sysconfig.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.dto.SaveSysConfigDTO;
import org.yu.flow.module.sysconfig.dto.SysConfigDTO;
import org.yu.flow.module.sysconfig.query.SysConfigQueryDTO;

/**
 * 系统配置 Service 接口
 */
public interface SysConfigService {

    PageBean<SysConfigDTO> findPage(SysConfigQueryDTO queryDTO);

    SysConfigDTO findById(String id);

    SysConfigDO create(SaveSysConfigDTO dto);

    SysConfigDO update(String id, SaveSysConfigDTO dto);

    void delete(String id);
}
