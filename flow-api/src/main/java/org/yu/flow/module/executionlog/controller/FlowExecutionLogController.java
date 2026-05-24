package org.yu.flow.module.executionlog.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.executionlog.dto.FlowExecutionLogDTO;
import org.yu.flow.module.executionlog.dto.FlowExecutionLogListDTO;
import org.yu.flow.module.executionlog.query.FlowExecutionLogQueryDTO;
import org.yu.flow.module.executionlog.service.FlowExecutionLogService;

import javax.annotation.Resource;

@RestController
@RequestMapping("/flow-api/execution-logs")
public class FlowExecutionLogController {

    @Resource
    private FlowExecutionLogService flowExecutionLogService;

    /**
     * 分页列表（轻量，不含大字段，供大盘表格使用）
     */
    @GetMapping("/page")
    public R<PageBean<FlowExecutionLogListDTO>> getPage(FlowExecutionLogQueryDTO queryDTO) {
        Page<FlowExecutionLogListDTO> pageResult = flowExecutionLogService.pageList(queryDTO);
        PageBean<FlowExecutionLogListDTO> pageBean = new PageBean<>(
                pageResult.getContent(),
                pageResult.getSize(),
                pageResult.getNumber(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
        return R.ok(pageBean);
    }

    /**
     * 按 ID 查询完整详情（含 traceData/requestParams/responseBody 等大字段，用于快照回放）
     */
    @GetMapping("/{id}")
    public R<FlowExecutionLogDTO> getById(@PathVariable String id) {
        FlowExecutionLogDTO log = flowExecutionLogService.getById(id);
        if (log != null) {
            return R.ok(log);
        }
        return R.fail("Log not found");
    }
}

