package org.yu.flow.module.envvar.service;

import org.yu.flow.module.envvar.dto.EnvVariableDictVO;
import org.yu.flow.module.envvar.dto.SaveSysEnvVariableDTO;
import org.yu.flow.module.envvar.dto.SysEnvVariableDTO;

import java.util.List;

public interface SysEnvVariableService {

    List<SysEnvVariableDTO> list(String keyword);

    List<EnvVariableDictVO> dictionary();

    SysEnvVariableDTO create(SaveSysEnvVariableDTO dto);

    SysEnvVariableDTO update(String id, SaveSysEnvVariableDTO dto);

    void delete(String id);
}
