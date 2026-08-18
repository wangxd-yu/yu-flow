-- 2026-08-18：遗留分目录并入全局 flow_directory 后删除
-- flow_page_directory → flow_directory (biz_type=page；demo_dir_001 为 api)
-- flow_model_directory → flow_directory (biz_type=model)
-- 与 db/migration-pg/V2026_08_18_03__merge_legacy_directories_into_flow_directory.sql 语义等价
-- 新环境 00_all 已无遗留表时跳过拷贝

-- 页面遗留目录
SET @has := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_page_directory'
);
SET @ddl := IF(@has > 0,
  'INSERT INTO `flow_directory` (`id`, `parent_id`, `name`, `biz_type`, `sort`, `create_time`, `update_time`) SELECT `id`, `parent_id`, `name`, CASE WHEN `id` = ''demo_dir_001'' THEN ''api'' ELSE ''page'' END, IFNULL(`sort`, 0), `create_time`, `update_time` FROM `flow_page_directory` p WHERE NOT EXISTS (SELECT 1 FROM `flow_directory` d WHERE d.`id` = p.`id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 模型遗留目录
SET @has := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_model_directory'
);
SET @ddl := IF(@has > 0,
  'INSERT INTO `flow_directory` (`id`, `parent_id`, `name`, `biz_type`, `sort`, `create_time`, `update_time`) SELECT `id`, `parent_id`, `name`, ''model'', IFNULL(`sort`, 0), `create_time`, `update_time` FROM `flow_model_directory` p WHERE NOT EXISTS (SELECT 1 FROM `flow_directory` d WHERE d.`id` = p.`id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史演示种子曾把 API 目录写进 flow_page_directory
INSERT INTO `flow_directory` (`id`, `parent_id`, `name`, `biz_type`, `sort`, `create_time`, `update_time`)
SELECT 'demo_dir_001', NULL, '✨ 官方演示案例', 'api', 999, NOW(), NOW()
FROM DUAL
WHERE EXISTS (SELECT 1 FROM `flow_api_info` WHERE `directory_id` = 'demo_dir_001')
  AND NOT EXISTS (SELECT 1 FROM `flow_directory` WHERE `id` = 'demo_dir_001');

UPDATE `flow_directory`
SET `biz_type` = 'api'
WHERE `id` = 'demo_dir_001'
  AND (`biz_type` IS NULL OR `biz_type` IN ('', 'page'));

SET @has := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_page_directory'
);
SET @ddl := IF(@has > 0, 'DROP TABLE IF EXISTS `flow_page_directory`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flow_model_directory'
);
SET @ddl := IF(@has > 0, 'DROP TABLE IF EXISTS `flow_model_directory`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
