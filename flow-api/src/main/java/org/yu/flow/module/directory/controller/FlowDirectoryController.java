package org.yu.flow.module.directory.controller;

import org.yu.flow.module.rbac.support.RequirePerm;
import org.yu.flow.annotation.YuFlowApi;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.dto.R;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.directory.dto.FlowDirectoryDTO;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
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
@RequirePerm({"flow:api:view", "flow:api:write"})
public class FlowDirectoryController {

    @Resource
    private FlowDirectoryService flowDirectoryService;

    /**
     * 获取目录树结构。
     *
     * @param bizType 可选业务域过滤：api / task / service / model / page
     */
    @GetMapping("/tree")
    public R<List<FlowDirectoryDTO>> getTree(@RequestParam(required = false) String bizType) {
        return R.ok(flowDirectoryService.getTree(bizType));
    }

    /**
     * 目录详情
     */
    @GetMapping("/{id}")
    public R<FlowDirectoryDTO> getById(@PathVariable String id) {
        FlowDirectoryDO entity = flowDirectoryService.getById(id);
        if (entity == null) {
            return R.fail(404, "目录不存在");
        }
        FlowDirectoryDTO dto = FlowDirectoryDTO.fromDO(entity);
        dto.setEffectivePathPrefix(flowDirectoryService.resolveEffectivePathPrefix(id));
        return R.ok(dto);
    }

    /**
     * 解析目录有效 pathPrefix（根→叶各级非空前缀叠加）
     */
    @GetMapping("/{id}/effective-path-prefix")
    public R<String> effectivePathPrefix(@PathVariable String id) {
        return R.ok(flowDirectoryService.resolveEffectivePathPrefix(id));
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
