package org.yu.flow.log.service.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.log.service.dto.FlowServiceLogDTO;
import org.yu.flow.log.service.dto.FlowServiceLogListDTO;
import org.yu.flow.log.service.query.FlowServiceLogQueryDTO;
import org.yu.flow.log.service.service.FlowServiceLogService;

import jakarta.annotation.Resource;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/log/service")
public class FlowServiceLogController {

    @Resource
    private FlowServiceLogService flowServiceLogService;

    @GetMapping("/page")
    public R<PageBean<FlowServiceLogListDTO>> getPage(FlowServiceLogQueryDTO queryDTO) {
        Page<FlowServiceLogListDTO> pageResult = flowServiceLogService.pageList(queryDTO);
        PageBean<FlowServiceLogListDTO> pageBean = new PageBean<>(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
        return R.ok(pageBean);
    }

    @GetMapping("/{id}")
    public R<FlowServiceLogDTO> getById(@PathVariable String id) {
        FlowServiceLogDTO log = flowServiceLogService.getById(id);
        if (log != null) {
            return R.ok(log);
        }
        return R.fail("Log not found");
    }

    @DeleteMapping("/clear/{serviceId}")
    public R<Void> clear(@PathVariable String serviceId) {
        flowServiceLogService.clearByServiceId(serviceId);
        return R.ok();
    }
}
