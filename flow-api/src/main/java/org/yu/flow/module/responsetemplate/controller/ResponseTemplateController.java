package org.yu.flow.module.responsetemplate.controller;

import org.yu.flow.annotation.YuFlowApi;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.responsetemplate.cache.ResponseTemplateCacheManager;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.responsetemplate.dto.ResponseTemplateDTO;
import org.yu.flow.module.responsetemplate.dto.SaveResponseTemplateDTO;
import org.yu.flow.module.responsetemplate.query.ResponseTemplateQueryDTO;
import org.yu.flow.module.responsetemplate.service.ResponseTemplateService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * API 响应模板管理 Controller
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/response-templates")
public class ResponseTemplateController {

    @Resource
    private ResponseTemplateService responseTemplateService;

    @Resource
    private ResponseTemplateCacheManager responseTemplateCacheManager;

    /**
     * 分页查询模板列表
     */
    @GetMapping("/page")
    public R<PageBean<ResponseTemplateDTO>> getPage(ResponseTemplateQueryDTO queryDTO) {
        return R.ok(responseTemplateService.findPage(queryDTO));
    }

    /**
     * 查询所有模板（用于前端下拉选择）
     */
    @GetMapping("/list")
    public R<List<ResponseTemplateDTO>> getAll() {
        return R.ok(responseTemplateService.findAll());
    }

    /**
     * 根据 ID 查询模板详情
     */
    @GetMapping("/{id}")
    public R<ResponseTemplateDTO> getById(@PathVariable String id) {
        return R.ok(responseTemplateService.findById(id));
    }

    /**
     * 获取全局默认模板（网关运行时调用，命中缓存）
     */
    @GetMapping("/default")
    public R<ResponseTemplateDO> getDefault() {
        return R.ok(responseTemplateCacheManager.getDefaultTemplate().orElse(null));
    }

    /**
     * 新建模板
     */
    @PostMapping
    public R<ResponseTemplateDO> create(@Valid @RequestBody SaveResponseTemplateDTO dto) {
        return R.ok(responseTemplateService.create(dto));
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    public R<ResponseTemplateDO> update(@PathVariable String id, @RequestBody SaveResponseTemplateDTO dto) {
        return R.ok(responseTemplateService.update(id, dto));
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        responseTemplateService.delete(id);
        return R.ok();
    }

    /**
     * 设置指定模板为全局默认
     */
    @PutMapping("/{id}/set-default")
    public R<Void> setDefault(@PathVariable String id) {
        responseTemplateService.setDefault(id);
        return R.ok();
    }
}
