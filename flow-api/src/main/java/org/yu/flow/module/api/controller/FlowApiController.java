package org.yu.flow.module.api.controller;

import org.yu.flow.annotation.YuFlowApi;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.BatchMoveDTO;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowApiDTO;
import org.yu.flow.module.api.query.FlowApiQueryDTO;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.dto.FlowDebugRequestDTO;
import org.yu.flow.dto.R;

import org.springframework.web.bind.annotation.*;
import org.yu.flow.module.api.service.FlowApiCrudService;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FlowApi CRUD 管理控制器
 *
 * @author yu-flow
 * @date 2025-03-05 23:32
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping(value = {"flow-api/api"})
public class FlowApiController {

    @Resource
    private FlowApiCrudService flowApiCrudService;

    @Resource
    private FlowEngine flowEngine;

    @PostMapping("/debug/run")
    public R<FlowTrace> debugRun(@RequestBody FlowDebugRequestDTO requestDTO) {
        try {
            Map<String, Object> args = new HashMap<>();
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("headers", requestDTO.getHeaders() != null ? requestDTO.getHeaders() : new HashMap<>());
            requestMap.put("params", requestDTO.getQueryParams() != null ? requestDTO.getQueryParams() : new HashMap<>());

            // Try to parse body as JSON if possible, otherwise keep as string
            Object parsedBody = requestDTO.getBody();
            if (requestDTO.getBody() != null && !requestDTO.getBody().trim().isEmpty()) {
                try {
                    parsedBody = cn.hutool.json.JSONUtil.parse(requestDTO.getBody());
                } catch (Exception e) {
                    // Ignore parse error, treat as raw string
                }
            }
            requestMap.put("body", parsedBody);
            args.put("request", requestMap);

            // Execute flow engine in trace mode
            FlowTrace trace = flowEngine.execute(requestDTO.getDslContent(), args, true);

            return R.ok(trace != null ? trace : new FlowTrace());
        } catch (Exception e) {
            log.error("Debug run failed", e);
            ExecutionLog errorLog = new ExecutionLog()
                .setId("err_global")
                .setNodeId("__global__")
                .setNodeName("Global Error")
                .setNodeType("error")
                .setStatus("error")
                .setStartTime(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()))
                .setError(e.getMessage());
            FlowTrace errorTrace = new FlowTrace();
            errorTrace.setStatus("error");
            errorTrace.setErrorMsg(e.getMessage());
            errorTrace.setStepLogs(Collections.singletonList(errorLog));
            return R.ok(errorTrace);
        }
    }


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
