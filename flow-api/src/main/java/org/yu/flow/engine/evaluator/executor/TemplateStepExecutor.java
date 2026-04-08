package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.TemplateStep;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template 模版字符串节点执行器
 *
 * 解析 {{key}} 占位符，使用上下文变量替换。
 * 同时支持 ${var} 格式的变量引用。
 */
@Slf4j
public class TemplateStepExecutor extends AbstractStepExecutor<TemplateStep> {

    /** {{key}} 占位符正则 */
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    /** ${var} 变量引用正则 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.+?)}");

    @Override
    public String execute(TemplateStep step, ExecutionContext context, FlowDefinition flow) {
        String template = step.getTemplate();
        if (template == null || template.isEmpty()) {
            throw new FlowException("TEMPLATE_EMPTY", "Template 节点 '" + step.getId() + "' 模版内容为空");
        }

        log.info("Template [{}]: 原始模版: {}", step.getId(), template);

        // 1. 先处理 inputs ETL (如果有)
        Map<String, Object> localVars = this.prepareInputs(step, context, flow);

        // 2. 替换 {{key}} 占位符
        String result = replaceTemplate(template, localVars, context);

        log.info("Template [{}]: 输出: {}", step.getId(), result);

        // 3. 存入上下文
        Map<String, Object> stepResult = new HashMap<>();
        stepResult.put(ContextKeys.RESULT, result);
        context.setVar(step.getId(), stepResult);

        return PortNames.OUT;
    }

    /**
     * 替换 {{key}} 占位符
     * 优先从 localVars (inputs ETL 结果) 中查找，其次从全局 context 查找
     */
    private String replaceTemplate(String template, Map<String, Object> localVars, ExecutionContext context) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = null;

            // 优先从 inputs 提取的本地变量中查找
            if (localVars.containsKey(key)) {
                value = localVars.get(key);
            } else {
                // 从全局 context 查找 (支持嵌套路径如 "user.name")
                value = resolveNestedVar(key, context);
            }

            String replacement = value != null ? String.valueOf(value) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 解析 ${var} 格式的变量引用
     */
    private Object resolveValue(String expression, ExecutionContext context) {
        Matcher matcher = VAR_PATTERN.matcher(expression.trim());
        if (matcher.matches()) {
            return resolveNestedVar(matcher.group(1), context);
        }
        // 直接变量名
        return resolveNestedVar(expression.trim(), context);
    }

    /**
     * 解析嵌套变量路径 (如 "user.name")
     */
    @SuppressWarnings("unchecked")
    private Object resolveNestedVar(String path, ExecutionContext context) {
        String[] parts = path.split("\\.");
        Object current = context.getVariable(parts[0]);

        for (int i = 1; i < parts.length && current != null; i++) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(parts[i]);
            } else {
                return null;
            }
        }

        return current;
    }
}
