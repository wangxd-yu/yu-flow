package org.yu.flow.config.properties;

/**
 * 元数据库迁移。对应 {@code yu.flow.db.*}。
 */
public class DbProperties {

    /**
     * 启动时是否用 Flyway 迁移元数据库。瀚高连接始终跳过，与本开关无关。
     * 嵌入宿主且表结构由宿主负责时设为 false。
     */
    private boolean autoMigrate = true;

    public boolean isAutoMigrate() {
        return autoMigrate;
    }

    public void setAutoMigrate(boolean autoMigrate) {
        this.autoMigrate = autoMigrate;
    }
}
