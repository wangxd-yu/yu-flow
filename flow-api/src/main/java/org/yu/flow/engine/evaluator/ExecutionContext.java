package org.yu.flow.engine.evaluator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.debug.DebugSession;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.ExecutionLog;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强版执行上下文（线程安全 + 深拷贝支持）
 */
public class ExecutionContext {
    private final Map<String, Object> var;

    private Object output;
    private final boolean isReadOnly; // 标记只读上下文
    private final boolean traceEnabled; // 标记是否开启全链路追踪
    // 节点完成状态跟踪（用于多父节点汇聚）
    private final Set<String> completedSteps = Collections.synchronizedSet(new HashSet<>());
    // 追踪日志收集器
    private FlowTrace flowTrace;
    // 正则表达式匹配 ${xxx.xxx} 格式
    private static final Pattern PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    // 内部缓存，用于懒加载节点结果
    private final Map<String, Object> transientCache = Collections.synchronizedMap(new HashMap<>());

    /**
     * 步骤执行计数器（线程安全）。
     * <p>所有通过 copy() 创建的分支上下文共享同一个 AtomicInteger 引用，
     * 因此并行分支也受全局步骤预算约束。</p>
     */
    private AtomicInteger stepCounter = new AtomicInteger(0);

    /**
     * 单次执行允许的最大步骤数。0 或负数表示不限制。
     */
    private int maxSteps = 0;

    /**
     * 关联的调试会话（可为 null 表示非调试模式）。
     * <p>所有通过 copy() 创建的分支上下文共享同一个 DebugSession 引用，
     * 使得并行分支中的断点检测仍然有效。</p>
     */
    private DebugSession debugSession;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 构造函数组
    public ExecutionContext() {
        this(null, false, false);
    }

    public ExecutionContext(Map<String, Object> inputs) {
        this(inputs, false, false);
    }

    public ExecutionContext(Map<String, Object> inputs, boolean readOnly, boolean traceEnabled) {
        this.var = Collections.synchronizedMap(new HashMap<>());
        if (inputs != null) {
            this.var.putAll(deepCopyVariables(inputs));
        }
        this.isReadOnly = readOnly;
        this.traceEnabled = traceEnabled;
        if (this.traceEnabled) {
            this.flowTrace = new FlowTrace();
            this.flowTrace.setStepLogs(Collections.synchronizedList(new ArrayList<>()));
            this.flowTrace.setStartTime(System.currentTimeMillis());
            this.flowTrace.setTraceId(UUID.randomUUID().toString());
            // 保存初始入参的快照
            this.flowTrace.setGlobalInputs(deepCopyVariables(this.var));
        }
    }

    // 线程安全的变量操作方法
    public void setVar(String name, Object value) {
        // 1. 解析变量名（支持 ${var} 格式）
        Matcher matcher = PATTERN.matcher(name);
        String actualName = matcher.find() ? matcher.group(1) : name;

        // 2. 检查只读模式
        if (isReadOnly) {
            throw new IllegalStateException("Cannot modify read-only context");
        }

        // 3. 处理 POJO 列表转换
        Object processedValue = convertPojoListToMap(value);

        // 4. 深拷贝后存入（线程安全）
        var.put(actualName, deepCopyIfNeeded(processedValue));
    }

    // 将 List<POJO> 转换为 List<Map>
    private Object convertPojoListToMap(Object value) {
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty() && isPojo(list.get(0))) {
                return MAPPER.convertValue(value, new TypeReference<List<Map<String, Object>>>() {
                });
            }
        }
        return value; // 非 POJO 列表则原样返回
    }

    // 判断对象是否为 POJO（非 JDK 内置类型）
    private boolean isPojo(Object obj) {
        return obj != null &&
                !obj.getClass().isPrimitive() &&
                !obj.getClass().getName().startsWith("java.") &&
                !obj.getClass().getName().startsWith("javax.");
    }

    public Object getVariable(String name) {
        return var.get(name);
    }

    public Map<String, Object> getVar() {
        return var;
    }

    // ========== 缓存操作 (Transient Cache) ==========
    public void putCache(String key, Object value) {
        if (!isReadOnly) {
            transientCache.put(key, value);
        }
    }

    public Object getCache(String key) {
        return transientCache.get(key);
    }

    public boolean hasCache(String key) {
        return transientCache.containsKey(key);
    }

    // 输出操作
    public void setOutput(Object output) {
        this.output = output;
    }

    public Object getOutput() {
        return output;
    }

    // 深拷贝支持
    private static Object deepCopyIfNeeded(Object obj) {
        if (obj instanceof Map) {
            return deepCopyVariables((Map<?, ?>) obj);
        }
        // 其他不可变对象直接返回
        return obj;
    }

    private static Map<String, Object> deepCopyVariables(Map<?, ?> source) {
        Map<String, Object> copy = new HashMap<>();
        source.forEach((k, v) -> copy.put(k.toString(), deepCopyIfNeeded(v)));
        return copy;
    }

    // 上下文拷贝（支持深拷贝）
    public ExecutionContext copy(boolean deepCopy) {
        ExecutionContext copy = new ExecutionContext(null, this.isReadOnly, this.traceEnabled);
        if (deepCopy) {
            copy.var.putAll(deepCopyVariables(this.var));
            copy.output = deepCopyIfNeeded(this.output);
        } else {
            copy.var.putAll(this.var);
            copy.output = this.output;
        }
        // 复制已完成节点集合（浅拷贝即可，节点ID是字符串）
        copy.completedSteps.addAll(this.completedSteps);
        // 共享同一个追踪日志收集器
        if (this.traceEnabled) {
            copy.flowTrace = this.flowTrace;
        }
        // 共享同一个步骤计数器（并行分支受全局预算约束）
        copy.stepCounter = this.stepCounter;
        copy.maxSteps = this.maxSteps;
        // 共享同一个调试会话（并行分支的断点检测仍然有效）
        copy.debugSession = this.debugSession;
        return copy;
    }

    // 创建只读视图
    public ExecutionContext asReadOnly() {
        ExecutionContext readOnlyCtx = new ExecutionContext(this.var, true, this.traceEnabled);
        if (this.traceEnabled) {
            readOnlyCtx.flowTrace = this.flowTrace;
        }
        return readOnlyCtx;
    }

    public boolean hasVariable(String k) {
        return var.containsKey(k);
    }

    // ========== 节点完成状态管理 ==========
    /**
     * 标记节点为已完成
     * @param stepId 节点ID
     */
    public void markStepCompleted(String stepId) {
        completedSteps.add(stepId);
    }

    /**
     * 检查节点是否已完成
     * @param stepId 节点ID
     * @return true 如果节点已完成
     */
    public boolean isStepCompleted(String stepId) {
        return completedSteps.contains(stepId);
    }

    /**
     * 将当前上下文的已完成节点合并到目标上下文
     * @param targetContext 目标上下文
     */
    public void mergeCompletedStepsTo(ExecutionContext targetContext) {
        targetContext.completedSteps.addAll(this.completedSteps);
    }

    // ========== 全链路追踪 (Trace) ==========
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public void addExecutionLog(ExecutionLog log) {
        if (traceEnabled && flowTrace != null && flowTrace.getStepLogs() != null) {
            flowTrace.getStepLogs().add(log);
        }
    }

    public FlowTrace getFlowTrace() {
        return flowTrace;
    }

    // ========== 调试模式 (Debug Session) ==========

    /**
     * 设置关联的调试会话。
     * @param debugSession 调试会话实例，null 表示非调试模式
     */
    public void setDebugSession(DebugSession debugSession) {
        this.debugSession = debugSession;
    }

    /**
     * 获取关联的调试会话。
     * @return 调试会话实例，null 表示非调试模式
     */
    public DebugSession getDebugSession() {
        return debugSession;
    }

    /**
     * 当前是否处于交互式调试模式。
     */
    public boolean isDebugMode() {
        return debugSession != null;
    }

    // ========== 步骤执行限制 (Anti-Hang) ==========

    /**
     * 设置单次执行允许的最大步骤数。
     * @param maxSteps 最大步骤数，0 或负数表示不限制
     */
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    /**
     * 原子递增步骤计数，并检查是否超出限制。
     * <p>在 FlowEngine.executeStep() 中调用，每执行一个节点 +1。
     * 若超出 maxSteps 限制，立即抛出 FlowException 强行终止流程。</p>
     *
     * @throws FlowException 若步骤数超出限制
     */
    public void incrementAndCheckStepLimit() {
        if (maxSteps <= 0) {
            return; // 不限制
        }
        int count = stepCounter.incrementAndGet();
        if (count > maxSteps) {
            throw new FlowException(
                    "STEP_LIMIT_EXCEEDED",
                    "流程执行步骤数已达上限（" + maxSteps + "），已被安全机制强制终止。" +
                    "可能存在死循环或过于复杂的流程编排，请检查流程设计。"
            );
        }
    }
}
