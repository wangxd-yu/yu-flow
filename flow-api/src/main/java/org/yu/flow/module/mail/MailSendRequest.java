package org.yu.flow.module.mail;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 发信请求（告警通道与后续流程编排节点共用）。
 */
@Data
@Builder
public class MailSendRequest {
    /** 收件人，至少一个 */
    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    /** 纯文本正文 */
    private String text;
    /** HTML 正文（与 text 可并存；有 html 时优先作为 multipart） */
    private String html;
}
