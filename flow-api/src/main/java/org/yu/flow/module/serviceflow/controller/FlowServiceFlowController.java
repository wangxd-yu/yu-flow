package org.yu.flow.module.serviceflow.controller;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.ContractParamTypeConverter;
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

    @Resource
    private ContractParamTypeConverter contractParamTypeConverter;

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

    @PutMapping("/{id}/publish")
    public R<FlowServiceFlowDO> publish(@PathVariable String id,
                                        @RequestParam(required = false, defaultValue = "DEV") String envCode) {
        return R.ok(flowServiceFlowService.publish(id, envCode));
    }

    @PutMapping("/{id}/unpublish")
    public R<FlowServiceFlowDO> unpublish(@PathVariable String id) {
        return R.ok(flowServiceFlowService.unpublish(id));
    }

    /**
     * 查询仍引用该服务的资产标签（下线前确认用）。
     */
    @GetMapping("/{id}/references")
    public R<List<String>> listReferences(@PathVariable String id) {
        return R.ok(flowServiceFlowService.listReferenceLabels(id));
    }

    @PutMapping("/{id}/republish")
    public R<FlowServiceFlowDO> republish(@PathVariable String id,
                                          @RequestParam(required = false, defaultValue = "DEV") String envCode) {
        return R.ok(flowServiceFlowService.publish(id, envCode));
    }

    @PutMapping("/{id}/rollback")
    public R<FlowServiceFlowDO> rollback(@PathVariable String id) {
        return R.ok(flowServiceFlowService.rollbackToPublished(id));
    }

    @GetMapping("/{id}/versions")
    public R<List<FlowAssetVersionDTO>> listVersions(@PathVariable String id) {
        return R.ok(flowServiceFlowService.listVersions(id));
    }

    @PutMapping("/{id}/versions/{versionId}/restore")
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
     *
     * <p>入参转换/必填校验与 CALL 共用 {@link ContractParamTypeConverter#convertAndValidateServiceInputs}；
     * 仍使用客户端草稿 DSL，不走 {@code executeById} 的业务解包路径。</p>
     */
    @PostMapping("/debug/run")
    public R<FlowTrace> debugRun(@RequestBody FlowDebugRequestDTO requestDTO) {
        try {
            Map<String, Object> rawInput = parseDebugInputBody(requestDTO.getBody());
            String contractJson = resolveDebugContract(requestDTO);
            Map<String, Object> typedInput =
                    contractParamTypeConverter.convertAndValidateServiceInputs(contractJson, rawInput);

            Map<String, Object> args = new HashMap<>();
            args.put("serviceName", requestDTO.getSourceName());
            args.put("serviceId", requestDTO.getSourceRef());
            args.put("input", typedInput);

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

    /** body JSON → $.service.input Map；空 body 视为空对象 */
    private Map<String, Object> parseDebugInputBody(String body) {
        if (body == null || body.isBlank()) {
            return new HashMap<>();
        }
        try {
            Object parsed = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper()
                    .readValue(body, Object.class);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> input = (Map<String, Object>) parsed;
                return new HashMap<>(input);
            }
            return new HashMap<>(Collections.singletonMap("body", parsed));
        } catch (Exception ignore) {
            return new HashMap<>(Collections.singletonMap("body", body));
        }
    }

    /** 请求体 contract 优先，否则按 sourceRef 加载草稿契约 */
    private String resolveDebugContract(FlowDebugRequestDTO requestDTO) {
        if (StrUtil.isNotBlank(requestDTO.getContract())) {
            return requestDTO.getContract();
        }
        if (StrUtil.isBlank(requestDTO.getSourceRef())) {
            return null;
        }
        FlowServiceFlowDO svc = flowServiceFlowService.findById(requestDTO.getSourceRef());
        return svc != null ? svc.getContract() : null;
    }
}
