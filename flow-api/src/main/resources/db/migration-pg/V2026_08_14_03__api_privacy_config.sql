-- 2026-08-14：接口/目录出站隐私拦截 JSON + 控制台明文预览权限
-- 与 db/migration/V2026_08_14_03__api_privacy_config.sql 语义等价

ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS privacy_config text;
COMMENT ON COLUMN flow_api_info.privacy_config IS '出站隐私拦截 JSON：enabled/fieldSuffix/extraFields/mask';

ALTER TABLE flow_directory ADD COLUMN IF NOT EXISTS privacy_config text;
COMMENT ON COLUMN flow_directory.privacy_config IS '目录级出站隐私JSON，结构同ApiPrivacyConfig';

INSERT INTO flow_sys_permission (id, perm_code, perm_name, group_code, remark, create_time)
VALUES
('p_privacy_r', 'flow:privacy:reveal', '隐私字段明文预览', 'flow', '控制台数据查看/预览明文逃生口；不替代宿主角色', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET perm_name = EXCLUDED.perm_name;
