package org.yu.flow.module.task.controller;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.dto.FlowDebugRequestDTO;
import org.yu.flow.module.assetversion.dto.FlowAssetVersionDTO;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.dto.FlowTaskDTO;
import org.yu.flow.module.task.query.FlowTaskQueryDTO;
import org.yu.flow.module.task.scheduler.FlowTaskScheduler;
import org.yu.flow.module.task.service.FlowTaskService;

import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 定时任务管理 REST 控制器
 *
 * @author yu-flow
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("/flow-api/task")
public class FlowTaskController {

    @Resource
    private FlowTaskService flowTaskService;

    @Resource
    private FlowTaskScheduler flowTaskScheduler;

    @Resource
    private FlowEngine flowEngine;

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    public R<FlowTaskDO> create(@RequestBody FlowTaskDO taskDO) {
        return R.ok(flowTaskService.save(taskDO));
    }

    @PutMapping("/{id}")
    public R<FlowTaskDO> update(@PathVariable String id, @RequestBody FlowTaskDO taskDO) {
        taskDO.setId(id);
        return R.ok(flowTaskService.update(taskDO));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowTaskService.delete(id);
        return R.ok();
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        flowTaskService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<FlowTaskDTO> getById(@PathVariable String id) {
        FlowTaskDO task = flowTaskService.findById(id);
        return R.ok(FlowTaskDTO.fromDO(task));
    }

    @GetMapping("/page")
    public R<PageBean<FlowTaskDTO>> getPage(FlowTaskQueryDTO queryDTO) {
        return R.ok(flowTaskService.findPage(queryDTO));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 启用 / 停用 / 日志开关
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/enable")
    public R<FlowTaskDO> enable(@PathVariable String id) {
        return R.ok(flowTaskService.enable(id));
    }

    @PutMapping("/{id}/disable")
    public R<FlowTaskDO> disable(@PathVariable String id) {
        return R.ok(flowTaskService.disable(id));
    }

    @PutMapping("/{id}/log-enabled")
    public R<FlowTaskDO> updateLogEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        return R.ok(flowTaskService.updateLogEnabled(id, enabled));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 发布 / 下线 / 回滚草稿 / 历史版本
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/publish")
    public R<FlowTaskDO> publish(@PathVariable String id) {
        return R.ok(flowTaskService.publish(id));
    }

    @PutMapping("/{id}/unpublish")
    public R<FlowTaskDO> unpublish(@PathVariable String id) {
        return R.ok(flowTaskService.unpublish(id));
    }

    @PutMapping("/{id}/rollback")
    public R<FlowTaskDO> rollback(@PathVariable String id) {
        return R.ok(flowTaskService.rollbackToPublished(id));
    }

    @PutMapping("/{id}/republish")
    public R<FlowTaskDO> republish(@PathVariable String id) {
        return R.ok(flowTaskService.republish(id));
    }

    @GetMapping("/{id}/versions")
    public R<List<FlowAssetVersionDTO>> listVersions(@PathVariable String id) {
        return R.ok(flowTaskService.listVersions(id));
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public R<FlowTaskDO> restoreVersion(@PathVariable String id, @PathVariable String versionId) {
        return R.ok(flowTaskService.restoreVersion(id, versionId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 手动触发 / 调试运行
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 手动立即触发一次（异步，不等待结果）
     */
    @PostMapping("/{id}/run")
    public R<Void> run(@PathVariable String id) {
        FlowTaskDO task = flowTaskService.findById(id);
        if (task == null) {
            return R.fail("任务不存在: " + id);
        }
        flowTaskScheduler.triggerManually(task);
        return R.ok();
    }

    /**
     * 调试运行（同步，返回 FlowTrace，与接口管理调试逻辑一致）
     */
    @PostMapping("/debug/run")
    public R<FlowTrace> debugRun(@RequestBody FlowDebugRequestDTO requestDTO) {
        try {
            FlowTrace trace = flowEngine.execute(requestDTO.getDslContent(),
                    Collections.emptyMap(), true, "DEBUG",
                    requestDTO.getSourceRef(), requestDTO.getSourceName());
            return R.ok(trace != null ? trace : new FlowTrace());
        } catch (Exception e) {
            log.error("Task debug run failed", e);
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
            errorTrace.setStepLogs(java.util.Collections.singletonList(errorLog));
            return R.ok(errorTrace);
        }
    }
}
