package org.yu.flow.module.mail;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowMailServiceImpl implements FlowMailService {

    @Resource
    private FlowMailSettings flowMailSettings;

    @Override
    public boolean isReady() {
        return flowMailSettings.isReady();
    }

    @Override
    public void send(MailSendRequest request) {
        if (!flowMailSettings.isReady()) {
            throw new FlowMailException("邮件未配置或未启用：请在系统配置 MAIL_* 或 yu.flow.mail 中填写 SMTP");
        }
        if (request == null) {
            throw new FlowMailException("发信请求为空");
        }
        List<String> to = normalizeAddresses(request.getTo());
        if (to.isEmpty()) {
            throw new FlowMailException("收件人 to 不能为空");
        }
        if (StrUtil.isBlank(request.getSubject())) {
            throw new FlowMailException("邮件主题不能为空");
        }
        if (StrUtil.isBlank(request.getText()) && StrUtil.isBlank(request.getHtml())) {
            throw new FlowMailException("邮件正文 text/html 不能都为空");
        }

        try {
            JavaMailSenderImpl sender = buildSender();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(flowMailSettings.getFrom());
            helper.setTo(to.toArray(new String[0]));
            List<String> cc = normalizeAddresses(request.getCc());
            if (!cc.isEmpty()) {
                helper.setCc(cc.toArray(new String[0]));
            }
            List<String> bcc = normalizeAddresses(request.getBcc());
            if (!bcc.isEmpty()) {
                helper.setBcc(bcc.toArray(new String[0]));
            }
            helper.setSubject(request.getSubject().trim());
            if (StrUtil.isNotBlank(request.getHtml())) {
                helper.setText(StrUtil.nullToEmpty(request.getText()), request.getHtml());
            } else {
                helper.setText(request.getText(), false);
            }
            sender.send(message);
            log.info("[FlowMail] 已发送 to={} subject={}", to, request.getSubject());
        } catch (FlowMailException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowMailException("邮件发送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sendQuietly(MailSendRequest request) {
        try {
            send(request);
            return true;
        } catch (Exception e) {
            log.warn("[FlowMail] 发送失败: {}", e.getMessage());
            return false;
        }
    }

    private JavaMailSenderImpl buildSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(flowMailSettings.getHost());
        sender.setPort(flowMailSettings.getPort());
        sender.setUsername(flowMailSettings.getUsername());
        sender.setPassword(flowMailSettings.getPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (flowMailSettings.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(flowMailSettings.getPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        if (flowMailSettings.isStarttls()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }

    private static List<String> normalizeAddresses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return raw.stream()
                .filter(StrUtil::isNotBlank)
                .flatMap(s -> Arrays.stream(s.split("[,;\\s]+")))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }
}
