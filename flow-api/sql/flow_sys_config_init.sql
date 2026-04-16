-- ============================================================================
-- 系统配置初始化数据
-- 说明：这些是平台启动所需的核心内置配置项，is_builtin = 1 不允许删除
-- ============================================================================

INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`)
VALUES
-- ========================= 网关基础配置 =========================
('SYSTEM_PREFIX', '/flow-api', 'STRING', 'GATEWAY', '网关 API 统一前缀，影响所有动态 API 的路由注册路径', 1, 1),
('API_TIMEOUT', '30000', 'NUMBER', 'GATEWAY', 'API 请求超时时间（毫秒），超时后自动中断并返回 504', 1, 1),

-- ========================= 安全认证配置 =========================
('TOKEN_EXPIRE', '7200', 'NUMBER', 'SECURITY', 'JWT Token 过期时间（秒），默认 2 小时', 1, 1),
('TOKEN_REFRESH_EXPIRE', '604800', 'NUMBER', 'SECURITY', 'Refresh Token 过期时间（秒），默认 7 天', 1, 1),
('LOGIN_MAX_RETRY', '5', 'NUMBER', 'SECURITY', '登录最大重试次数，超过后锁定账号', 1, 1),
('LOGIN_LOCK_DURATION', '1800', 'NUMBER', 'SECURITY', '账号锁定时长（秒），默认 30 分钟', 1, 1),

-- ========================= 通用设置 =========================
('SITE_TITLE', 'Yu Flow 低代码平台', 'STRING', 'GENERAL', '系统名称，显示在页面标题和登录页', 1, 1),
('FILE_UPLOAD_MAX_SIZE', '10485760', 'NUMBER', 'GENERAL', '文件上传最大大小（字节），默认 10MB', 1, 1),
('PAGINATION_DEFAULT_SIZE', '10', 'NUMBER', 'GENERAL', '默认分页条数', 1, 1);
