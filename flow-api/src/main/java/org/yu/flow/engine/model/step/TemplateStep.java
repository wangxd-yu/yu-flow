package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.NodeType;

import java.util.Arrays;
import java.util.List;

/**
 * Template 模版字符串节点
 *
 * 用于生成长文本（如消息通知、邮件正文）。
 * 使用 {{key}} 双大括号作为占位符。
 *
 * 配置:
 *   template: 模版字符串，如 "Hello {{name}}, 您的验证码是 {{code}}"
 *   inputs: (ETL) 定义变量来源
 *
 * 输出:
 *   替换完成后的 String，存储在 {nodeId}.result
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TemplateStep extends Step {

    /**
     * 模版字符串
     * 使用 {{key}} 占位符
     */
    private String template;


    @Override
    public String getType() {
        return NodeType.TEMPLATE;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
