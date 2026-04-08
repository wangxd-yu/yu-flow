package org.yu.flow.engine.evaluator;

import org.yu.flow.engine.model.*;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.util.InputParamsUtil;
import org.yu.flow.engine.evaluator.executor.*;
import org.yu.flow.engine.model.*;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.*;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.util.ThrowableUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.engine.evaluator.executor.*;
import org.yu.flow.engine.model.step.ResponseResult;

import javax.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 流程引擎 - 负责解析和执行流程定义
 */
@Slf4j
@Component
public class FlowEngine {
    private final FlowParser parser = new FlowParser();
    private final Map<String, Object> serviceMap = new HashMap<>();
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private final Map<String, StepExecutor<? extends Step>> executors = new HashMap<>();

    /**
     * 全局受控的线程池（有界队列 + 最大线程数限制 + 调用方降级策略）
     * 替代原 Executors.newCachedThreadPool()，防止高并发下 OOM。
     */
    private ExecutorService executorService;

    public FlowEngine() {
        initDefaultExecutor();
        registerExecutors();
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
    public void setExecutorService(ExecutorService executorService) {
        if (executorService != null) {
            if (this.executorService != null && this.executorService instanceof ThreadPoolExecutor) {
                this.executorService.shutdown();
            }
            this.executorService = executorService;
            registerExecutors(); // 重新使用新线程池注册
        }
    }

    /**
     * Spring 容器关闭时优雅停止线程池
     */
    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void registerExecutors() {
        executors.put("call", new ServiceCallStepExecutor(serviceMap, evaluator));
        executors.put("serviceCall", new ServiceCallStepExecutor(serviceMap, evaluator));
        executors.put("api", new ApiServiceCallStepExecutor());
        executors.put("parallel", new ParallelStepExecutor(this, executorService));
        executors.put("condition", new ConditionStepExecutor(evaluator));
        executors.put("if", new IfStepExecutor());
        executors.put("switch", new SwitchStepExecutor());
        executors.put("set", new SetVarStepExecutor(evaluator));
        executors.put("return", new ReturnStepExecutor(evaluator));
        executors.put("evaluate", new EvaluateStepExecutor());
        executors.put("start", new StartStepExecutor());
        executors.put("end", new EndStepExecutor());
        executors.put("request", new RequestStepExecutor());
        executors.put("httpRequest", new HttpRequestStepExecutor());
        executors.put("for", new ForStepExecutor(this, executorService));
        executors.put("collect", new CollectStepExecutor(this));
        executors.put("template", new TemplateStepExecutor());
        executors.put("response", new ResponseStepExecutor());
        executors.put("record", new RecordStepExecutor());
        executors.put("database", new DatabaseNodeExecutor(null, evaluator));
        executors.put("systemVar", new SystemVarStepExecutor());
        executors.put("systemMethod", new SystemMethodStepExecutor());
    }

    public void setSqlExecutorService(SqlExecutorService sqlExecutorService) {
        StepExecutor<?> executor = executors.get("database");
        if (executor instanceof DatabaseNodeExecutor) {
            ((DatabaseNodeExecutor) executor).setSqlExecutorService(sqlExecutorService);
        }
    }

    /**
     * 注册服务到引擎
     *
     * @param name    服务名称
     * @param service 服务实例
     * @return 当前引擎实例（支持链式调用）
     */
    public FlowEngine registerService(String name, Object service) {
        serviceMap.put(name, service);
        return this;
    }

    /**
     * 执行流程
     *
     * @param flowJson 流程定义JSON
     * @param args   输入参数
     * @return 执行结果
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String flowJson, Map<String, Object> args) throws JsonProcessingException {
        FlowDefinition flowDefinition = parser.parse(flowJson); // 解析时保存流程定义

        ExecutionContext context = new ExecutionContext(args);

        // 构建父节点映射（用于多父节点汇聚）
        Map<String, List<String>> parentMap = buildParentMapping(flowDefinition);

        // 构建并行兄弟节点映射（用于区分并行分支和条件分支）
        Map<String, Set<String>> parallelSiblings = buildParallelSiblingsMapping(flowDefinition);

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
                return (T) new org.springframework.http.ResponseEntity<>(rr.getBody(), httpHeaders, org.springframework.http.HttpStatus.valueOf(status));
            }

            return (T) ExecutionResult.success(context.getOutput());
        } catch (FlowException e) {
            log.error("e: ", e);
            // 处理业务异常
            ErrorDefinition errorDef = flowDefinition.getErrors().get(e.getErrorCode());
            return (T) ExecutionResult.failure(
                    errorDef != null ? errorDef.getCode() : 500,
                    errorDef != null ? errorDef.getMessage() : e.getMessage()
            );
        } catch (Exception e) {
            log.error(cn.hutool.core.exceptions.ExceptionUtil.stacktraceToString(e));
            // 处理系统异常
            return (T) ExecutionResult.failure(500, "系统错误: " + e.getMessage());
        }
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
                    currentStep = findStepById(nextStepId, flowDefinition);
                }
            } else if (nextTarget instanceof List) {
                // 并行分支 (List<String>)
                List<String> nextStepIds = (List<String>) nextTarget;
                List<CompletableFuture<ExecutionContext>> futures = new ArrayList<>();

                for (String stepId : nextStepIds) {
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
                Set<String> convergenceNodeIds = new HashSet<>();
                for (String stepId : nextStepIds) {
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
        log.info("执行步骤 {} [{}]", step.getId(), step.getType());
        log.info("步骤前变量: {}", context.getVar());
        // 默认端口为 "out"；若 executor 返回 null，则视为"主动终止信号"（如 ForStep/CollectStep 的线程接力）
        String nextPort = PortNames.OUT;
        try {
            StepExecutor executor = executors.get(step.getType());
            if (executor == null) {
                throw new FlowException("UNKNOWN_STEP_TYPE", "未知的步骤类型: " + step.getType());
            }
            String result = executor.execute(step, context, flowDefinition);
            // result 为 null：执行器主动返回 null，表示当前线程应终止调度循环
            // （ForStep：发射完毕，主线程使命结束；CollectStep：非最后线程，静默退出）
            nextPort = result;  // 可以是 null，runFlow 会正确处理
        } catch (RetryStepException e) {
            Step retryStep = findStepById(e.getStepId(), flowDefinition);
            return executeStep(retryStep, context, flowDefinition);
        } catch (FlowException e) {
            log.error(ThrowableUtil.getStackTrace(e));
            throw e;
        }
        log.info("步骤后变量: {}", context.getVar());
        return nextPort;
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
        Map<String, List<String>> parentMap = buildParentMapping(flow);
        Map<String, Set<String>> parallelSiblings = buildParallelSiblingsMapping(flow);
        runFlow(startStep, context, flow, parentMap, parallelSiblings, true);
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
        Map<String, List<String>> parentMap = buildParentMapping(flow);
        Map<String, Set<String>> parallelSiblings = buildParallelSiblingsMapping(flow);
        // stopAtJoinNodes = false：分支可以直接到达 CollectStep（多父汇聚节点）
        runFlow(startStep, context, flow, parentMap, parallelSiblings, false);
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

    private Step findStepById(String stepId, FlowDefinition flowDefinition) throws FlowException {
        Step step = flowDefinition.getStep(stepId);
        if (step != null) {
            return step;
        }
        throw new FlowException("STEP_NOT_FOUND", "找不到步骤: " + stepId);
    }

    /**
     * 查找服务方法（基于参数值推导类型）
     */
    private Method findMethod(Object service, String methodName, List<String> argExpressions) throws FlowException {
        try {
            Class<?> serviceClass = service.getClass();

            // 如果没有参数，直接查找无参方法
            if (argExpressions == null || argExpressions.isEmpty()) {
                return serviceClass.getMethod(methodName);
            }

            // 查找所有同名方法
            Method[] methods = serviceClass.getMethods();
            List<Method> candidateMethods = Arrays.stream(methods)
                    .filter(m -> m.getName().equals(methodName))
                    .collect(Collectors.toList());

            if (candidateMethods.isEmpty()) {
                throw new FlowException("METHOD_NOT_FOUND",
                        String.format("在服务 %s 中找不到方法 %s",
                                serviceClass.getSimpleName(), methodName));
            }

            // 如果只有一个匹配方法，直接返回
            if (candidateMethods.size() == 1) {
                return candidateMethods.get(0);
            }

            // 尝试根据参数数量过滤
            List<Method> byParamCount = candidateMethods.stream()
                    .filter(m -> m.getParameterCount() == argExpressions.size())
                    .collect(Collectors.toList());

            if (byParamCount.size() == 1) {
                return byParamCount.get(0);
            }

            // 如果仍有多个候选方法，返回第一个（可能需要更复杂的类型匹配）
            return candidateMethods.get(0);

        } catch (NoSuchMethodException e) {
            throw new FlowException("METHOD_NOT_FOUND",
                    String.format("在服务 %s 中找不到方法 %s",
                            service.getClass().getSimpleName(), methodName), e);
        }
    }

    /**
     * 查找服务方法（支持参数类型匹配）
     *
     * @param service    服务实例
     * @param methodName 方法名称
     * @param argTypes   参数类型列表（可为null）
     * @return 匹配的Method对象
     * @throws FlowException 如果方法找不到或存在歧义
     */
    private Method findMethod(Object service, String methodName, Class<?>[] argTypes) throws FlowException {
        try {
            if (argTypes != null) {
                // 精确匹配参数类型
                return service.getClass().getMethod(methodName, argTypes);
            } else {
                // 没有参数类型信息时使用简单匹配
                return findMethodByNameOnly(service, methodName);
            }
        } catch (NoSuchMethodException e) {
            throw new FlowException("METHOD_NOT_FOUND",
                    String.format("在服务 %s 中找不到方法 %s(%s)",
                            service.getClass().getSimpleName(),
                            methodName,
                            argTypes != null ? Arrays.toString(argTypes) : ""));
        }
    }

    /**
     * 仅根据方法名查找方法（处理重载情况）
     */
    private Method findMethodByNameOnly(Object service, String methodName) throws FlowException {
        Class<?> serviceClass = service.getClass();
        Method[] methods = serviceClass.getMethods();

        List<Method> matchedMethods = Arrays.stream(methods)
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());

        if (matchedMethods.isEmpty()) {
            throw new FlowException("METHOD_NOT_FOUND",
                    String.format("在服务 %s 中找不到方法 %s", serviceClass.getSimpleName(), methodName));
        }

        if (matchedMethods.size() == 1) {
            return matchedMethods.get(0);
        }

        // 优先选择无参数方法
        Optional<Method> noArgMethod = matchedMethods.stream()
                .filter(m -> m.getParameterCount() == 0)
                .findFirst();

        if (noArgMethod.isPresent()) {
            return noArgMethod.get();
        }

        // 无法解决歧义时抛出异常
        throw new FlowException("AMBIGUOUS_METHOD",
                String.format("在服务 %s 中找到多个匹配方法 %s，请指定参数类型",
                        serviceClass.getSimpleName(), methodName));
    }

    /**
     * 判断是否是内部参数
     * @return 是否是内部参数
     */
    private Map<String, List<String>> buildParentMapping(FlowDefinition flowDefinition) {
        Map<String, List<String>> parentMap = new HashMap<>();

        // 遍历所有步骤，分析 next 映射，构建反向引用
        for (Step step : flowDefinition.getSteps()) {
            String parentId = step.getId();

            // 遍历该步骤的所有出口
            if (step.getNext() != null) {
                for (Object nextTarget : step.getNext().values()) {
                    if (nextTarget instanceof String) {
                        // 单个子节点
                        String childId = (String) nextTarget;
                        parentMap.computeIfAbsent(childId, k -> new ArrayList<>()).add(parentId);
                    } else if (nextTarget instanceof List) {
                        // 并行子节点列表
                        List<String> childIds = (List<String>) nextTarget;
                        for (String childId : childIds) {
                            parentMap.computeIfAbsent(childId, k -> new ArrayList<>()).add(parentId);
                        }
                    }
                }
            }
        }

        log.info("父节点映射: {}", parentMap);
        return parentMap;
    }

    /**
     * 构建并行兄弟节点映射
     * Key: 节点ID, Value: 与该节点从同一个并行分支出来的兄弟节点集合
     */
    private Map<String, Set<String>> buildParallelSiblingsMapping(FlowDefinition flowDefinition) {
        Map<String, Set<String>> siblingsMap = new HashMap<>();

        // 遍历所有步骤，查找并行分支（next值为List的情况）
        for (Step step : flowDefinition.getSteps()) {
            if (step.getNext() != null) {
                for (Object nextTarget : step.getNext().values()) {
                    if (nextTarget instanceof List) {
                        // 这是一个并行分支，List中的所有节点互为兄弟
                        List<String> parallelBranches = (List<String>) nextTarget;
                        Set<String> siblingsSet = new HashSet<>(parallelBranches);

                        // 为每个并行分支节点记录其兄弟节点
                        for (String branchId : parallelBranches) {
                            siblingsMap.put(branchId, siblingsSet);
                        }
                    }
                }
            }
        }

        log.info("并行兄弟节点映射: {}", siblingsMap);
        return siblingsMap;
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

