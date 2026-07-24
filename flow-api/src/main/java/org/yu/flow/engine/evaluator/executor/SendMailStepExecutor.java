package org.yu.flow.engine.evaluator.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.SendMailStep;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.mail.FlowMailException;
import org.yu.flow.module.mail.FlowMailService;
import org.yu.flow.module.mail.MailSendRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 发送邮件节点：委托 {@link FlowMailService}。
 */
@Slf4j
public class SendMailStepExecutor extends AbstractStepExecutor<SendMailStep> {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public String execute(SendMailStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = prepareInputs(step, context, flow);

        String toRaw = firstNonBlank(str(inputs.get("to")), resolveString(step.getTo(), inputs));
        String ccRaw = firstNonBlank(str(inputs.get("cc")), resolveString(step.getCc(), inputs));
        String bccRaw = firstNonBlank(str(inputs.get("bcc")), resolveString(step.getBcc(), inputs));
        String subject = firstNonBlank(str(inputs.get("subject")), resolveString(step.getSubject(), inputs));
        String text = firstNonBlank(str(inputs.get("text")), resolveString(step.getText(), inputs));
        String html = firstNonBlank(str(inputs.get("html")), resolveString(step.getHtml(), inputs));

        List<String> to = splitAddrs(toRaw);
        if (to.isEmpty()) {
            throw new FlowException("MAIL_TO_REQUIRED", "收件人 to 不能为空", step.getId());
        }
        if (StrUtil.isBlank(subject)) {
            throw new FlowException("MAIL_SUBJECT_REQUIRED", "邮件主题 subject 不能为空", step.getId());
        }
        if (StrUtil.isBlank(text) && StrUtil.isBlank(html)) {
            throw new FlowException("MAIL_BODY_REQUIRED", "邮件正文 text/html 不能都为空", step.getId());
        }

        FlowMailService mailService;
        try {
            mailService = SpringUtil.getBean(FlowMailService.class);
        } catch (Exception e) {
            throw new FlowException("MAIL_SERVICE_UNAVAILABLE", "邮件服务不可用: " + e.getMessage(), step.getId());
        }
        if (mailService == null || !mailService.isReady()) {
            throw new FlowException("MAIL_NOT_CONFIGURED",
                    "SMTP 未配置或未启用，请在系统配置「邮件 SMTP」中填写", step.getId());
        }

        try {
            mailService.send(MailSendRequest.builder()
                    .to(to)
                    .cc(splitAddrs(ccRaw))
                    .bcc(splitAddrs(bccRaw))
                    .subject(subject.trim())
                    .text(text)
                    .html(html)
                    .build());
        } catch (FlowMailException e) {
            throw new FlowException("MAIL_SEND_FAILED", e.getMessage(), step.getId());
        } catch (Exception e) {
            throw new FlowException("MAIL_SEND_FAILED", "邮件发送失败: " + e.getMessage(), step.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("to", to);
        result.put("subject", subject);
        Map<String, Object> out = new HashMap<>();
        out.put(PortNames.OUT, result);
        context.setVar(step.getId(), out);
        return PortNames.OUT;
    }

    private static String resolveString(String template, Map<String, Object> inputs) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object val = inputs.get(varName);
            String replacement = val != null ? String.valueOf(val) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static List<String> splitAddrs(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String a, String b) {
        if (StrUtil.isNotBlank(a)) {
            return a;
        }
        return b;
    }
}
