-- 脚本语言白名单：支持通过 flow_sys_config 热更新
-- 读取优先级：flow_sys_config（启用）> application.yml / 环境变量 > 代码默认
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `create_time`, `update_time`)
VALUES
('SCRIPT_ALLOWED_LANGUAGES', 'aviator,spel,javascript,groovy', 'STRING', 'SECURITY',
 'Evaluate/Switch 等节点允许的脚本语言白名单（逗号分隔）。空=不限制；未在白名单的语言将不可用。', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
