package org.yu.flow.module.executionlog.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.executionlog.dto.FlowExecutionLogDTO;
import org.yu.flow.module.executionlog.query.FlowExecutionLogQueryDTO;
import org.yu.flow.module.executionlog.service.FlowExecutionLogService;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/flow-api/execution-logs")
public class FlowExecutionLogController {

    @Resource
    private FlowExecutionLogService flowExecutionLogService;

    @GetMapping("/page")
    public R<PageBean<FlowExecutionLogDTO>> getPage(FlowExecutionLogQueryDTO queryDTO) {
        Page<FlowExecutionLogDTO> pageResult = flowExecutionLogService.pageQuery(queryDTO);
        
        PageBean<FlowExecutionLogDTO> pageBean = new PageBean<>(
                pageResult.getContent(),
                pageResult.getSize(),
                pageResult.getNumber(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
        return R.ok(pageBean);
    }

    @GetMapping("/{id}")
    public R<FlowExecutionLogDTO> getById(@PathVariable String id) {
        FlowExecutionLogDTO log = flowExecutionLogService.getById(id);
        if (log != null) {
            return R.ok(log);
        }
        return R.fail("Log not found");
    }
}
