package org.yu.flow.config.properties;

/**
 * SMTP 邮件发送。对应 YAML：{@code yu.flow.mail.*}
 * <p>系统配置 MAIL_* 优先于本段。</p>
 */
public class MailProperties {
    private boolean enabled = false;
    private String host = "";
    private int port = 465;
    private String username = "";
    private String password = "";
    /** 发件人地址；空则用 username */
    private String from = "";
    private boolean ssl = true;
    private boolean starttls = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }
}
