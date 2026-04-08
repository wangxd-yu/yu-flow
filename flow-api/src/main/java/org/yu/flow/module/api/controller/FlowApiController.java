package org.yu.flow.module.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.BatchMoveDTO;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowApiDTO;
import org.yu.flow.module.api.query.FlowApiQueryDTO;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.dto.R;

import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * FlowApi CRUD 管理控制器
 *
 * @author yu-flow
 * @date 2025-03-05 23:32
 */
@Slf4j
@RestController
@RequestMapping(value = {"flow-api/api"})
public class FlowApiController {

    @Resource
    private FlowApiCrudService flowApiCrudService;

    @PostMapping
    public R<FlowApiDO> create(@RequestBody FlowApiDO flowApiDO) {
        FlowApiDO savedConfig = flowApiCrudService.save(flowApiDO);
        return R.ok(savedConfig);
    }

    @PostMapping("/batch/create")
    public R<List<FlowApiDO>> batchCreate(@RequestBody List<FlowApiDO> flowApiDOList) {
        List<FlowApiDO> savedList = flowApiCrudService.batchSave(flowApiDOList);
        return R.ok(savedList);
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        flowApiCrudService.batchDelete(ids);
        return R.ok();
    }

    @PutMapping("/batch/moveToDir")
    public R<Void> batchMove(@RequestBody BatchMoveDTO batchMoveDTO) {
        flowApiCrudService.batchMove(batchMoveDTO.getIds(), batchMoveDTO.getTargetDirectoryId());
        return R.ok();
    }

    /**
     * 校验 API 是否已被占用 (精确匹配 URL 和 Method)
     *
     * @param url    API 路径
     * @param method 请求方法
     * @return true-已占用/存在冲突, false-可用
     */
    @GetMapping("/check-exact")
    public R<Boolean> checkExact(@RequestParam String url, @RequestParam String method) {
        return R.ok(flowApiCrudService.existsByUrlAndMethod(url, method));
    }

    @PutMapping("/{id}")
    public R<FlowApiDO> update(@PathVariable String id, @RequestBody FlowApiDO flowApiDO) {
        flowApiDO.setId(id);
        return R.ok(flowApiCrudService.update(flowApiDO));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowApiCrudService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<FlowApiDO> getById(@PathVariable String id) {
        return R.ok(flowApiCrudService.findById(id));
    }

    @GetMapping
    public R<List<FlowApiDTO>> getAll() {
        List<FlowApiDTO> configs = flowApiCrudService.findAll();
        return R.ok(configs);
    }

    @GetMapping("/page")
    public R<PageBean<FlowApiDTO>> getPage(FlowApiQueryDTO queryDTO) {
        return R.ok(flowApiCrudService.findPage(queryDTO));
    }

    @GetMapping("/publish-status/{status}")
    public R<List<FlowApiDTO>> getByPublishStatus(@PathVariable Integer status) {
        List<FlowApiDTO> configs = flowApiCrudService.findByPublishStatus(status);
        return R.ok(configs);
    }

    @GetMapping("/name/{name}")
    public R<FlowApiDTO> getByName(@PathVariable String name) {
        FlowApiDTO config = flowApiCrudService.findByName(name);
        return R.ok(config);
    }
}
