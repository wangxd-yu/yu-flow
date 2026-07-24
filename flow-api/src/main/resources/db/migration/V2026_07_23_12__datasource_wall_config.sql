-- 数据源 SQL 安全墙（Druid Wall）配置 + 默认数据源哨兵行

ALTER TABLE `flow_datasource`
    ADD COLUMN `wall_config` TEXT NULL COMMENT 'SQL安全墙JSON(DataSourceWallConfig)' AFTER `status`,
    ADD COLUMN `is_system` TINYINT NOT NULL DEFAULT 0 COMMENT '系统数据源(1=不可删改连接，如[DEFAULT])' AFTER `wall_config`;

-- 系统默认数据源哨兵行：连接池仍走 spring.datasource；仅 wall_config 可编辑
INSERT INTO `flow_datasource` (
    `id`, `name`, `code`, `db_type`, `driver_class_name`, `url`, `username`, `password`,
    `initial_size`, `min_idle`, `max_active`, `status`, `wall_config`, `is_system`,
    `health_status`, `error_count`, `create_time`, `update_time`
) VALUES (
    'ds_system_default',
    '系统默认数据源',
    '[DEFAULT]',
    'mysql',
    'com.mysql.cj.jdbc.Driver',
    '',
    '',
    '',
    5, 5, 20, 1,
    '{"enabled":false,"multiStatementAllow":false,"commentAllow":false,"noneBaseStatementAllow":false,"selectAllow":true,"insertAllow":true,"updateAllow":true,"deleteAllow":true,"tableCheck":true,"tableWhiteList":[],"tableBlackList":[],"functionBlackList":["sleep","benchmark","load_file","updatexml","extractvalue","pg_sleep"],"variantCheck":true}',
    1,
    'UNKNOWN',
    0,
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE
    `is_system` = 1,
    `name` = VALUES(`name`);
