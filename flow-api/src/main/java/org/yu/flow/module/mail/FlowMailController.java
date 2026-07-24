package org.yu.flow.module.mail;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 邮件探测接口，便于配置 SMTP 后验证连通性。
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/mail")
public class FlowMailController {

    @Resource
    private FlowMailService flowMailService;
    @Resource
    private FlowMailSettings flowMailSettings;

    @PostMapping("/test")
    @RequirePerm("sys:config:write")
    public R<Map<String, Object>> test(@RequestBody TestMailDTO dto) {
        if (!flowMailSettings.isReady()) {
            throw new FlowMailException("邮件未配置或未启用，请先在系统配置「邮件」中填写 SMTP");
        }
        String to = StrUtil.trim(dto == null ? null : dto.getTo());
        if (StrUtil.isBlank(to)) {
            throw new FlowMailException("请填写测试收件人 to");
        }
        flowMailService.send(MailSendRequest.builder()
                .to(List.of(to))
                .subject(StrUtil.blankToDefault(dto.getSubject(), "【Yu Flow】SMTP 测试邮件"))
                .text(StrUtil.blankToDefault(dto.getText(), "这是一封来自 Yu Flow 的 SMTP 测试邮件。"))
                .build());
        return R.ok(Map.of("success", true, "to", to));
    }

    @Data
    public static class TestMailDTO {
        private String to;
        private String subject;
        private String text;
    }
}
