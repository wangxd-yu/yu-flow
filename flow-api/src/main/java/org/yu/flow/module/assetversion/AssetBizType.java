package org.yu.flow.module.assetversion;

/**
 * 资产历史版本业务类型常量。
 */
public final class AssetBizType {

    public static final String API = "api";
    public static final String TASK = "task";
    public static final String SERVICE = "service";

    public static final String SOURCE_PUBLISH = "publish";
    public static final String SOURCE_ROLLBACK = "rollback";

    /** 系统配置：每资产历史版本保留条数 */
    public static final String CONFIG_RETENTION_COUNT = "ASSET_VERSION_RETENTION_COUNT";
    public static final int DEFAULT_RETENTION_COUNT = 20;

    private AssetBizType() {
    }
}
