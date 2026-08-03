package org.yu.flow.module.oss.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.dto.OssConnectionDTO;
import org.yu.flow.module.oss.dto.OssConnectionTestResultDTO;
import org.yu.flow.module.oss.query.OssConnectionQueryDTO;

import java.util.List;

public interface OssConnectionService {

    OssConnectionDO save(OssConnectionDO connectionDO);

    OssConnectionDO update(OssConnectionDO connectionDO);

    void delete(String id);

    void batchDelete(List<String> ids);

    OssConnectionDO findById(String id);

    OssConnectionDO findByCode(String code);

    PageBean<OssConnectionDTO> findPage(OssConnectionQueryDTO queryDTO);

    List<OssConnectionDTO> listEnabled();

    OssConnectionDO enable(String id);

    OssConnectionDO disable(String id);

    OssConnectionTestResultDTO testConnection(OssConnectionDO probe);
}
