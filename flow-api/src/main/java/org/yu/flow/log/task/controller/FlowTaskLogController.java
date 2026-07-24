package org.yu.flow.log.task.controller;

import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.log.task.dto.FlowTaskLogDTO;
import org.yu.flow.log.task.dto.FlowTaskLogListDTO;
import org.yu.flow.log.task.query.FlowTaskLogQueryDTO;
import org.yu.flow.log.task.service.FlowTaskLogService;

import jakarta.annotation.Resource;

/**
 * 任务日志 REST 控制器
 *
 * @author yu-flow
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/log/task")
@RequirePerm("log:view")
public class FlowTaskLogController {

    @Resource
    private FlowTaskLogService flowTaskLogService;

    /**
     * 分页列表（轻量，不含大字段）
     */
    @GetMapping("/page")
    public R<PageBean<FlowTaskLogListDTO>> getPage(FlowTaskLogQueryDTO queryDTO) {
        Page<FlowTaskLogListDTO> pageResult = flowTaskLogService.pageList(queryDTO);
        PageBean<FlowTaskLogListDTO> pageBean = new PageBean<>(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
        return R.ok(pageBean);
    }

    /**
     * 按 ID 查询完整详情（含 traceData，用于快照回放）
     */
    @GetMapping("/{id}")
    public R<FlowTaskLogDTO> getById(@PathVariable String id) {
        FlowTaskLogDTO log = flowTaskLogService.getById(id);
        if (log != null) {
            return R.ok(log);
        }
        return R.fail("Log not found");
    }

    /**
     * 清空某任务的全部日志
     */
    @DeleteMapping("/clear/{taskId}")
    public R<Void> clear(@PathVariable String taskId) {
        flowTaskLogService.clearByTaskId(taskId);
        return R.ok();
    }
}
