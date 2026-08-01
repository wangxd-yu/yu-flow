package org.yu.flow.engine.evaluator;

import org.yu.flow.engine.model.*;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.auto.util.InputParamsUtil;
import org.yu.flow.engine.evaluator.executor.*;
import org.yu.flow.engine.model.*;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.*;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.module.api.service.FlowApiCrudService;
import org.yu.flow.module.serviceflow.service.FlowServiceFlowExecutionService;
import org.yu.flow.util.ThrowableUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.engine.cache.FlowDefinitionCache;
import org.yu.flow.engine.debug.DebugSession;
import org.yu.flow.engine.model.TraceSnapshotLimits;
import org.yu.flow.engine.model.step.ResponseResult;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 流程引擎 - 负责解析和执行流程定义
 */
@Slf4j
@Component
public class FlowEngine {
    private static final DateTimeFormatter TRACE_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    private final FlowParser parser = new FlowParser();
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private final Map<String, StepExecutor<? extends Step>> executors = new HashMap<>();

    /**
     * 全局受控的线程池（有界队列 + 最大线程数限制 + 调用方降级策略）
     * 替代原 Executors.newCachedThreadPool()，防止高并发下 OOM。
     */
    private ExecutorService executorService;

    /**
     * 单次流程执行允许的最大步骤数。0 表示不限制。
     * <p>在演示模式下由 DemoModeGuard 自动注入，防止死循环和过于复杂的流程编排。</p>
     */
    private int maxSteps = 0;

    private FlowServiceFlowExecutionService serviceFlowExecutionService;
    private FlowApiCrudService flowApiCrudService;
    private FlowApiExecutionService flowApiExecutionService;
    private FlowDefinitionCache definitionCache;
    private TraceSnapshotLimits snapshotLimits = TraceSnapshotLimits.DEFAULTS;
    private int forMaxInFlight = 64;
    private boolean enginePoolFromSpring;

    public FlowEngine() {
        initDefaultExecutor();
        registerExecutors();
        // 尝试从 Spring 容器获取 DemoModeGuard 配置（非 Spring 环境下忽略）
        try {
            DemoModeGuard guard =
                    cn.hutool.extra.spring.SpringUtil.getBean(DemoModeGuard.class);
            if (guard != null) {
                this.maxSteps = guard.getMaxSteps();
            }
        } catch (Exception ignored) {
            // 非 Spring 环境或容器未就绪，忽略
        }
    }

    private void initDefaultExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        this.executorService = new ThreadPoolExecutor(
                cpuCores * 2,             // corePoolSize
                cpuCores * 4,             // maximumPoolSize
                60L, TimeUnit.SECONDS,    // keepAliveTime
                new LinkedBlockingQueue<>(1024),  // 有界队列
                new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时，由调用方线程直接执行
        );
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("flowEngineExecutor")
    public void setExecutorService(ExecutorService executorService) {
        if (executorService != null) {
            if (this.executorService != null && this.executorService instanceof ThreadPoolExecutor
                    && !enginePoolFromSpring) {
                this.executorService.shutdown();
            }
            this.executorService = executorService;
            this.enginePoolFromSpring = true;
            registerExecutors(); // 重新使用新线程池注册
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setApiCallServices(
            @org.springframework.context.annotation.Lazy FlowServiceFlowExecutionService serviceFlowExecutionService,
            FlowApiCrudService flowApiCrudService,
            FlowApiExecutionService flowApiExecutionService) {
        this.serviceFlowExecutionService = serviceFlowExecutionService;
        this.flowApiCrudService = flowApiCrudService;
        this.flowApiExecutionService = flowApiExecutionService;
        wireApiExecutor();
    }

    private void wireApiExecutor() {
        StepExecutor<?> executor = executors.get("api");
        if (executor instanceof ApiServiceCallStepExecutor api) {
            api.setServiceFlowExecutionService(serviceFlowExecutionService);
            api.setFlowApiCrudService(flowApiCrudService);
            api.setFlowApiExecutionService(flowApiExecutionService);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDefinitionCache(FlowDefinitionCache definitionCache) {
        this.definitionCache = definitionCache;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setYuFlowProperties(YuFlowProperties properties) {
        if (properties != null && properties.getEngine() != null) {
            this.snapshotLimits = TraceSnapshotLimits.from(properties.getEngine());
            this.forMaxInFlight = properties.getEngine().getForMaxInFlight();
            // 若尚未注入 Spring 池，按配置重建本地默认池
            if (!enginePoolFromSpring) {
                applyEnginePoolConfig(properties.getEngine());
            }
            registerExecutors();
        }
    }

    private void applyEnginePoolConfig(YuFlowProperties.Engine engine) {
        int cpu = Runtime.getRuntime().availableProcessors();
        int core = engine.getPoolCoreSize() > 0 ? engine.getPoolCoreSize() : Math.max(2, cpu * 2);
        int max = engine.getPoolMaxSize() > 0 ? engine.getPoolMaxSize() : Math.max(core, cpu * 4);
        int queue = Math.max(16, engine.getPoolQueueCapacity());
        if (this.executorService != null && this.executorService instanceof ThreadPoolExecutor) {
            this.executorService.shutdown();
        }
        this.executorService = new ThreadPoolExecutor(
                core, max, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Spring 容器关闭时优雅停止线程池
     */
    @PreDestroy
    public void shutdown() {
        if (executorService != null && !enginePoolFromSpring) {
            executorService.shutdown();
        }
    }

    private void registerExecutors() {
        executors.put("api", new ApiServiceCallStepExecutor());
        executors.put("parallel", new ParallelStepExecutor(this, executorService));
        executors.put("if", new IfStepExecutor());
        executors.put("switch", new SwitchStepExecutor());
        executors.put("evaluate", new EvaluateStepExecutor());
        executors.put("request", new RequestStepExecutor());
        executors.put("httpRequest", new HttpRequestStepExecutor());
        executors.put("for", new ForStepExecutor(this, executorService, forMaxInFlight));
        executors.put("forEach", new ForEachStepExecutor(this));
        executors.put("collect", new CollectStepExecutor(this));
        executors.put("template", new TemplateStepExecutor());
        executors.put("response", new ResponseStepExecutor());
        executors.put("record", new RecordStepExecutor());
        executors.put("database", new DatabaseNodeExecutor(null, evaluator));
        executors.put("systemVar", new SystemVarStepExecutor());
        executors.put("systemMethod", new SystemMethodStepExecutor());
        executors.put("schedule", new ScheduleStepExecutor());
        executors.put("service", new ServiceStepExecutor());
        executors.put("mqTrigger", new MqTriggerStepExecutor());
        executors.put("delay", new DelayStepExecutor());
        executors.put("sendMail", new SendMailStepExecutor());
        executors.put("mqSend", new MqSendStepExecutor());
        executors.put("errorHandler", new ErrorHandlerStepExecutor());
        wireApiExecutor();
    }

    public void setSqlExecutorService(SqlExecutorService sqlExecutorService) {
        StepExecutor<?> executor = executors.get("database");
        if (executor instanceof DatabaseNodeExecutor) {
            ((DatabaseNodeExecutor) executor).setSqlExecutorService(sqlExecutorService);
        }
    }

    /**
     * 执行流程
     *
     * @param flowJson 流程定义JSON
     * @param args   输入参数
     * @return 执行结果
     */
    public <T> T execute(String flowJson, Map<String, Object> args) throws JsonProcessingException {
        return execute(flowJson, args, false);
    }

    /**
     * 执行流程（支持全链路追踪模式）
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String flowJson, Map<String, Object> args, boolean traceEnabled) throws JsonProcessingException {
        return execute(flowJson, args, traceEnabled, null, null, null, null);
    }

    /**
     * 执行流程（支持全链路追踪 + 调用来源标记，供三方日志使用）
     *
     * @param invokeSource 调用来源：API / TASK / DEBUG
     * @param sourceRef    来源关联 ID（apiId / taskId）
     * @param sourceName   来源名称（接口名 / 任务名）
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String flowJson, Map<String, Object> args, boolean traceEnabled,
                         String invokeSource, String sourceRef, String sourceName) throws JsonProcessingException {
        return execute(flowJson, args, traceEnabled, null, invokeSource, sourceRef, sourceName);
    }

    /**
     * 兼容旧调用：仅传 sourceRef
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String flowJson, Map<String, Object> args, boolean traceEnabled,
                         String invokeSource, String sourceRef) throws JsonProcessingException {
        return execute(flowJson, args, traceEnabled, null, invokeSource, sourceRef, null);
    }

    /**
     * 以交互式调试模式执行流程。
     *
     * <p>创建开启 Trace 的执行上下文，并将 {@link DebugSession} 关联到上下文中。
     * 当引擎在 {@code executeStep} 中检测到断点时，会通过 DebugSession 自动挂起引擎线程。</p>
     *
     * @param flowJson     流程定义 JSON
     * @param args         输入参数
     * @param debugSession 调试会话实例
     * @return FlowTrace 完整执行追踪报告
     */
    @SuppressWarnings("unchecked")
    public <T> T executeWithDebugSession(String flowJson, Map<String, Object> args,
                                          DebugSession debugSession) throws JsonProcessingException {
        return executeWithDebugSession(flowJson, args, debugSession, null, null);
    }

    /**
     * 交互式调试，并带上来源关联信息（便于三方日志定位具体接口/任务）
     */
    @SuppressWarnings("unchecked")
    public <T> T executeWithDebugSession(String flowJson, Map<String, Object> args,
                                          DebugSession debugSession,
                                          String sourceRef, String sourceName) throws JsonProcessingException {
        return execute(flowJson, args, true, debugSession, "DEBUG", sourceRef, sourceName);
    }

    /**
     * 执行流程（内部核心方法，支持全链路追踪和交互式调试）
     */
    @SuppressWarnings("unchecked")
    private <T> T execute(String flowJson, Map<String, Object> args,
                          boolean traceEnabled, DebugSession debugSession,
                          String invokeSource, String sourceRef, String sourceName) throws JsonProcessingException {
        FlowDefinition flowDefinition = resolveDefinition(flowJson);

        // 传递 traceEnabled 标记到上下文
        ExecutionContext context = new ExecutionContext(args, false, traceEnabled, snapshotLimits);

        // [防挂死] 将最大步骤数限制传递到执行上下文
        if (maxSteps > 0) {
            context.setMaxSteps(maxSteps);
        }

        // [交互式调试] 将调试会话关联到执行上下文；调试模式强制标记 DEBUG
        if (debugSession != null) {
            context.setDebugSession(debugSession);
            context.setInvokeSource("DEBUG");
            if (context.getFlowTrace() != null) {
                debugSession.bindLiveTrace(context.getFlowTrace());
            }
        } else if (invokeSource != null && !invokeSource.isBlank()) {
            context.setInvokeSource(invokeSource);
        }
        if (sourceRef != null && !sourceRef.isBlank()) {
            context.setSourceRef(sourceRef);
        }
        if (sourceName != null && !sourceName.isBlank()) {
            context.setSourceName(sourceName);
        }

        // DEBUG / REGRESSION：业务库写操作默认事务回滚（与 DB 模式调试一致）
        String effectiveSource = context.getInvokeSource();
        boolean rollbackDb = shouldRollbackDbWrites(effectiveSource, args);
        if (rollbackDb) {
            org.yu.flow.module.datasource.support.FlowDbRollbackScope.open();
            context.setVar("@__rollbackDb", true);
        }

        // 拓扑索引：缓存在 Definition 上，子流程复用
        Map<String, List<String>> parentMap = flowDefinition.getParentMap();
        Map<String, Set<String>> parallelSiblings = flowDefinition.getParallelSiblings();

        //解析flow入参
        if(flowDefinition.getArgs() != null && !flowDefinition.getArgs().isEmpty()) {
            flowDefinition.getArgs().forEach((key, value) -> {
                // 如果是字符串类型，进行特殊处理（变量引用、JSON解析等）
                if (value instanceof String) {
                    String strValue = StrUtil.trim(value.toString());

                    //判断 参数类型：常量；变量；对象
                    //1、变量 以 ${} 包裹
                    if (strValue.startsWith("${") && strValue.endsWith("}")) {
                        String varName = strValue.substring(2, strValue.length() - 1); // 提取 ${} 内部内容
                        context.setVar(key, InputParamsUtil.resolveParam(context.getVar(), varName));
                    }
                    //2、对象 Json格式
                    else if (JSONUtil.isTypeJSON(strValue)) {
                        context.setVar(key, ExpressionEvaluator.evaluate(strValue, context));
                    }
                    //3、其他情况属于字符串常量
                    else {
                        context.setVar(key, strValue);
                    }
                } else {
                    // 非字符串类型（数字、布尔等），直接设置原始值
                    context.setVar(key, value);
                }
            });
        }

        try {
            // 获取起点
            Step currentStep = null;
            if (flowDefinition.getStartStepId() != null) {
                currentStep = findStepById(flowDefinition.getStartStepId(), flowDefinition);
            } else if (!flowDefinition.getSteps().isEmpty()) {
                currentStep = flowDefinition.getSteps().get(0);
            }

            if (currentStep != null) {
                runFlow(currentStep, context, flowDefinition, parentMap, parallelSiblings);
            }

            // ================================================================
            // 【Scatter-Gather 异步完成等待】
            //
            // 若流程中包含 ForStep，ForStepExecutor.execute() 会返回 null（主干退出），
            // 同时在 context 中注册一个 LoopBarrier。主线程在此检测并等待。
            // 最后一条分支线程完成下游节点执行后，会调用
            // barrier.completionFuture.complete(null) 唤醒主线程。
            // ================================================================
            ForStepExecutor.LoopBarrier pendingBarrier = findPendingBarrier(context);
            if (pendingBarrier != null) {
                long waitTimeout = pendingBarrier.timeoutMs > 0 ? pendingBarrier.timeoutMs : 30_000L;
                log.info("[execute] 检测到 LoopBarrier [for={}, collect={}]，主线程等待 {}ms",
                        pendingBarrier.forStepId, pendingBarrier.collectStepId, waitTimeout);
                pendingBarrier.awaitCompletion(waitTimeout);
                log.info("[execute] LoopBarrier 完成，主线程继续读取结果");
            }

            // 【完全接管并自定义 HTTP 响应 (绕过全局拦截器)】
            // 场景：支持 Webhook 回调或标准的 RESTful API，必须确保响应不被包装层 {"success":true,"data":...} 污染。
            // 解决机制：若执行流最终在 Context 中留下的是 ResponseResult (即执行过 Response 节点)，
            // 不再包装为内部的 ExecutionResult，而是转换为 Spring Web 的标准 org.springframework.http.ResponseEntity。
            // 当由顶层 Controller (定义为 Object 返回值) 抛出 ResponseEntity 时，Spring MVC 的 HttpEntityMethodProcessor
            // 将自动接管处理，不再经过 @RestControllerAdvice (ResponseBodyAdvice) 的默认对象序列化切面，
            // 从而原生渲染自定义的 Status Code、Headers 字典以及纯净的纯文本/JSON Body 响应，完全尊重 HTTP 原始语义。
            if (context.getOutput() instanceof ResponseResult) {
                ResponseResult rr = (ResponseResult) context.getOutput();
                org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
                if (rr.getHeaders() != null) {
                    rr.getHeaders().forEach(httpHeaders::add);
                }
                int status = Integer.parseInt(String.valueOf(rr.getStatus()));
                if (traceEnabled) {
                    // 如果是 Debug 模式且包含 Response 节点，仍然优先返回日志快照
                    FlowTrace trace = context.getFlowTrace();
                    trace.setEndTime(System.currentTimeMillis());
                    trace.setTotalDurationMs(trace.getEndTime() - trace.getStartTime());
                    applyTraceStatusFromStepLogs(trace);
                    trace.setGlobalOutputs(rr);
                    return (T) trace;
                }
                return (T) new org.springframework.http.ResponseEntity<>(rr.getBody(), httpHeaders, org.springframework.http.HttpStatus.valueOf(status));
            }

            if (traceEnabled) {
                FlowTrace trace = context.getFlowTrace();
                trace.setEndTime(System.currentTimeMillis());
                trace.setTotalDurationMs(trace.getEndTime() - trace.getStartTime());
                applyTraceStatusFromStepLogs(trace);
                trace.setGlobalOutputs(context.getOutput());
                return (T) trace;
            }

            return (T) ExecutionResult.success(context.getOutput());
        } catch (FlowException e) {
            log.error("e: ", e);
            Object recovered = tryRecoverWithErrorHandler(e, context, flowDefinition, parentMap, parallelSiblings, traceEnabled);
            if (recovered != null) {
                return (T) recovered;
            }
            if (traceEnabled) {
                FlowTrace trace = context.getFlowTrace();
                trace.setEndTime(System.currentTimeMillis());
                trace.setTotalDurationMs(trace.getEndTime() - trace.getStartTime());
                trace.setStatus("error");
                trace.setErrorMsg(e.getMessage());
                return (T) trace;
            }
            // 处理业务异常
            ErrorDefinition errorDef = flowDefinition.getErrors() != null
                    ? flowDefinition.getErrors().get(e.getErrorCode()) : null;
            return (T) ExecutionResult.failure(
                    errorDef != null ? errorDef.getCode() : 500,
                    errorDef != null ? errorDef.getMessage() : e.getMessage()
            );
        } catch (Exception e) {
            log.error(cn.hutool.core.exceptions.ExceptionUtil.stacktraceToString(e));
            FlowException wrapped = new FlowException("SYSTEM_ERROR", "系统错误: " + e.getMessage(), null, null, e, FlowException.Severity.ERROR);
            Object recovered = tryRecoverWithErrorHandler(wrapped, context, flowDefinition, parentMap, parallelSiblings, traceEnabled);
            if (recovered != null) {
                return (T) recovered;
            }
            if (traceEnabled) {
                FlowTrace trace = context.getFlowTrace();
                trace.setEndTime(System.currentTimeMillis());
                trace.setTotalDurationMs(trace.getEndTime() - trace.getStartTime());
                trace.setStatus("error");
                trace.setErrorMsg(e.getMessage());
                return (T) trace;
            }
            // 处理系统异常
            return (T) ExecutionResult.failure(500, "系统错误: " + e.getMessage());
        } finally {
            if (rollbackDb) {
                org.yu.flow.module.datasource.support.FlowDbRollbackScope.close();
            }
        }
    }

    /**
     * DEBUG / REGRESSION 默认回滚业务库写；可用 args.__rollbackDb=false 显式关闭。
     */
    private static boolean shouldRollbackDbWrites(String invokeSource, Map<String, Object> args) {
        if (args != null && args.containsKey("__rollbackDb")) {
            Object v = args.get("__rollbackDb");
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            if (v != null) {
                return Boolean.parseBoolean(String.valueOf(v));
            }
        }
        if (invokeSource == null) {
            return false;
        }
        String src = invokeSource.trim().toUpperCase(Locale.ROOT);
        return "DEBUG".equals(src) || "REGRESSION".equals(src);
    }

    /**
     * 若流程中存在 errorHandler 节点且尚未进入错误处理，则跳转补偿链路。
     *
     * @return 恢复成功时的返回值；无法恢复时返回 null
     */
    @SuppressWarnings("unchecked")
    private Object tryRecoverWithErrorHandler(
            FlowException e,
            ExecutionContext context,
            FlowDefinition flowDefinition,
            Map<String, List<String>> parentMap,
            Map<String, Set<String>> parallelSiblings,
            boolean traceEnabled) {
        if (Boolean.TRUE.equals(context.getVariable("@__errorHandling"))) {
            return null;
        }
        Step errorHandler = findErrorHandlerStep(flowDefinition);
        if (errorHandler == null) {
            return null;
        }
        context.setVar("@__errorHandling", true);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("message", e.getMessage());
        err.put("code", e.getErrorCode());
        err.put("stepId", e.getStepId());
        context.setVar("error", err);
        log.info("[errorHandler] 捕获异常，跳转至 [{}]: {}", errorHandler.getId(), e.getMessage());
        try {
            runFlow(errorHandler, context, flowDefinition, parentMap, parallelSiblings);
            if (context.getOutput() instanceof ResponseResult) {
                ResponseResult rr = (ResponseResult) context.getOutput();
                org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
                if (rr.getHeaders() != null) {
                    rr.getHeaders().forEach(httpHeaders::add);
                }
                int status = Integer.parseInt(String.valueOf(rr.getStatus()));
                if (traceEnabled) {
                    FlowTrace trace = context.getFlowTrace();
                    trace.setEndTime(System.currentTimeMillis());
                    trace.setTotalDurationMs(trace.getEndTime() - trace.getStartTime());
                    applyTraceStatusFromStepLogs(trace);
                    if (trace.getStatus() == null) {
                        trace.setStatus("success");
                    }
                    trace.setGlobalOutputs(rr);
                    return trace;
                }
                return new org.springframework.http.ResponseEntity<>(rr.getBody(), httpHeaders,
                        org.springframework.http.HttpStatus.valueOf(status));
            }
            if (traceEnabled) {
                FlowTrace trace = context.getFlowTrace();
                trace.setEndTime(System.currentTimeMillis());
                trace.setTotalDurationMs(trace.getEndTime() - trace.getStartTime());
                applyTraceStatusFromStepLogs(trace);
                if (trace.getStatus() == null) {
                    trace.setStatus("success");
                }
                trace.setGlobalOutputs(context.getOutput());
                return trace;
            }
            return ExecutionResult.success(context.getOutput());
        } catch (Exception recoverEx) {
            log.error("[errorHandler] 错误处理链路仍失败", recoverEx);
            return null;
        }
    }

    private Step findErrorHandlerStep(FlowDefinition flowDefinition) {
        if (flowDefinition == null || flowDefinition.getSteps() == null) {
            return null;
        }
        for (Step step : flowDefinition.getSteps()) {
            if (step != null && NodeType.ERROR_HANDLER.equals(step.getType())) {
                return step;
            }
        }
        return null;
    }

    /**
     * 递归执行流程
     * @param parentMap 父节点映射表（用于多父节点汇聚检查）
     * @param parallelSiblings 并行兄弟节点映射（用于区分并行分支和条件分支）
     */
    private void runFlow(Step initialStep, ExecutionContext context, FlowDefinition flowDefinition, Map<String, List<String>> parentMap, Map<String, Set<String>> parallelSiblings) throws Exception {
        runFlow(initialStep, context, flowDefinition, parentMap, parallelSiblings, false);
    }

    private void runFlow(Step initialStep, ExecutionContext context, FlowDefinition flowDefinition, Map<String, List<String>> parentMap, Map<String, Set<String>> parallelSiblings, boolean stopAtJoinNodes) throws Exception {
        Step currentStep = initialStep;
        while (currentStep != null) {
            String nextPort = executeStep(currentStep, context, flowDefinition);

            context.markStepCompleted(currentStep.getId());
            Object nextTarget = currentStep.getNext().get(nextPort);

            if (nextTarget == null) {
                // 如果没有显式连接，且是普通输出，尝试顺序执行（兼容逻辑）
                // 理想情况下，Jointer 模式下应该全部通过 next 映射
                currentStep = null;
            } else if (nextTarget instanceof String) {
                // 单一分支
                String nextStepId = (String) nextTarget;
                if (stopAtJoinNodes && isJoinNode(nextStepId, parentMap)) {
                    currentStep = null;
                } else {
                    Step nextStep = findStepById(nextStepId, flowDefinition);
                    if (isJoinNode(nextStepId, parentMap) && !allParentsCompleted(nextStep, context, parentMap, parallelSiblings)) {
                        log.debug("汇聚节点 {} 的父节点尚未全部就绪，当前分支停止等待", nextStepId);
                        currentStep = null;
                    } else {
                        currentStep = nextStep;
                    }
                }
            } else if (nextTarget instanceof List) {
                // 并行分支 (List<String>)
                List<String> nextStepIds = (List<String>) nextTarget;
                List<CompletableFuture<ExecutionContext>> futures = new ArrayList<>();
                Set<String> convergenceNodeIds = new HashSet<>();

                for (String stepId : nextStepIds) {
                    if (isJoinNode(stepId, parentMap)) {
                        convergenceNodeIds.add(stepId);
                        log.debug("节点 {} 是多父汇聚节点，跳过分支直启，等待父节点完成后统一执行", stepId);
                        continue;
                    }
                    Step branchStartStep = findStepById(stepId, flowDefinition);
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        // 采用浅拷贝（类 Copy-On-Write），避免并行大范围数据时深克隆导致的 OOM
                        ExecutionContext branchContext = context.copy(false);
                        try {
                            runFlow(branchStartStep, branchContext, flowDefinition, parentMap, parallelSiblings, true);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                        return branchContext;
                    }, executorService));
                }

                // 等待所有分支完成并合并结果
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<ExecutionContext> future : futures) {
                    ExecutionContext branchContext = future.get();
                    // 合并变量回主上下文 (注意线程安全和覆盖策略)
                    // 这里简单合并，后完成的覆盖先完成的
                    synchronized (context) {
                         branchContext.getVar().forEach((k, v) -> {
                            if (!context.hasVariable(k) || !Objects.equals(context.getVariable(k), v)) {
                                context.setVar(k, v);
                            }
                        });
                        // 如果分支有输出，也更新主输出 (可能需要更复杂的合并策略)
                        if (branchContext.getOutput() != null) {
                            context.setOutput(branchContext.getOutput());
                        }
                        // 【关键修复】合并分支的已完成节点状态
                        branchContext.mergeCompletedStepsTo(context);
                    }
                }
                // 并行执行后，检查是否有汇聚节点可以执行
                for (String stepId : nextStepIds) {
                    if (isJoinNode(stepId, parentMap)) {
                        continue;
                    }
                    Step branchStep = findStepById(stepId, flowDefinition);
                    collectJoinNodeIds(branchStep, convergenceNodeIds, flowDefinition, parentMap, new HashSet<>());
                }

                // 检查这些节点中是否有所有父节点都已完成的
                currentStep = null; // Reset currentStep for convergence check
                for (String nodeId : convergenceNodeIds) {
                    Step convergenceNode = findStepById(nodeId, flowDefinition);
                    if (!context.isStepCompleted(convergenceNode.getId()) && allParentsCompleted(convergenceNode, context, parentMap, parallelSiblings)) {
                        log.info("汇聚节点 {} 的所有父节点已完成，继续执行", nodeId);
                        currentStep = convergenceNode;
                        break; // 找到第一个可执行的汇聚节点就执行
                    }
                }

                // 如果没有找到可执行的汇聚节点，结束当前流程
                if (currentStep == null) {
                    // 没有可执行的汇聚节点，结束
                }
                // 否则循环继续，执行汇聚节点
            } else {
                 throw new FlowException("INVALID_NEXT_TARGET", "无效的下一跳目标类型: " + nextTarget.getClass());
            }
        }
    }

    /**
     * 执行单个步骤
     * @return 触发的输出端口名称 (Jointer)
     */
    private String executeStep(Step step, ExecutionContext context, FlowDefinition flowDefinition) {
        log.debug("执行步骤 {} [{}]", step.getId(), step.getType());
        log.debug("步骤前变量: {}", context.getVar());

        // ═══════════════ 调试断点检测 (Debug Hook) ═══════════════
        // 在节点业务逻辑执行前检查断点，若命中则挂起引擎线程等待前端指令。
        DebugSession debugSession = context.getDebugSession();
        if (debugSession != null && debugSession.getStatus() != DebugSession.Status.CANCELLED) {
            // 传入可序列化变量快照（剥离 LoopBarrier），供前端查看和修改
            Map<String, Object> varSnapshot = context.snapshotVarsForTrace();
            Map<String, Object> variableUpdates = debugSession.checkAndSuspend(
                    step.getId(), step.getName(), varSnapshot);

            // 若前端在调试面板中修改了变量，将修改注入回执行上下文
            if (variableUpdates != null && !variableUpdates.isEmpty()) {
                log.info("[调试模式] 注入前端修改的变量: {}", variableUpdates.keySet());
                variableUpdates.forEach(context::setVar);
            }

            // 若会话已被取消，以异常方式终止引擎执行
            if (debugSession.getStatus() == DebugSession.Status.CANCELLED) {
                throw new FlowException("DEBUG_CANCELLED", "调试会话已被取消");
            }
        }

        // Trace 开始
        ExecutionLog traceLog = null;
        long startTime = System.currentTimeMillis();
        if (context.isTraceEnabled()) {
            traceLog = new ExecutionLog()
                .setId(UUID.randomUUID().toString())
                .setNodeId(step.getId())
                .setNodeName(step.getName())
                .setNodeType(step.getType())
                .setStartTime(Instant.ofEpochMilli(startTime).atZone(ZONE_SH).toLocalTime().format(TRACE_CLOCK))
                .setStatus("running")
                // 快照：节点执行前的上下文变量（可 JSON 序列化）
                .setInputs(context.snapshotVarsForTrace());
            context.addExecutionLog(traceLog);
        }

        // 默认端口为 "out"；若 executor 返回 null，则视为"主动终止信号"
        String nextPort = PortNames.OUT;
        try {
            // [防挂死] 步骤计数器递增并检查是否超出限制（由 ExecutionContext.maxSteps 控制）
            context.incrementAndCheckStepLimit();

            StepExecutor executor = executors.get(step.getType());
            if (executor == null) {
                throw new FlowException("UNKNOWN_STEP_TYPE", "未知的步骤类型: " + step.getType());
            }
            String result = executor.execute(step, context, flowDefinition);
            // result 为 null：执行器主动返回 null，表示当前线程应终止调度循环
            nextPort = result;

            if (traceLog != null) {
                // HttpRequest 等节点走 fail 口时并不抛异常，但业务上应记为 error
                if (PortNames.FAIL.equals(nextPort)) {
                    traceLog.setStatus("error");
                    traceLog.setError(resolveFailPortError(step, context));
                } else {
                    traceLog.setStatus("success");
                }
                // 快照：节点执行后的上下文变量（可 JSON 序列化，剔除 Barrier）
                traceLog.setOutputs(context.snapshotVarsForTrace());
            }
        } catch (RetryStepException e) {
            Step retryStep = findStepById(e.getStepId(), flowDefinition);
            return executeStep(retryStep, context, flowDefinition);
        } catch (FlowException e) {
            log.error(ThrowableUtil.getStackTrace(e));
            if (traceLog != null) {
                traceLog.setStatus("error");
                traceLog.setError(e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.error(cn.hutool.core.exceptions.ExceptionUtil.stacktraceToString(e));
            if (traceLog != null) {
                traceLog.setStatus("error");
                traceLog.setError(e.getMessage());
            }
            throw e;
        } finally {
            if (traceLog != null) {
                traceLog.setDuration(System.currentTimeMillis() - startTime);
            }
        }

        log.debug("步骤后变量: {}", context.getVar());
        return nextPort;
    }

    /**
     * 从节点输出中提取 fail 口错误信息（如 HttpRequest 的 error / status）。
     */
    @SuppressWarnings("unchecked")
    private String resolveFailPortError(Step step, ExecutionContext context) {
        if (step == null || context == null || context.getVar() == null) {
            return "节点走 fail 分支";
        }
        Object nodeOut = context.getVar().get(step.getId());
        if (nodeOut instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) nodeOut;
            Object err = map.get("error");
            if (err != null && !String.valueOf(err).isBlank()) {
                return String.valueOf(err);
            }
            Object status = map.get("status");
            if (status != null) {
                return "HTTP " + status;
            }
        }
        return "节点走 fail 分支";
    }

    /** 若任一步骤日志为 error，则整条 Trace 记为 error */
    private void applyTraceStatusFromStepLogs(FlowTrace trace) {
        if (trace == null) {
            return;
        }
        List<ExecutionLog> logs = trace.getStepLogs();
        if (logs == null || logs.isEmpty()) {
            if (trace.getStatus() == null) {
                trace.setStatus("success");
            }
            return;
        }
        for (ExecutionLog stepLog : logs) {
            if (stepLog != null && "error".equalsIgnoreCase(stepLog.getStatus())) {
                trace.setStatus("error");
                if (trace.getErrorMsg() == null || trace.getErrorMsg().isBlank()) {
                    trace.setErrorMsg(stepLog.getError() != null
                            ? stepLog.getError()
                            : ("节点失败: " + stepLog.getNodeId()));
                }
                return;
            }
        }
        if (trace.getStatus() == null || "success".equalsIgnoreCase(trace.getStatus())) {
            trace.setStatus("success");
        }
    }

    /**
     * 供内部 Executor 使用的执行方法
     */
    public void executeStepDirectly(Step step, ExecutionContext context, FlowDefinition flow) {
        executeStep(step, context, flow);
    }

    /**
     * 内部执行器调用的子流程执行方法
     * 从指定步骤开始执行子流程，stopAtJoinNodes=true（遇到多父汇聚节点停止）
     */
    public void runSubFlow(String startStepId, ExecutionContext context, FlowDefinition flow) throws Exception {
        Step startStep = findStepById(startStepId, flow);
        runFlow(startStep, context, flow, flow.getParentMap(), flow.getParallelSiblings(), true);
    }

    /**
     * 供 ForStepExecutor 分支任务和 CollectStepExecutor 线程接力使用。
     *
     * <p><b>与 runSubFlow 的核心区别：stopAtJoinNodes = false</b></p>
     * <p>分支任务需要一路执行到 CollectStep（图拓扑上的多父汇聚节点），
     * 因此不能在 CollectStep 之前被引擎截停。CollectStepExecutor 内部通过
     * return null 自行决定是否终止当前分支的 runFlow 循环。</p>
     *
     * <p>同时，最后一条线程在 CollectStepExecutor 的 performHandoff 方法中，
     * 也会调用此方法在主上下文上运行 CollectStep 下游节点（"线程接力"）。</p>
     *
     * @param startStepId 从该步骤节点开始执行
     * @param context     执行上下文（可以是 branchContext 或 mainContext）
     * @param flow        完整流程定义
     */
    public void runBranchFlow(String startStepId, ExecutionContext context, FlowDefinition flow) throws Exception {
        Step startStep = findStepById(startStepId, flow);
        // stopAtJoinNodes = false：分支可以直接到达 CollectStep（多父汇聚节点）
        runFlow(startStep, context, flow, flow.getParentMap(), flow.getParallelSiblings(), false);
    }

    /**
     * 公开的步骤查找方法（供 ForStepExecutor 空数组旁路使用）
     *
     * @return 找到的 Step，未找到返回 null（不抛异常）
     */
    public Step findStep(String stepId, FlowDefinition flow) {
        try {
            return findStepById(stepId, flow);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 DSL：优先走编译缓存，未注入缓存时本地 parse + 挂载拓扑。 */
    private FlowDefinition resolveDefinition(String flowJson) throws JsonProcessingException {
        if (definitionCache != null) {
            return definitionCache.getOrParse(flowJson);
        }
        FlowDefinition def = parser.parse(flowJson);
        def.ensureTopologyIndexes();
        return def;
    }

    private Step findStepById(String stepId, FlowDefinition flowDefinition) throws FlowException {
        Step step = flowDefinition.getStep(stepId);
        if (step != null) {
            return step;
        }
        throw new FlowException("STEP_NOT_FOUND", "找不到步骤: " + stepId);
    }

    /**
     * 检查节点的所有父节点是否都已完成
     * @param step 待检查的节点
     * @param context 执行上下文（包含已完成节点信息）
     * @param parentMap 父节点映射表
     * @param parallelSiblings 并行兄弟节点映射
     * @return true 如果所有父节点都已完成（或没有父节点）
     */
    private boolean allParentsCompleted(Step step, ExecutionContext context, Map<String, List<String>> parentMap, Map<String, Set<String>> parallelSiblings) {
        List<String> parents = parentMap.get(step.getId());

        // 没有父节点（起始节点）或父节点列表为空
        if (parents == null || parents.isEmpty()) {
            return true;
        }

        // 只有1个父节点，直接检查它是否完成
        if (parents.size() == 1) {
            return context.isStepCompleted(parents.get(0));
        }

        // 多个父节点：带 inputs 的节点是数据依赖汇聚，必须等待所有输入来源完成。
        if (step.getInputs() != null && !step.getInputs().isEmpty()) {
            boolean allCompleted = parents.stream().allMatch(context::isStepCompleted);
            if (!allCompleted) {
                long completedCount = parents.stream().filter(context::isStepCompleted).count();
                log.debug("数据汇聚节点 {} 的父节点 {} 中有 {}/{} 个已完成，等待其他输入节点",
                    step.getId(), parents, completedCount, parents.size());
            }
            return allCompleted;
        }

        // 多个父节点：需要区分并行分支汇聚和条件分支汇聚
        // 检查这些父节点中是否有从同一个并行分支出来的（即它们是兄弟节点）
        Set<String> parallelParents = new HashSet<>();
        for (String parentId : parents) {
            Set<String> siblings = parallelSiblings.get(parentId);
            if (siblings != null) {
                // 这个父节点是并行分支的一部分
                parallelParents.add(parentId);
            }
        }

        // 如果所有父节点都是并行分支（即它们都有兄弟节点），且它们互为兄弟
        // 那么需要等待所有父节点完成
        if (parallelParents.size() == parents.size()) {
            // 检查这些父节点是否都在同一个并行分支集合中
            Set<String> firstParentSiblings = parallelSiblings.get(parents.get(0));
            boolean allFromSameParallelBranch = parents.stream().allMatch(p -> {
                Set<String> pSiblings = parallelSiblings.get(p);
                return pSiblings != null && pSiblings.equals(firstParentSiblings);
            });

            if (allFromSameParallelBranch) {
                // 所有父节点都来自同一个并行分支，需要全部完成
                boolean allCompleted = parents.stream().allMatch(context::isStepCompleted);
                if (!allCompleted) {
                    long completedCount = parents.stream().filter(context::isStepCompleted).count();
                    log.debug("并行汇聚节点 {} 的父节点 {} 中有 {}/{} 个已完成，等待其他并行分支",
                        step.getId(), parents, completedCount, parents.size());
                }
                return allCompleted;
            }
        }

        // 否则，是条件分支汇聚，只需要至少一个父节点完成
        boolean anyCompleted = parents.stream().anyMatch(context::isStepCompleted);
        if (anyCompleted) {
            return true;
        }

        log.debug("节点 {} 的所有父节点 {} 都未完成", step.getId(), parents);
        return false;
    }

    private boolean isJoinNode(String stepId, Map<String, List<String>> parentMap) {
        List<String> parents = parentMap.get(stepId);
        return parents != null && parents.size() > 1;
    }

    private void collectJoinNodeIds(Step step, Set<String> nodeIds, FlowDefinition flowDefinition, Map<String, List<String>> parentMap, Set<String> visited) {
        if (step == null || visited.contains(step.getId())) {
            return;
        }
        visited.add(step.getId());

        if (step.getNext() == null || step.getNext().isEmpty()) {
            return;
        }

        for (Object nextTarget : step.getNext().values()) {
            if (nextTarget instanceof String) {
                String nextId = (String) nextTarget;
                if (isJoinNode(nextId, parentMap)) {
                    nodeIds.add(nextId);
                } else {
                    collectJoinNodeIds(findStepById(nextId, flowDefinition), nodeIds, flowDefinition, parentMap, visited);
                }
            } else if (nextTarget instanceof List) {
                for (String nextId : (List<String>) nextTarget) {
                    if (isJoinNode(nextId, parentMap)) {
                        nodeIds.add(nextId);
                    } else {
                        collectJoinNodeIds(findStepById(nextId, flowDefinition), nodeIds, flowDefinition, parentMap, visited);
                    }
                }
            }
        }
    }

    /**
     * 扫描执行上下文，查找 ForStep 注册的 LoopBarrier
     *
     * <p>LoopBarrier 的 key 格式固定为 {@code ContextKeys.BARRIER_PREFIX + collectStepId}，
     * 由 ForStepExecutor 在 execute() 开始时注册。</p>
     *
     * @return 找到的 LoopBarrier，不存在则返回 null
     */
    private ForStepExecutor.LoopBarrier findPendingBarrier(ExecutionContext context) {
        Map<String, Object> vars = context.getVar();
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            if (entry.getKey().startsWith(ForStepExecutor.BARRIER_KEY_PREFIX)
                    && entry.getValue() instanceof ForStepExecutor.LoopBarrier) {
                ForStepExecutor.LoopBarrier barrier = (ForStepExecutor.LoopBarrier) entry.getValue();
                // 只等待未完成的 barrier（空数组旁路时 barrier 已立即完成，但仍需 get() 一次确认）
                return barrier;
            }
        }
        return null;
    }
}

