package org.yu.flow.exception;

/**
 * 配置异常：系统配置、环境配置错误
 */
public class ConfigurationException extends FlowException {

    public ConfigurationException(String message) {
        super("CONFIGURATION_ERROR", message, null, null, null, Severity.FATAL);
    }

    public ConfigurationException(String message, Throwable cause) {
        super("CONFIGURATION_ERROR", message, null, null, cause, Severity.FATAL);
    }
}
