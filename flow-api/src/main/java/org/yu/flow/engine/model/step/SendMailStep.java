package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.Arrays;
import java.util.List;

/**
 * 发送邮件节点：复用 {@link org.yu.flow.module.mail.FlowMailService}。
 *
 * <p>字段支持 {@code ${var}} 替换（var 来自 inputs）。也可用 inputs.to / subject / text / html 覆盖。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SendMailStep extends Step {

    /** 收件人，逗号分隔；可被 inputs.to 覆盖 */
    private String to;
    private String cc;
    private String bcc;
    private String subject;
    /** 纯文本正文 */
    private String text;
    /** HTML 正文（有值时按 multipart 发送） */
    private String html;

    @Override
    public String getType() {
        return NodeType.SEND_MAIL;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}
