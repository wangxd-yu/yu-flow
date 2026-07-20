package org.yu.flow.module.serviceflow.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.dto.FlowDebugRequestDTO;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.dto.FlowServiceFlowDTO;
import org.yu.flow.module.serviceflow.query.FlowServiceFlowQueryDTO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowExecutionService;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowService;

import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@YuFlowApi
@RestController
@RequestMapping("/flow-api/service-flow")
public class FlowServiceFlowController {

    @Resource
    private FlowServiceFlowService flowServiceFlowService;

    @Resource
    private FlowServiceFlowExecutionService flowServiceFlowExecutionService;

    @Resource
    private FlowEngine flowEngine;

    @PostMapping
    public R<FlowServiceFlowDO> create(@RequestBody FlowServiceFlowDO body) {
        return R.ok(flowServiceFlowService.save(body));
    }

    @PutMapping("/{id}")
    public R<FlowServiceFlowDO> update(@PathVariable String id, @RequestBody FlowServiceFlowDO body) {
        body.setId(id);
        return R.ok(flowServiceFlowService.update(body));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowServiceFlowService.delete(id);
        return R.ok();
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        flowServiceFlowService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<FlowServiceFlowDTO> getById(@PathVariable String id) {
        return R.ok(FlowServiceFlowDTO.fromDO(flowServiceFlowService.findById(id)));
    }

    @GetMapping("/page")
    public R<PageBean<FlowServiceFlowDTO>> getPage(FlowServiceFlowQueryDTO queryDTO) {
        return R.ok(flowServiceFlowService.findPage(queryDTO));
    }

    @PutMapping("/{id}/enable")
    public R<FlowServiceFlowDO> enable(@PathVariable String id) {
        return R.ok(flowServiceFlowService.enable(id));
    }

    @PutMapping("/{id}/disable")
    public R<FlowServiceFlowDO> disable(@PathVariable String id) {
        return R.ok(flowServiceFlowService.disable(id));
    }

    @PutMapping("/{id}/log-enabled")
    public R<FlowServiceFlowDO> updateLogEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        return R.ok(flowServiceFlowService.updateLogEnabled(id, enabled));
    }

    @PostMapping("/{id}/publish")
    public R<FlowServiceFlowDO> publish(@PathVariable String id) {
        return R.ok(flowServiceFlowService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public R<FlowServiceFlowDO> unpublish(@PathVariable String id) {
        return R.ok(flowServiceFlowService.unpublish(id));
    }

    @PostMapping("/{id}/republish")
    public R<FlowServiceFlowDO> republish(@PathVariable String id) {
        return R.ok(flowServiceFlowService.republish(id));
    }

    @PostMapping("/{id}/rollback")
    public R<FlowServiceFlowDO> rollback(@PathVariable String id) {
        return R.ok(flowServiceFlowService.rollbackToPublished(id));
    }

    @GetMapping("/{id}/versions")
    public R<List<FlowAssetVersionDTO>> listVersions(@PathVariable String id) {
        return R.ok(flowServiceFlowService.listVersions(id));
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public R<FlowServiceFlowDO> restoreVersion(@PathVariable String id, @PathVariable String versionId) {
        return R.ok(flowServiceFlowService.restoreVersion(id, versionId));
    }

    /**
     * 手动同步执行一次（返回业务输出）。
     */
    @PostMapping("/{id}/run")
    public R<Object> run(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body != null ? body : Collections.emptyMap();
        try {
            Object out = flowServiceFlowExecutionService.executeById(id, input, "MANUAL", false);
            return R.ok(out);
        } catch (Exception e) {
            log.error("Service flow run failed: {}", id, e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 调试运行（同步返回 FlowTrace）。
     */
    @PostMapping("/debug/run")
    public R<FlowTrace> debugRun(@RequestBody FlowDebugRequestDTO requestDTO) {
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("serviceName", requestDTO.getSourceName());
            args.put("serviceId", requestDTO.getSourceRef());
            // 调试入参：body 若为 JSON 对象则作为 $.service.input
            if (requestDTO.getBody() != null && !requestDTO.getBody().isBlank()) {
                try {
                    Object parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(requestDTO.getBody(), Object.class);
                    if (parsed instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> input = (Map<String, Object>) parsed;
                        args.put("input", input);
                    } else {
                        args.put("input", Collections.singletonMap("body", parsed));
                    }
                } catch (Exception ignore) {
                    args.put("input", Collections.singletonMap("body", requestDTO.getBody()));
                }
            }
            FlowTrace trace = flowEngine.execute(
                    requestDTO.getDslContent(),
                    args,
                    true,
                    "DEBUG",
                    requestDTO.getSourceRef(),
                    requestDTO.getSourceName()
            );
            return R.ok(trace != null ? trace : new FlowTrace());
        } catch (Exception e) {
            log.error("Service flow debug run failed", e);
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
}
