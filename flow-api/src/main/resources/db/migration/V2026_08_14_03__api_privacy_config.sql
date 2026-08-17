-- 2026-08-14：接口/目录出站隐私拦截 JSON + 控制台明文预览权限

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_api_info'
    AND COLUMN_NAME = 'privacy_config'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE `flow_api_info` ADD COLUMN `privacy_config` text COMMENT ''出站隐私拦截 JSON：enabled/fieldSuffix/extraFields/mask'' AFTER `security_config`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'flow_directory'
    AND COLUMN_NAME = 'privacy_config'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE `flow_directory` ADD COLUMN `privacy_config` text COMMENT ''目录级出站隐私JSON，结构同ApiPrivacyConfig'' AFTER `security_config`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
('p_privacy_r', 'flow:privacy:reveal', '隐私字段明文预览', 'flow', '控制台数据查看/预览明文逃生口；不替代宿主角色', NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);
