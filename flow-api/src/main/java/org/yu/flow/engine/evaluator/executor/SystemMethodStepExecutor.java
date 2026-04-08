package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import cn.hutool.extra.spring.SpringUtil;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.SystemMethodStep;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.sysmacro.cache.CachedMacro;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统方法调用节点执行器 (System Method Step Executor)
 *
 * <p><b>执行流程：</b></p>
 * <ol>
 *   <li>调用父类 {@code prepareInputs()} 通过 JSONPath 从上下文中提取入参，
 *       得到 {@code Map<String, Object> localVars}（例 {@code {date: Date, format: "yyyy-MM-dd"}}）。</li>
 *   <li>以 {@code localVars} 构建 SpEL {@link StandardEvaluationContext}，
 *       将每个 key 注册为 SpEL 变量（可在表达式中以 {@code #key} 形式引用）。</li>
 *   <li>同时向上下文注入 Spring {@link BeanFactory}，使表达式支持 {@code @bean.method()} 调用。</li>
 *   <li>执行 {@code step.getExpression()} 的 SpEL 表达式，将结果包装为
 *       {@code {out: result}} 写入执行上下文，供下游通过 {@code $.nodeId.out} 提取。</li>
 * </ol>
 *
 * <h3>SpEL 变量注册原理：</h3>
 * <pre>{@code
 * StandardEvaluationContext ctx = new StandardEvaluationContext();
 * ctx.setVariables(localVars);   // Map<String,Object> 整体注册
 * // 此后表达式中 #date 即 localVars.get("date"), #format 即 localVars.get("format")
 * }</pre>
 *
 * <h3>支持的表达式示例：</h3>
 * <ul>
 *   <li>静态工具类：{@code T(cn.hutool.core.util.IdUtil).simpleUUID()}</li>
 *   <li>参数化调用：{@code T(cn.hutool.core.date.DateUtil).format(#date, #format)}</li>
 *   <li>Spring Bean：{@code @myService.encrypt(#input)}</li>
 *   <li>字符串计算：{@code #firstName + ' ' + #lastName}</li>
 *   <li>条件表达式：{@code #score >= 60 ? '及格' : '不及格'}</li>
 * </ul>
 *
 * @author yu-flow
 * @date 2026-03-01
 * @see SystemMethodStep
 * @see AbstractStepExecutor
 */
public class SystemMethodStepExecutor extends AbstractStepExecutor<SystemMethodStep> {

    /**
     * 执行系统方法调用节点。
     *
     * @param step    节点配置（含 methodCode、expression、inputs）
     * @param context 运行时执行上下文（持有全局变量）
     * @param flow    完整流程定义（用于 prepareInputs 内部的 lazyEvaluate 机制）
     * @return 下游出口端口名称，固定为 {@code "out"}
     * @throws FlowException 当表达式为空或执行失败时抛出，包含节点 ID 和上下文快照
     */
    @Override
    public String execute(SystemMethodStep step, ExecutionContext context, FlowDefinition flow) {
        String methodCode = step.getMethodCode();
        try {
            // 1. 获取已编译的宏定义
            SysMacroCacheManager manager = getSysMacroCacheManager();
            if (manager == null) {
                return PortNames.OUT; // 仅测试下发生
            }
            CachedMacro macro = manager.getMacro(methodCode);
            if (macro == null) {
                throw new FlowException("SYSTEM_METHOD_NOT_FOUND",
                        "系统方法未找到或已停用: " + methodCode, step.getId(), context.getVar());
            }

            // 2. 提取入参原始值 (由 prepareInputs 处理 JSONPath 提取)
            Map<String, Object> inputValues = this.prepareInputs(step, context, flow);

            // 3. 构建安全求值上下文
            StandardEvaluationContext spelContext = new StandardEvaluationContext();

            // 【安全提示】此处应注入之前定义的 SafeTypeLocator (黑名单沙盒)
            // spelContext.setTypeLocator(new org.yu.flow.engine.evaluator.spel.SafeTypeLocator());

            // 注入 Spring Bean 解析能力
            try {
                BeanFactory beanFactory = SpringUtil.getBeanFactory();
                if (beanFactory != null) {
                    spelContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
                }
            } catch (Exception ignored) { }

            // 4. 装配参数进入变量池 (#p0, #p1 ...)
            String paramsStr = macro.getSysMacro().getMacroParams();
            if (paramsStr != null && !paramsStr.trim().isEmpty()) {
                String[] paramKeys = paramsStr.split(",");
                for (int i = 0; i < paramKeys.length; i++) {
                    String key = paramKeys[i].trim();
                    Object val = inputValues.get(key);
                    // 推荐使用 #p0, #p1 这种索引方式注入，保证表达式与入参名解耦
                    spelContext.setVariable("p" + i, val);
                    // 同时保留按名注入，增加兼容性
                    spelContext.setVariable(key, val);
                }
            }

            // 5. 执行 SpEL
            Object result = macro.getCompiledExpression().getValue(spelContext);

            // 6. 输出结果
            Map<String, Object> nodeResult = new HashMap<>(2);
            nodeResult.put(PortNames.OUT, result);
            context.setVar(step.getId(), nodeResult);
            context.setOutput(result);

            return PortNames.OUT;
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("SYSTEM_METHOD_EXEC_ERROR",
                    "系统方法节点 [" + step.getId() + "] (code=" + methodCode + ") 执行失败: " + e.getMessage(),
                    step.getId(), context.getVar(), e, FlowException.Severity.ERROR);
        }
    }
}
