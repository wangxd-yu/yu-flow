package org.yu.flow.module.mqtask.controller;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.evaluator.executor.MqTriggerStepExecutor;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.dto.FlowDebugRequestDTO;
import org.yu.flow.module.mqtask.consumer.MqConsumerManager;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskDTO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskLogDTO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskLogListDTO;
import org.yu.flow.module.mqtask.dto.FlowMqTaskSimulateRequestDTO;
import org.yu.flow.module.mqtask.query.FlowMqTaskLogQueryDTO;
import org.yu.flow.module.mqtask.query.FlowMqTaskQueryDTO;
import org.yu.flow.module.mqtask.service.FlowMqTaskLogService;
import org.yu.flow.module.mqtask.service.FlowMqTaskService;
import org.yu.flow.module.rbac.support.RequirePerm;

import jakarta.annotation.Resource;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MQ 任务管理 REST 控制器
 *
 * <p>权限沿用 MQ 连接管理域：{@code flow:mq:view} / {@code flow:mq:write}。</p>
 *
 * @author yu-flow
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("/flow-api/mq-task")
@RequirePerm({"flow:mq:view", "flow:mq:write"})
public class FlowMqTaskController {

    private static final DateTimeFormatter TRACE_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private FlowMqTaskService flowMqTaskService;

    @Resource
    private FlowMqTaskLogService flowMqTaskLogService;

    @Resource
    private MqConsumerManager mqConsumerManager;

    @Resource
    private FlowEngine flowEngine;

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    public R<FlowMqTaskDO> create(@RequestBody FlowMqTaskDO taskDO) {
        return R.ok(flowMqTaskService.save(taskDO));
    }

    @PutMapping("/{id}")
    public R<FlowMqTaskDO> update(@PathVariable String id, @RequestBody FlowMqTaskDO taskDO) {
        taskDO.setId(id);
        return R.ok(flowMqTaskService.update(taskDO));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowMqTaskService.delete(id);
        return R.ok();
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        flowMqTaskService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<FlowMqTaskDTO> getById(@PathVariable String id) {
        FlowMqTaskDO task = flowMqTaskService.findById(id);
        return R.ok(FlowMqTaskDTO.fromDO(task));
    }

    @GetMapping("/page")
    public R<PageBean<FlowMqTaskDTO>> getPage(FlowMqTaskQueryDTO queryDTO) {
        return R.ok(flowMqTaskService.findPage(queryDTO));
    }

    /** 消费订阅运行状态（管理页展示） */
    @GetMapping("/{id}/consumer-status")
    public R<Boolean> consumerStatus(@PathVariable String id) {
        return R.ok(mqConsumerManager.isRunning(id));
    }

    /** 存活订阅的任务 ID 集合（列表页批量展示消费状态） */
    @GetMapping("/consumer-status/running")
    public R<Set<String>> runningConsumers() {
        return R.ok(mqConsumerManager.runningTaskIds());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 启用 / 停用 / 日志开关
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/enable")
    public R<FlowMqTaskDO> enable(@PathVariable String id) {
        return R.ok(flowMqTaskService.enable(id));
    }

    @PutMapping("/{id}/disable")
    public R<FlowMqTaskDO> disable(@PathVariable String id) {
        return R.ok(flowMqTaskService.disable(id));
    }

    @PutMapping("/{id}/log-enabled")
    public R<FlowMqTaskDO> updateLogEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        return R.ok(flowMqTaskService.updateLogEnabled(id, enabled));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 发布 / 下线 / 回滚草稿
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/publish")
    public R<FlowMqTaskDO> publish(@PathVariable String id) {
        return R.ok(flowMqTaskService.publish(id));
    }

    @PutMapping("/{id}/unpublish")
    public R<FlowMqTaskDO> unpublish(@PathVariable String id) {
        return R.ok(flowMqTaskService.unpublish(id));
    }

    @PutMapping("/{id}/rollback")
    public R<FlowMqTaskDO> rollback(@PathVariable String id) {
        return R.ok(flowMqTaskService.rollbackToPublished(id));
    }

    @PutMapping("/{id}/republish")
    public R<FlowMqTaskDO> republish(@PathVariable String id) {
        return R.ok(flowMqTaskService.publish(id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 手动模拟 / 调试运行
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 手动模拟一条消息触发（异步，不等待结果）。仅已发布任务可触发，结果在任务日志中查看。
     */
    @PostMapping("/{id}/simulate")
    public R<Void> simulate(@PathVariable String id,
                            @RequestBody(required = false) FlowMqTaskSimulateRequestDTO requestDTO) {
        FlowMqTaskDO task = flowMqTaskService.findById(id);
        if (task == null) {
            return R.fail("MQ 任务不存在: " + id);
        }
        if (task.getPublishStatus() == null || task.getPublishStatus() != 1
                || StrUtil.isBlank(task.getPublishedSnapshot())) {
            return R.fail("任务未发布，无法模拟触发。请先发布，或使用「调试运行」验证草稿。");
        }
        String message = requestDTO != null ? requestDTO.getMessage() : null;
        mqConsumerManager.simulate(task, message);
        return R.ok();
    }

    /**
     * 调试运行（同步，返回 FlowTrace）。
     * <p>注入 {@code __mq*} 上下文变量，与真实消费一致，供 MqTrigger 节点写入 {@code $.mq.*}：
     * body 作为模拟消息体，headers 作为消息头，queryParams.topic 作为来源 topic。</p>
     */
    @PostMapping("/debug/run")
    public R<FlowTrace> debugRun(@RequestBody FlowDebugRequestDTO requestDTO) {
        try {
            Map<String, Object> args = new HashMap<>();
            if (StrUtil.isNotBlank(requestDTO.getSourceName())) {
                args.put("taskName", requestDTO.getSourceName());
            }
            String topic = requestDTO.getQueryParams() != null
                    ? requestDTO.getQueryParams().get("topic") : null;
            args.put(MqTriggerStepExecutor.ARG_TOPIC, StrUtil.nullToEmpty(topic));
            args.put(MqTriggerStepExecutor.ARG_MESSAGE, requestDTO.getBody());
            args.put(MqTriggerStepExecutor.ARG_HEADERS, requestDTO.getHeaders());
            args.put(MqTriggerStepExecutor.ARG_MESSAGE_ID, "debug-" + UUID.randomUUID());

            FlowTrace trace = flowEngine.execute(requestDTO.getDslContent(),
                    args, true, "DEBUG",
                    requestDTO.getSourceRef(), requestDTO.getSourceName());
            return R.ok(trace != null ? trace : new FlowTrace());
        } catch (Exception e) {
            log.error("MQ task debug run failed", e);
            ExecutionLog errorLog = new ExecutionLog()
                    .setId("err_global")
                    .setNodeId("__global__")
                    .setNodeName("Global Error")
                    .setNodeType("error")
                    .setStatus("error")
                    .setStartTime(LocalTime.now(ZONE_SH).format(TRACE_CLOCK))
                    .setError(e.getMessage());
            FlowTrace errorTrace = new FlowTrace();
            errorTrace.setStatus("error");
            errorTrace.setErrorMsg(e.getMessage());
            errorTrace.setStepLogs(java.util.Collections.singletonList(errorLog));
            return R.ok(errorTrace);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 执行日志
    // ─────────────────────────────────────────────────────────────────────────

    /** 日志分页列表（轻量，不含大字段） */
    @GetMapping("/log/page")
    public R<PageBean<FlowMqTaskLogListDTO>> getLogPage(FlowMqTaskLogQueryDTO queryDTO) {
        Page<FlowMqTaskLogListDTO> pageResult = flowMqTaskLogService.pageList(queryDTO);
        PageBean<FlowMqTaskLogListDTO> pageBean = new PageBean<>(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
        return R.ok(pageBean);
    }

    /** 日志完整详情（含 traceData，用于快照回放） */
    @GetMapping("/log/{id}")
    public R<FlowMqTaskLogDTO> getLogById(@PathVariable String id) {
        FlowMqTaskLogDTO logDTO = flowMqTaskLogService.getById(id);
        if (logDTO != null) {
            return R.ok(logDTO);
        }
        return R.fail("Log not found");
    }

    /** 清空某任务的全部日志 */
    @DeleteMapping("/log/clear/{taskId}")
    public R<Void> clearLog(@PathVariable String taskId) {
        flowMqTaskLogService.clearByTaskId(taskId);
        return R.ok();
    }
}
