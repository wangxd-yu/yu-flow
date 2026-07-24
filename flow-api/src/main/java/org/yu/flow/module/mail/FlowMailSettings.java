package org.yu.flow.module.mail;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;

import jakarta.annotation.Resource;

/**
 * 有效 SMTP 配置：系统配置 MAIL_* 优先，回退 {@code yu.flow.mail}。
 */
@Component
public class FlowMailSettings {

    public static final String ENABLED = "MAIL_ENABLED";
    public static final String HOST = "MAIL_HOST";
    public static final String PORT = "MAIL_PORT";
    public static final String USERNAME = "MAIL_USERNAME";
    public static final String PASSWORD = "MAIL_PASSWORD";
    public static final String FROM = "MAIL_FROM";
    public static final String SSL = "MAIL_SSL";
    public static final String STARTTLS = "MAIL_STARTTLS";

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;
    @Resource
    private YuFlowProperties yuFlowProperties;

    public boolean isEnabled() {
        YuFlowProperties.Mail yml = yuFlowProperties.getMail();
        return Boolean.TRUE.equals(sysConfigCacheManager.getBoolConfig(ENABLED, yml != null && yml.isEnabled()));
    }

    public String getHost() {
        return StrUtil.trim(sysConfigCacheManager.getStringConfig(HOST, ymlHost()));
    }

    public int getPort() {
        Integer v = sysConfigCacheManager.getIntConfig(PORT, ymlPort());
        return v == null || v <= 0 ? 465 : v;
    }

    public String getUsername() {
        return StrUtil.trim(sysConfigCacheManager.getStringConfig(USERNAME, ymlUsername()));
    }

    public String getPassword() {
        return sysConfigCacheManager.getStringConfig(PASSWORD, ymlPassword());
    }

    public String getFrom() {
        String from = StrUtil.trim(sysConfigCacheManager.getStringConfig(FROM, ymlFrom()));
        return StrUtil.isNotBlank(from) ? from : getUsername();
    }

    public boolean isSsl() {
        YuFlowProperties.Mail yml = yuFlowProperties.getMail();
        return Boolean.TRUE.equals(sysConfigCacheManager.getBoolConfig(SSL, yml == null || yml.isSsl()));
    }

    public boolean isStarttls() {
        YuFlowProperties.Mail yml = yuFlowProperties.getMail();
        return Boolean.TRUE.equals(sysConfigCacheManager.getBoolConfig(STARTTLS, yml != null && yml.isStarttls()));
    }

    public boolean isReady() {
        return isEnabled()
                && StrUtil.isNotBlank(getHost())
                && StrUtil.isNotBlank(getUsername())
                && StrUtil.isNotBlank(getPassword())
                && StrUtil.isNotBlank(getFrom());
    }

    private String ymlHost() {
        YuFlowProperties.Mail m = yuFlowProperties.getMail();
        return m == null ? "" : StrUtil.nullToEmpty(m.getHost());
    }

    private int ymlPort() {
        YuFlowProperties.Mail m = yuFlowProperties.getMail();
        return m == null ? 465 : m.getPort();
    }

    private String ymlUsername() {
        YuFlowProperties.Mail m = yuFlowProperties.getMail();
        return m == null ? "" : StrUtil.nullToEmpty(m.getUsername());
    }

    private String ymlPassword() {
        YuFlowProperties.Mail m = yuFlowProperties.getMail();
        return m == null ? "" : StrUtil.nullToEmpty(m.getPassword());
    }

    private String ymlFrom() {
        YuFlowProperties.Mail m = yuFlowProperties.getMail();
        return m == null ? "" : StrUtil.nullToEmpty(m.getFrom());
    }
}
