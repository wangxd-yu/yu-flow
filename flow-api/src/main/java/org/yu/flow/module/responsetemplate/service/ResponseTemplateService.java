package org.yu.flow.module.responsetemplate.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.dto.ResponseTemplateDTO;
import org.yu.flow.module.responsetemplate.dto.SaveResponseTemplateDTO;
import org.yu.flow.module.responsetemplate.query.ResponseTemplateQueryDTO;

import java.util.List;

/**
 * API 响应模板 Service 接口
 */
public interface ResponseTemplateService {

    PageBean<ResponseTemplateDTO> findPage(ResponseTemplateQueryDTO queryDTO);

    ResponseTemplateDTO findById(String id);

    /**
     * 查询所有模板简要信息（用于前端 Select 下拉选项）
     */
    List<ResponseTemplateDTO> findAll();

    ResponseTemplateDO create(SaveResponseTemplateDTO dto);

    ResponseTemplateDO update(String id, SaveResponseTemplateDTO dto);

    void delete(String id);

    /**
     * 将指定模板设为全局默认
     */
    void setDefault(String id);
}
