package org.yu.flow.engine.evaluator.executor;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.EndStep;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End 终点节点执行器
 * 设置流程输出结果
 */
public class EndStepExecutor extends AbstractStepExecutor<EndStep> {

    // 匹配 ${xxx.yyy} 格式的模板变量
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");

    @Override
    public String execute(EndStep step, ExecutionContext context, FlowDefinition flow) {
        String responseBody = step.getResponseBody();

        if (responseBody != null && !responseBody.isEmpty()) {
            // 处理模板变量替换
            Object output = resolveTemplate(responseBody, context);
            context.setOutput(output);
        }

        return null; // End 节点没有下一步
    }

    /**
     * 解析模板，替换 ${xxx.yyy} 格式的变量引用
     */
    private Object resolveTemplate(String template, ExecutionContext context) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);

        // 如果整个模板就是一个变量引用，返回原始值类型
        if (matcher.matches()) {
            String path = matcher.group(1);
            return getValueByPath(path, context);
        }

        // 否则进行字符串替换
        matcher.reset();
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1);
            Object value = getValueByPath(path, context);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        // 如果没有任何模板变量，返回原始字符串
        if (result.length() == 0) {
            return template;
        }

        return result.toString();
    }

    /**
     * 通过路径获取值，支持 nodeId.result 格式
     */
    private Object getValueByPath(String path, ExecutionContext context) {
        try {
            // 使用 JSONPath 查询
            String jsonPath = "$." + path;
            return JsonPath.read(context.getVar(), jsonPath);
        } catch (PathNotFoundException e) {
            return null;
        }
    }
}
