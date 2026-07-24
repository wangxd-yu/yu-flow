package org.yu.flow.module.mail;

/**
 * 邮件发送失败。
 */
public class FlowMailException extends RuntimeException {
    public FlowMailException(String message) {
        super(message);
    }

    public FlowMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
