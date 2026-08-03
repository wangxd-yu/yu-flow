package org.yu.flow.module.oss.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.dto.OssUploadProfileDTO;
import org.yu.flow.module.oss.query.OssUploadProfileQueryDTO;

import java.util.List;

public interface OssUploadProfileService {

    OssUploadProfileDO save(OssUploadProfileDO profileDO);

    OssUploadProfileDO update(OssUploadProfileDO profileDO);

    void delete(String id);

    void batchDelete(List<String> ids);

    OssUploadProfileDO findById(String id);

    OssUploadProfileDO requireByCode(String code);

    PageBean<OssUploadProfileDTO> findPage(OssUploadProfileQueryDTO queryDTO);

    List<OssUploadProfileDTO> listEnabled();
}
