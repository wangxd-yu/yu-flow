package org.yu.flow.engine.evaluator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强版执行上下文（线程安全 + 深拷贝支持）
 */
public class ExecutionContext {
    private final Map<String, Object> var;

    private Object output;
    private final boolean isReadOnly; // 标记只读上下文
    // 节点完成状态跟踪（用于多父节点汇聚）
    private final Set<String> completedSteps = Collections.synchronizedSet(new HashSet<>());
    // 正则表达式匹配 ${xxx.xxx} 格式
    private static final Pattern PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    // 内部缓存，用于懒加载节点结果
    private final Map<String, Object> transientCache = Collections.synchronizedMap(new HashMap<>());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 构造函数组
    public ExecutionContext() {
        this(null, false);
    }

    public ExecutionContext(Map<String, Object> inputs) {
        this(inputs, false);
    }

    private ExecutionContext(Map<String, Object> inputs, boolean readOnly) {
        this.var = Collections.synchronizedMap(new HashMap<>());
        if (inputs != null) {
            this.var.putAll(deepCopyVariables(inputs));
        }
        this.isReadOnly = readOnly;
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
        ExecutionContext copy = new ExecutionContext();
        if (deepCopy) {
            copy.var.putAll(deepCopyVariables(this.var));
            copy.output = deepCopyIfNeeded(this.output);
        } else {
            copy.var.putAll(this.var);
            copy.output = this.output;
        }
        // 复制已完成节点集合（浅拷贝即可，节点ID是字符串）
        copy.completedSteps.addAll(this.completedSteps);
        return copy;
    }

    // 创建只读视图
    public ExecutionContext asReadOnly() {
        return new ExecutionContext(this.var, true);
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
}
