package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import com.jayway.jsonpath.JsonPath;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.StepExecutor;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.SystemVarStep;
import org.yu.flow.exception.FlowException;
import cn.hutool.extra.spring.SpringUtil;
import org.yu.flow.module.sysmacro.cache.CachedMacro;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抽象的步骤执行器
 * 提供通用的底层能力，如解析基于 JSONPath 的输入变量
 *
 * @param <T> 具体步骤类型
 */
public abstract class AbstractStepExecutor<T extends Step> implements StepExecutor<T> {

    private SysMacroCacheManager sysMacroCacheManager;

    protected SysMacroCacheManager getSysMacroCacheManager() {
        if (sysMacroCacheManager == null) {
            try {
                sysMacroCacheManager = SpringUtil.getBean(SysMacroCacheManager.class);
            } catch (Exception e) {
                // Return null if bean is not found (e.g., in unit tests)
            }
        }
        return sysMacroCacheManager;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, JsonPath> JSON_PATH_CACHE = new java.util.concurrent.ConcurrentHashMap<>(1024);

    private static final Pattern NODE_ID_PATTERN = Pattern.compile("^\\$\\.([a-zA-Z0-9_\\-]+)\\..*");

    /**
     * 从 step 的 inputs 配置中提取变量值
     * inputs 格式: { "varName": { "extractPath": "$.nodeId.result" } } 或 { "varName": "$.nodeId.xxx" }
     *
     * @param step    步骤定义
     * @param context 执行上下文
     * @param flow    流程定义
     * @return 提取后的变量映射表
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> prepareInputs(T step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = new HashMap<>();

        // 获取步骤的 inputs 配置
        Map<String, Object> inputsConfig = step.getInputs();
        if (inputsConfig == null || inputsConfig.isEmpty()) {
            return inputs;
        }

        // 遍历每个 input 定义，使用 JSONPath 提取值
        Map<String, Object> contextData = context.getVar();
        if (contextData == null) {
            contextData = new HashMap<>();
        }

        for (Map.Entry<String, Object> entry : inputsConfig.entrySet()) {
            String varName = entry.getKey();
            Object config = entry.getValue();

            String extractPath = null;
            if (config instanceof String) {
                // 简化格式: "varName": "$.path"
                extractPath = (String) config;
            } else if (config instanceof Map) {
                // 完整格式: "varName": { "extractPath": "$.path" }
                Map<String, Object> configMap = (Map<String, Object>) config;
                Object pathObj = configMap.get("extractPath");
                if (pathObj instanceof String) {
                    extractPath = (String) pathObj;
                }
            }

            if (extractPath != null && !extractPath.isEmpty()) {
                if (extractPath.startsWith("$.") || extractPath.startsWith("$[")) {
                    // 系统变量阶段：触发懒加载拦截
                    lazyEvaluateSystemVarStepIfNeeded(extractPath, context, flow, contextData);

                    try {
                        JsonPath compiledPath = JSON_PATH_CACHE.computeIfAbsent(extractPath, JsonPath::compile);
                        Object value = compiledPath.read(contextData);
                        inputs.put(varName, value);
                    } catch (Exception e) {
                        // 路径不存在或异常时设置为 null
                        inputs.put(varName, null);
                    }
                } else {
                    inputs.put(varName, extractPath);
                }
            } else {
                inputs.put(varName, config);
            }
        }

        return inputs;
    }

    /**
     * 懒加载拦截，当发现数据来源指向 SystemVarStep 且还未执行时，立刻当场触发对 SpEL 进行求值并缓存
     */
    private void lazyEvaluateSystemVarStepIfNeeded(String extractPath, ExecutionContext context, FlowDefinition flow, Map<String, Object> contextData) {
        if (flow == null || context == null || extractPath == null) {
            return;
        }

        Matcher matcher = NODE_ID_PATTERN.matcher(extractPath);
        if (!matcher.matches()) {
            return;
        }
        String nodeId = matcher.group(1);

        Step sourceStep = flow.getAllSteps().stream()
                .filter(s -> nodeId.equals(s.getId()))
                .findFirst()
                .orElse(null);

        if (sourceStep instanceof SystemVarStep) {
            SystemVarStep sysStep = (SystemVarStep) sourceStep;
            String cacheKey = ContextKeys.SYS_VAR_CACHE_PREFIX + nodeId;
            Object evalResult = null;

            boolean hasCache = context.hasCache(cacheKey);
            if (!sysStep.isVolatileVar() && hasCache) {
                // 读取缓存（Memoization）
                evalResult = context.getCache(cacheKey);
            } else {
                // 动态求值（Lazy Evaluation）
                String macroCode = sysStep.getVariableCode();
                try {
                    SysMacroCacheManager manager = getSysMacroCacheManager();
                    if (manager == null) {
                        return; // 缺少组件，在测试环境下通常是正常的
                    }
                    CachedMacro macro = manager.getMacro(macroCode);
                    if (macro == null) {
                        throw new FlowException("SYSTEM_MACRO_NOT_FOUND",
                                "系统宏未找到或已停用: " + macroCode, nodeId, context.getVar());
                    }

                    StandardEvaluationContext spelCtx = new StandardEvaluationContext();

                    // 【安全提示】此处应注入预先定义好的 SafeTypeLocator (黑名单沙盒)
                    // spelCtx.setTypeLocator(new org.yu.flow.engine.evaluator.spel.SafeTypeLocator());

                    if (context.getVar() != null) {
                        spelCtx.setVariables(context.getVar());
                    }

                    // 注入 Spring 容器支持（如 @bean 调用）
                    try {
                        BeanFactory beanFactory = SpringUtil.getBeanFactory();
                        if (beanFactory != null) {
                            spelCtx.setBeanResolver(new BeanFactoryResolver(beanFactory));
                        }
                    } catch (Exception ignored) { }

                    evalResult = macro.getCompiledExpression().getValue(spelCtx);

                    // 写入缓存供其他并行的普通业务节点复用
                    context.putCache(cacheKey, evalResult);

                    if (macroCode != null && !macroCode.trim().isEmpty()) {
                        context.setVar(macroCode, evalResult);
                    }
                } catch (FlowException e) {
                    throw e;
                } catch (Exception e) {
                    throw new FlowException("SYSTEM_VAR_LAZY_EVAL_ERROR",
                            "系统变量节点 [" + nodeId + "] (code=" + macroCode + ") 延迟求值失败: " + e.getMessage(),
                            nodeId, context.getVar(), e, FlowException.Severity.ERROR);
                }
            }

            // 将求得的数据更新至外层 contextData，以便外层 JsonPath.read(contextData, ...) 可以正常提取此路径节点上的数据
            Map<String, Object> nodeResult = new HashMap<>();
            nodeResult.put(PortNames.OUT, evalResult);
            contextData.put(nodeId, nodeResult);
            context.setVar(nodeId, nodeResult);
        }
    }
}
