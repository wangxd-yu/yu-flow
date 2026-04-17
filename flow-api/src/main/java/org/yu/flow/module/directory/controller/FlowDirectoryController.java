package org.yu.flow.module.directory.controller;

import org.yu.flow.annotation.YuFlowApi;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.dto.R;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.dto.FlowDirectoryDTO;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 全局目录管理 Controller
 *
 * @author yu-flow
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("flow-api/directories")
public class FlowDirectoryController {

    @Resource
    private FlowDirectoryService flowDirectoryService;

    /**
     * 获取目录树结构
     */
    @GetMapping("/tree")
    public R<List<FlowDirectoryDTO>> getTree() {
        return R.ok(flowDirectoryService.getTree());
    }

    /**
     * 新增目录
     */
    @PostMapping
    public R<FlowDirectoryDO> create(@RequestBody FlowDirectoryDO directory) {
        return R.ok(flowDirectoryService.create(directory));
    }

    /**
     * 修改目录
     */
    @PutMapping("/{id}")
    public R<FlowDirectoryDO> update(@PathVariable String id, @RequestBody FlowDirectoryDO directory) {
        return R.ok(flowDirectoryService.update(id, directory));
    }

    /**
     * 删除目录（校验子节点和关联资产）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowDirectoryService.delete(id);
        return R.ok();
    }
}
