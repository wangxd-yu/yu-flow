package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import cn.hutool.extra.spring.SpringUtil;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.SystemVarStep;
import org.yu.flow.module.sysmacro.cache.CachedMacro;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统变量节点执行器
 * 负责执行带有 SpEL 表达式的系统变量节点，并将执行结果设置到上下文中，保证可调用 Spring Bean 和静态方法。
 *
 * @author yu-flow
 */
public class SystemVarStepExecutor extends AbstractStepExecutor<SystemVarStep> {

    /**
     * 系统宏缓存管理器
     */
    @Override
    public String execute(SystemVarStep step, ExecutionContext context, FlowDefinition flow) {
        String macroCode = step.getVariableCode();
        try {
            // 1. 获取宏定义实例
            SysMacroCacheManager manager = getSysMacroCacheManager();
            if (manager == null) {
                return PortNames.OUT; // 仅测试下发生
            }
            CachedMacro macro = manager.getMacro(macroCode);
            if (macro == null) {
                throw new FlowException("SYSTEM_MACRO_NOT_FOUND",
                        "系统宏未找到或已停用: " + macroCode, step.getId(), context.getVar());
            }

            // 2. 配置安全求值上下文
            StandardEvaluationContext spelContext = new StandardEvaluationContext();

            // 【安全提示】此处应注入预先定义好的 SafeTypeLocator (黑名单沙盒)
            // spelContext.setTypeLocator(new org.yu.flow.engine.evaluator.spel.SafeTypeLocator());

            // 将当前流程上下文变量作为变量池注入
            if (context.getVar() != null) {
                spelContext.setVariables(context.getVar());
            }

            // 注入 Spring 容器支持（如 @bean 调用）
            try {
                BeanFactory beanFactory = SpringUtil.getBeanFactory();
                if (beanFactory != null) {
                    spelContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
                }
            } catch (Exception ignored) { }

            // 3. 执行预编译的 SpEL 表达式
            Object result = macro.getCompiledExpression().getValue(spelContext);

            // 4. 设置执行结果：1. 按变编码存入 2. 按节点ID存入供 JSONPath 提取
            context.setVar(macroCode, result);

            Map<String, Object> nodeResult = new HashMap<>(2);
            nodeResult.put(PortNames.OUT, result);
            context.setVar(step.getId(), nodeResult);
            context.setOutput(result);

            return PortNames.OUT;
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("SYSTEM_VAR_EXEC_ERROR",
                    "系统变量节点 [" + step.getId() + "] (code=" + macroCode + ") 执行失败: " + e.getMessage(),
                    step.getId(), context.getVar(), e, FlowException.Severity.ERROR);
        }
    }
}
