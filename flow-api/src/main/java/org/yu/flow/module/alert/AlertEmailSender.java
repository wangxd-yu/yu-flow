package org.yu.flow.module.alert;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.mail.FlowMailService;
import org.yu.flow.module.mail.MailSendRequest;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 告警 Email 通道：解析通道 config_json，委托 {@link FlowMailService} 真实发信。
 *
 * <p>config 示例：{@code {"to":"a@b.com,b@c.com","cc":"","subject":"可选主题覆盖"}}</p>
 */
@Slf4j
@Component
public class AlertEmailSender {

    @Resource
    private FlowMailService flowMailService;

    public boolean send(String configJson, String defaultSubject, String body) {
        try {
            if (StrUtil.isBlank(configJson)) {
                log.warn("[AlertEmail] 配置为空");
                return false;
            }
            if (!flowMailService.isReady()) {
                log.warn("[AlertEmail] SMTP 未就绪（系统配置 MAIL_* 或 yu.flow.mail）");
                return false;
            }
            JsonNode node = FlowObjectMapperUtil.flowObjectMapper().readTree(configJson);
            List<String> to = splitAddrs(node.path("to").asText(""));
            if (to.isEmpty()) {
                log.warn("[AlertEmail] 缺少 to");
                return false;
            }
            List<String> cc = splitAddrs(node.path("cc").asText(""));
            String subject = StrUtil.blankToDefault(
                    StrUtil.trim(node.path("subject").asText("")),
                    StrUtil.blankToDefault(defaultSubject, "【Yu Flow 运行告警】"));
            return flowMailService.sendQuietly(MailSendRequest.builder()
                    .to(to)
                    .cc(cc.isEmpty() ? null : cc)
                    .subject(subject)
                    .text(body)
                    .build());
        } catch (Exception e) {
            log.warn("[AlertEmail] 发送失败: {}", e.getMessage());
            return false;
        }
    }

    /** @deprecated 使用 {@link #send} */
    @Deprecated
    public boolean sendStub(String configJson, String subject, String body) {
        return send(configJson, subject, body);
    }

    private static List<String> splitAddrs(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }
}
