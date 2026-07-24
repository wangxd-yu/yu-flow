package org.yu.flow.module.mail;

/**
 * 平台邮件发送服务（告警 Email 通道与后续流程编排节点共用入口）。
 */
public interface FlowMailService {

    /** SMTP 是否已启用且关键项齐全 */
    boolean isReady();

    /**
     * 发送邮件；失败抛 {@link FlowMailException}。
     */
    void send(MailSendRequest request);

    /**
     * 发送邮件；失败返回 false（不抛异常），适合告警等旁路路径。
     */
    boolean sendQuietly(MailSendRequest request);
}
