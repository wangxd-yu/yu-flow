package org.yu.flow.log.third.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.log.third.dto.FlowThirdLogDTO;
import org.yu.flow.log.third.dto.FlowThirdLogListDTO;
import org.yu.flow.log.third.query.FlowThirdLogQueryDTO;
import org.yu.flow.log.third.service.FlowThirdLogService;

import jakarta.annotation.Resource;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/log/third")
public class FlowThirdLogController {

    @Resource
    private FlowThirdLogService flowThirdLogService;

    @GetMapping("/page")
    public R<PageBean<FlowThirdLogListDTO>> getPage(FlowThirdLogQueryDTO queryDTO) {
        Page<FlowThirdLogListDTO> pageResult = flowThirdLogService.pageList(queryDTO);
        PageBean<FlowThirdLogListDTO> pageBean = new PageBean<>(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
        return R.ok(pageBean);
    }

    @GetMapping("/{id}")
    public R<FlowThirdLogDTO> getById(@PathVariable String id) {
        FlowThirdLogDTO log = flowThirdLogService.getById(id);
        if (log != null) {
            return R.ok(log);
        }
        return R.fail("Log not found");
    }
}
