-- 2026-08-17：flow_sys_config / flow_sys_macro 主键由 bigint 自增改为 varchar(32) 雪花
-- 与 db/migration-pg/V2026_08_17_01__sys_config_macro_snowflake_id.sql 语义等价
-- 已有数字主键转为十进制字符串（如 1 → '1'）；新行由 SnowIdGenerator 写入

SET @cfg_type := (
  SELECT DATA_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_sys_config'
    AND COLUMN_NAME = 'id'
);
SET @ddl := IF(
    @cfg_type IN ('bigint', 'int', 'integer'),
    'ALTER TABLE `flow_sys_config` MODIFY COLUMN `id` varchar(32) NOT NULL COMMENT ''雪花ID''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @macro_type := (
  SELECT DATA_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_sys_macro'
    AND COLUMN_NAME = 'id'
);
SET @ddl := IF(
    @macro_type IN ('bigint', 'int', 'integer'),
    'ALTER TABLE `flow_sys_macro` MODIFY COLUMN `id` varchar(32) NOT NULL COMMENT ''雪花ID''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
