package org.yu.flow.engine.debug;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.dto.FlowDebugRequestDTO;

import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 交互式调试器 REST API 控制器。
 *
 * <p>提供调试会话的完整生命周期管理接口：</p>
 * <ul>
 *   <li>{@code POST   /debug/session/start}        — 创建会话，异步启动引擎执行</li>
 *   <li>{@code GET    /debug/session/{id}/status}   — 轮询会话状态（RUNNING/SUSPENDED/COMPLETED）</li>
 *   <li>{@code POST   /debug/session/{id}/resume}   — 恢复/单步跳过（附带可选变量修改）</li>
 *   <li>{@code DELETE /debug/session/{id}}           — 取消并销毁会话</li>
 *   <li>{@code GET    /debug/sessions}               — 列出所有活跃会话（管理用途）</li>
 * </ul>
 *
 * <h3>交互流程</h3>
 * <ol>
 *   <li>前端调用 {@code /start} 传入 DSL + 断点列表，后端异步启动引擎执行。</li>
 *   <li>引擎线程在遇到断点时自动挂起，前端通过 {@code /status} 轮询感知到 SUSPENDED 状态。</li>
 *   <li>前端展示当前变量快照，用户可查看/修改变量后点击 Resume(F8) 或 StepOver(F6)。</li>
 *   <li>前端调用 {@code /resume} 传入操作指令和可选变量修改，引擎线程被唤醒继续执行。</li>
 *   <li>循环 2-4 直到流程执行完毕或会话被取消。</li>
 * </ol>
 *
 * @author yu-flow
 * @since 1.0
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("flow-api/debug")
public class DebugController {

    @Resource
    private DebugSessionRegistry debugSessionRegistry;

    @Resource
    private FlowEngine flowEngine;

    @Resource(name = "flowAsyncExecutor")
    private Executor asyncExecutor;

    // ════════════════════════════ 会话管理 ════════════════════════════

    /**
     * 创建调试会话并异步启动引擎执行。
     *
     * @param requestDTO 包含 DSL 内容、模拟入参和断点列表
     * @return 新创建的会话状态（包含 sessionId）
     */
    @PostMapping("/session/start")
    public R<DebugStatusDTO> startSession(@RequestBody FlowDebugRequestDTO requestDTO) {
        try {
            // 校验必要参数
            if (requestDTO.getDslContent() == null || requestDTO.getDslContent().trim().isEmpty()) {
                return R.fail(400, "DSL 内容不能为空");
            }
            if (requestDTO.getBreakpoints() == null || requestDTO.getBreakpoints().isEmpty()) {
                return R.fail(400, "断点列表不能为空，交互式调试至少需要设置一个断点");
            }

            // 创建调试会话
            DebugSession session = debugSessionRegistry.createSession(requestDTO.getBreakpoints());

            // 构建引擎入参（复用 FlowApiController.debugRun 的参数组装逻辑）
            Map<String, Object> args = buildEngineArgs(requestDTO);

            // 异步启动引擎执行（在独立线程中运行，遇到断点会自动挂起）
            CompletableFuture.runAsync(() -> {
                try {
                    // 创建开启 Trace 的执行上下文，并关联调试会话
                    FlowTrace trace = flowEngine.executeWithDebugSession(
                            requestDTO.getDslContent(), args, session);
                    session.markCompleted(trace);
                } catch (Exception e) {
                    log.error("[DebugController] 调试执行异常: sessionId={}", session.getSessionId(), e);
                    FlowTrace errorTrace = buildErrorTrace(e);
                    session.markError(e.getMessage(), errorTrace);
                }
            }, asyncExecutor);

            log.info("[DebugController] 调试会话已启动: sessionId={}, 断点={}",
                    session.getSessionId(), requestDTO.getBreakpoints());

            return R.ok(DebugStatusDTO.from(session));

        } catch (IllegalStateException e) {
            return R.fail(429, e.getMessage());
        } catch (Exception e) {
            log.error("[DebugController] 启动调试会话失败", e);
            return R.fail(500, "启动调试会话失败: " + e.getMessage());
        }
    }

    /**
     * 轮询调试会话状态。
     *
     * <p>前端应以 1-2 秒间隔轮询此接口，检测引擎是否挂起在断点处。</p>
     *
     * @param sessionId 会话 ID
     * @return 当前会话状态（包含挂起节点信息和变量快照）
     */
    @GetMapping("/session/{sessionId}/status")
    public R<DebugStatusDTO> getSessionStatus(@PathVariable String sessionId) {
        DebugSession session = debugSessionRegistry.getSession(sessionId);
        if (session == null) {
            return R.fail(404, "调试会话不存在或已过期: " + sessionId);
        }
        return R.ok(DebugStatusDTO.from(session));
    }

    /**
     * 恢复执行或单步跳过。
     *
     * @param sessionId 会话 ID
     * @param resumeDTO 操作指令（resume/stepOver）和可选变量修改
     * @return 操作后的会话状态
     */
    @PostMapping("/session/{sessionId}/resume")
    public R<DebugStatusDTO> resumeSession(
            @PathVariable String sessionId,
            @RequestBody DebugResumeDTO resumeDTO) {

        DebugSession session = debugSessionRegistry.getSession(sessionId);
        if (session == null) {
            return R.fail(404, "调试会话不存在或已过期: " + sessionId);
        }

        if (session.getStatus() != DebugSession.Status.SUSPENDED) {
            return R.fail(400, "会话当前状态不是 SUSPENDED，无法执行恢复操作。当前状态: " + session.getStatus());
        }

        String action = resumeDTO.getAction();
        if ("stepOver".equals(action)) {
            session.stepOver(resumeDTO.getVariableUpdates());
        } else {
            // 默认为 resume
            session.resume(resumeDTO.getVariableUpdates());
        }

        // 短暂等待让引擎线程有机会状态变更，避免前端立即轮询时还看到旧状态
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return R.ok(DebugStatusDTO.from(session));
    }

    /**
     * 取消并销毁调试会话。
     *
     * @param sessionId 会话 ID
     */
    @DeleteMapping("/session/{sessionId}")
    public R<Void> cancelSession(@PathVariable String sessionId) {
        DebugSession session = debugSessionRegistry.getSession(sessionId);
        if (session == null) {
            return R.fail(404, "调试会话不存在或已过期: " + sessionId);
        }
        debugSessionRegistry.removeSession(sessionId);
        return R.ok();
    }

    /**
     * 列出所有活跃的调试会话（管理/监控用途）。
     */
    @GetMapping("/sessions")
    public R<List<Map<String, Object>>> listSessions() {
        return R.ok(debugSessionRegistry.listSessions());
    }

    // ════════════════════════════ 私有辅助方法 ════════════════════════════

    /**
     * 复用 FlowApiController.debugRun 的参数组装逻辑，构建引擎入参。
     */
    private Map<String, Object> buildEngineArgs(FlowDebugRequestDTO requestDTO) {
        Map<String, Object> args = new HashMap<>();
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("headers", requestDTO.getHeaders() != null ? requestDTO.getHeaders() : new HashMap<>());
        requestMap.put("params", requestDTO.getQueryParams() != null ? requestDTO.getQueryParams() : new HashMap<>());

        Object parsedBody = requestDTO.getBody();
        if (requestDTO.getBody() != null && !requestDTO.getBody().trim().isEmpty()) {
            try {
                parsedBody = cn.hutool.json.JSONUtil.parse(requestDTO.getBody());
            } catch (Exception e) {
                // 解析失败则保持为原始字符串
            }
        }
        requestMap.put("body", parsedBody);
        args.put("request", requestMap);
        return args;
    }

    /**
     * 构建异常时的 FlowTrace 报告。
     */
    private FlowTrace buildErrorTrace(Exception e) {
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
        return errorTrace;
    }
}
