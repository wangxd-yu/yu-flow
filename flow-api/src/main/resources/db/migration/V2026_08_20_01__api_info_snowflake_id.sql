-- 2026-08-20：flow_api_info 主键由 bigint 改为 varchar(32) 雪花
-- 与 db/migration-pg/V2026_08_20_01__api_info_snowflake_id.sql 语义等价
-- 已有数字主键转为十进制字符串；新行由 SnowIdGenerator 写入

SET @api_id_type := (
  SELECT DATA_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_api_info'
    AND COLUMN_NAME = 'id'
);
SET @ddl := IF(
    @api_id_type IN ('bigint', 'int', 'integer'),
    'ALTER TABLE `flow_api_info` MODIFY COLUMN `id` varchar(32) NOT NULL COMMENT ''雪花ID''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
