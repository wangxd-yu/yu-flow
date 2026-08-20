-- ============================================================================
-- Yu Flow 系统种子数据（MySQL）
-- 来源：本地 flow 库导出；幂等 ON DUPLICATE KEY UPDATE
-- 用法：先执行 00_all_flow_tables.sql，再执行本文件
-- 取代：flow_sys_config_init.sql / flow_sys_macro_init.sql（可保留作参考）
-- 管理员账号：不写用户表，启动时由 RbacAdminBootstrap + yu.flow.* 引导
-- 注意：flow_db_connection 含本机 JDBC URL，部署时请按环境修改或删掉该段
-- ============================================================================

-- ===== flow_sys_role (3) =====

INSERT INTO `flow_sys_role` (`id`, `role_code`, `role_name`, `status`, `is_builtin`, `remark`, `create_time`, `update_time`) VALUES
('role_admin', 'ADMIN', '管理员', 1, 1, '全部权限', '2026-07-22 20:52:46', '2026-07-22 20:52:46'),
('role_operator', 'OPERATOR', '运维员', 1, 1, '编排与观测，无用户/系统配置写', '2026-07-22 20:52:46', '2026-07-22 20:52:46'),
('role_viewer', 'VIEWER', '只读员', 1, 1, '只读查看', '2026-07-22 20:52:46', '2026-07-22 20:52:46')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_sys_permission (40) =====

INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`) VALUES
('p_alert_v', 'flow:alert:view', '告警查看', 'ops', NULL, '2026-07-22 22:19:49'),
('p_alert_w', 'flow:alert:edit', '告警管理', 'ops', NULL, '2026-07-22 22:19:49'),
('p_all', '*', '全部权限', 'sys', 'ADMIN 超权', '2026-07-22 20:52:46'),
('p_api_v', 'flow:api:view', '接口查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_api_w', 'flow:api:write', '接口编排', 'flow', NULL, '2026-07-22 20:52:46'),
('p_cfg_v', 'sys:config:view', '系统配置查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_cfg_w', 'sys:config:write', '系统配置管理', 'sys', NULL, '2026-07-22 20:52:46'),
('p_docs', 'docs:view', '接口文档', 'sys', NULL, '2026-07-22 20:52:46'),
('p_ds_v', 'flow:ds:view', '数据源查看', 'infra', NULL, '2026-07-22 20:52:46'),
('p_ds_w', 'flow:ds:write', '数据源管理', 'infra', NULL, '2026-07-22 20:52:46'),
('p_home', 'home:view', '首页', 'home', NULL, '2026-07-22 20:52:46'),
('p_host_v', 'sys:host:view', '宿主机配置查看', 'sys', '平台设置 · 宿主机配置', '2026-08-13 16:00:00'),
('p_host_w', 'sys:host:write', '宿主机配置管理', 'sys', '平台设置 · 宿主机配置', '2026-08-13 16:00:00'),
('p_log_v', 'log:view', '日志中心', 'ops', NULL, '2026-07-22 20:52:46'),
('p_macro_v', 'sys:macro:view', '全局参数查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_macro_w', 'sys:macro:write', '全局参数管理', 'sys', NULL, '2026-07-22 20:52:46'),
('p_model_v', 'flow:model:view', '模型查看', 'infra', NULL, '2026-07-22 20:52:46'),
('p_model_w', 'flow:model:write', '模型管理', 'infra', NULL, '2026-07-22 20:52:46'),
('p_mq_v', 'flow:mq:view', 'MQ查看', 'flow', 'MQ 连接与 MQ 任务查看', '2026-08-01 10:16:35'),
('p_mq_w', 'flow:mq:write', 'MQ编排', 'flow', 'MQ 连接与 MQ 任务编辑/发布/启停', '2026-08-01 10:16:35'),
('p_open_v', 'flow:open:view', '开放平台查看', 'ops', NULL, '2026-07-22 20:52:46'),
('p_open_w', 'flow:open:write', '开放平台管理', 'ops', NULL, '2026-07-22 20:52:46'),
('p_oss_a', 'flow:oss:admin', '对象存储管理', 'flow', '跨用户台账与隐私下载（内置 Scope=ALL）', '2026-08-03 19:35:29'),
('p_oss_t', 'flow:oss:audit', '对象存储审计', 'flow', '隐私下载审计日志', '2026-08-03 19:35:29'),
('p_oss_v', 'flow:oss:view', '对象存储查看', 'flow', 'OSS 连接/场景/台账查看', '2026-08-03 19:35:29'),
('p_oss_w', 'flow:oss:write', '对象存储编排', 'flow', 'OSS 连接/场景编辑', '2026-08-03 19:35:29'),
('p_page_v', 'flow:page:view', '页面查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_page_w', 'flow:page:write', '页面设计', 'flow', NULL, '2026-07-22 20:52:46'),
('p_privacy_r', 'flow:privacy:reveal', '隐私字段明文预览', 'flow', '控制台数据查看/预览明文逃生口；不替代宿主角色', '2026-08-14 14:00:00'),
('p_release_v', 'flow:release:view', '发布门禁查看', 'ops', NULL, '2026-07-24 11:28:05'),
('p_release_w', 'flow:release:edit', '发布门禁管理', 'ops', NULL, '2026-07-24 11:28:05'),
('p_role_v', 'sys:role:view', '角色查看', 'sys', NULL, '2026-07-22 21:32:58'),
('p_role_w', 'sys:role:write', '角色管理', 'sys', NULL, '2026-07-22 21:32:58'),
('p_rt_v', 'flow:runtime:view', '运行中心', 'ops', NULL, '2026-07-22 20:52:46'),
('p_svc_v', 'flow:service:view', '服务查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_svc_w', 'flow:service:write', '服务编排', 'flow', NULL, '2026-07-22 20:52:46'),
('p_task_v', 'flow:task:view', '任务查看', 'flow', NULL, '2026-07-22 20:52:46'),
('p_task_w', 'flow:task:write', '任务编排', 'flow', NULL, '2026-07-22 20:52:46'),
('p_tpl_v', 'sys:template:view', '响应模板查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_tpl_w', 'sys:template:write', '响应模板管理', 'sys', NULL, '2026-07-22 20:52:46'),
('p_user_v', 'sys:user:view', '用户查看', 'sys', NULL, '2026-07-22 20:52:46'),
('p_user_w', 'sys:user:write', '用户管理', 'sys', NULL, '2026-07-22 20:52:46')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_sys_role_permission (50) =====

INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`) VALUES
('role_admin', '*'),
('role_operator', 'docs:view'),
('role_operator', 'flow:alert:edit'),
('role_operator', 'flow:alert:view'),
('role_operator', 'flow:api:view'),
('role_operator', 'flow:api:write'),
('role_operator', 'flow:ds:view'),
('role_operator', 'flow:ds:write'),
('role_operator', 'flow:model:view'),
('role_operator', 'flow:model:write'),
('role_operator', 'flow:mq:view'),
('role_operator', 'flow:mq:write'),
('role_operator', 'flow:open:view'),
('role_operator', 'flow:open:write'),
('role_operator', 'flow:oss:view'),
('role_operator', 'flow:oss:write'),
('role_operator', 'flow:page:view'),
('role_operator', 'flow:page:write'),
('role_operator', 'flow:release:edit'),
('role_operator', 'flow:release:view'),
('role_operator', 'flow:runtime:view'),
('role_operator', 'flow:service:view'),
('role_operator', 'flow:service:write'),
('role_operator', 'flow:task:view'),
('role_operator', 'flow:task:write'),
('role_operator', 'home:view'),
('role_operator', 'log:view'),
('role_operator', 'sys:config:view'),
('role_operator', 'sys:host:view'),
('role_operator', 'sys:macro:view'),
('role_operator', 'sys:macro:write'),
('role_operator', 'sys:template:view'),
('role_operator', 'sys:template:write'),
('role_viewer', 'docs:view'),
('role_viewer', 'flow:alert:view'),
('role_viewer', 'flow:api:view'),
('role_viewer', 'flow:ds:view'),
('role_viewer', 'flow:model:view'),
('role_viewer', 'flow:mq:view'),
('role_viewer', 'flow:open:view'),
('role_viewer', 'flow:oss:view'),
('role_viewer', 'flow:page:view'),
('role_viewer', 'flow:release:view'),
('role_viewer', 'flow:runtime:view'),
('role_viewer', 'flow:service:view'),
('role_viewer', 'flow:task:view'),
('role_viewer', 'home:view'),
('role_viewer', 'log:view'),
('role_viewer', 'sys:config:view'),
('role_viewer', 'sys:host:view'),
('role_viewer', 'sys:macro:view'),
('role_viewer', 'sys:template:view')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);


-- ===== flow_sys_config (55) =====

INSERT INTO `flow_sys_config` (`id`, `config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `sort_order`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('1', 'SYSTEM_PREFIX', '/flow-api', 'STRING', 'GATEWAY', '网关 API 统一前缀，影响所有动态 API 的路由注册路径', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 19:29:32'),
('2', 'API_TIMEOUT', '30000', 'NUMBER', 'GATEWAY', 'API 请求超时时间（毫秒），超时后自动中断并返回 504', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-05-22 19:08:06'),
('3', 'TOKEN_EXPIRE', '14400', 'NUMBER', 'SECURITY', 'JWT Token 过期时间（秒），默认 2 小时', 1, 1, 40, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('4', 'TOKEN_REFRESH_EXPIRE', '604800', 'NUMBER', 'SECURITY', 'Refresh Token 过期时间（秒），默认 7 天', 1, 1, 50, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('5', 'LOGIN_MAX_RETRY', '5', 'NUMBER', 'SECURITY', '登录最大重试次数，超过后锁定账号', 1, 1, 20, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('6', 'LOGIN_LOCK_DURATION', '1800', 'NUMBER', 'SECURITY', '账号锁定时长（秒），默认 30 分钟', 1, 1, 30, NULL, '2026-04-12 18:51:52', NULL, '2026-07-22 22:02:34'),
('7', 'SITE_TITLE', 'Yu Flow 低代码平台', 'STRING', 'GENERAL', '系统名称，显示在页面标题和登录页', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 18:51:52'),
('8', 'FILE_UPLOAD_MAX_SIZE', '10485760', 'NUMBER', 'GENERAL', '文件上传最大大小（字节），默认 10MB', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 18:51:52'),
('9', 'PAGINATION_DEFAULT_SIZE', '10', 'NUMBER', 'GENERAL', '默认分页条数', 1, 1, 100, NULL, '2026-04-12 18:51:52', NULL, '2026-04-12 18:51:52'),
('10', 'ASSET_VERSION_RETENTION_COUNT', '20', 'NUMBER', 'FLOW', '接口/任务/服务编排历史版本保留条数（每个资产最多保留最近 N 条，超出自动删除最旧版本；最小为 1）', 1, 1, 100, NULL, '2026-07-20 21:35:31', NULL, '2026-07-20 21:35:31'),
('11', 'OPEN_ENABLED', 'true', 'BOOLEAN', 'OPEN', '开放入口总开关（/flow-api/open/**）。停用本项后回退 yu.flow.open.enabled', 1, 1, 10, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('12', 'OPEN_ALLOW_PLAIN_SECRET', 'false', 'BOOLEAN', 'OPEN', '是否允许 X-Yu-App-Secret 明文头（生产务必 false）', 1, 1, 20, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('13', 'OPEN_ALLOW_DIRECT_PATH', 'false', 'BOOLEAN', 'OPEN', '是否允许 AppKey 直打真实发布 path（默认仅开放前缀入口）', 1, 1, 30, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('14', 'OPEN_REQUIRE_HOST_AUTH', 'true', 'BOOLEAN', 'OPEN', '无 AppKey 访问已发布 API 时是否强制宿主登录（JWT）', 1, 1, 40, NULL, '2026-07-22 20:01:00', NULL, '2026-07-23 17:57:23'),
('15', 'OPEN_CALL_LOG_ENABLED', 'true', 'BOOLEAN', 'OPEN', '开放入站摘要日志全局开关（平台可单独关闭）', 1, 1, 50, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('16', 'OPEN_SKEW_SECONDS', '300', 'NUMBER', 'OPEN', 'HMAC 签名时钟偏差（秒）', 1, 1, 60, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('17', 'OPEN_NONCE_FAIL_CLOSED', 'true', 'BOOLEAN', 'OPEN', 'nonce 写入 Redis 失败时是否拒绝请求（生产建议 true）', 1, 1, 70, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('18', 'OPEN_INCLUDE_BODY_HASH', 'true', 'BOOLEAN', 'OPEN', 'HMAC 是否纳入 body SHA-256', 1, 1, 80, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('19', 'OPEN_ROTATE_GRACE_HOURS', '24', 'NUMBER', 'OPEN', '密钥轮换后旧密可用宽限期（小时）；≤0 立即失效', 1, 1, 90, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('20', 'INGRESS_ENABLED', 'true', 'BOOLEAN', 'INGRESS', '已发布 API 入站防护总开关（安全默认开启；false=关闭后网关仍强制管理端 JWT）', 1, 1, 10, NULL, '2026-07-22 20:01:00', NULL, '2026-07-23 17:57:23'),
('21', 'INGRESS_DEFAULT_AUTH_MODE', 'NONE', 'ENUM', 'INGRESS', '[HOST:需管理端登录|OPEN:开放平台鉴权|NONE:无鉴权（仍受 allow-ingress-auth-none 约束）] 默认鉴权：NONE | HOST | OPEN（接口可覆盖）', 1, 1, 20, NULL, '2026-07-22 20:01:00', NULL, '2026-07-24 23:33:30'),
('22', 'INGRESS_DEFAULT_ANTI_REPLAY', 'true', 'BOOLEAN', 'INGRESS', '默认防重放（仅 OPEN 鉴权生效）', 1, 1, 30, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('23', 'INGRESS_DEFAULT_RATE_LIMIT_ENABLED', 'false', 'BOOLEAN', 'INGRESS', '默认是否启用接口限流', 1, 1, 40, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('24', 'INGRESS_DEFAULT_RATE_LIMIT_QPS', '100', 'NUMBER', 'INGRESS', '默认限流 QPS（秒级固定窗口）', 1, 1, 50, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('25', 'INGRESS_DEFAULT_IP_ALLOWLIST', '', 'STRING', 'INGRESS', '默认 IP 白名单（空=不限制；逗号分隔 IP/CIDR）', 1, 1, 60, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('26', 'INGRESS_RATE_LIMIT_FAIL_OPEN', 'true', 'BOOLEAN', 'INGRESS', '限流 Redis 失败时是否放行（fail-open）', 1, 1, 70, NULL, '2026-07-22 20:01:00', NULL, '2026-07-22 21:59:12'),
('27', 'RBAC_ENABLED', 'true', 'BOOLEAN', 'SECURITY', '是否启用数据库用户 RBAC；false 时登录回退 yu.flow.username/password', 1, 1, 10, NULL, '2026-07-22 20:52:46', NULL, '2026-07-22 22:02:34'),
('28', 'INGRESS_DEFAULT_TIMEOUT_MS', '30000', 'NUMBER', 'INGRESS', '已发布 API 默认执行超时（毫秒）。≤0 不限制。接口 securityConfig.timeoutMs 可覆盖，改完需发布', 1, 1, 80, NULL, '2026-07-22 21:54:31', NULL, '2026-07-22 21:59:12'),
('29', 'ALERT_ENABLED', 'false', 'BOOLEAN', 'ALERT', '运行告警总开关。开启后按间隔扫描近窗口异常并推送 Webhook', 1, 1, 10, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('30', 'ALERT_WEBHOOK_URL', '', 'STRING', 'ALERT', 'Webhook URL（钉钉/企微自定义机器人或任意 HTTP 接收端）', 1, 1, 20, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('31', 'ALERT_INTERVAL_MINUTES', '15', 'NUMBER', 'ALERT', '扫描间隔（分钟）', 1, 1, 30, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('32', 'ALERT_TOP_N', '10', 'NUMBER', 'ALERT', '每次最多推送异常条数', 1, 1, 40, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('33', 'ALERT_WINDOW', '24h', 'STRING', 'ALERT', '指标窗口：1h / 24h / 7d', 1, 1, 50, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('34', 'ALERT_MIN_HEALTH', 'error', 'STRING', 'ALERT', '最低告警健康度：error 仅严重；warn 含预警', 1, 1, 60, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('35', 'ALERT_DEDUP_MINUTES', '60', 'NUMBER', 'ALERT', '同一资产告警去重静默（分钟）', 1, 1, 70, NULL, '2026-07-22 21:54:39', NULL, '2026-07-22 21:59:12'),
('36', 'MAIL_ENABLED', 'false', 'BOOLEAN', 'MAIL', '邮件发送总开关。关闭后告警 Email 通道与后续邮件节点均不可发信', 1, 1, 10, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('37', 'MAIL_HOST', '', 'STRING', 'MAIL', 'SMTP 主机，如 smtp.qq.com / smtp.163.com', 1, 1, 20, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('38', 'MAIL_PORT', '465', 'NUMBER', 'MAIL', 'SMTP 端口：SSL 常用 465，STARTTLS 常用 587', 1, 1, 30, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('39', 'MAIL_USERNAME', '', 'STRING', 'MAIL', 'SMTP 登录账号（通常为邮箱地址）', 1, 1, 40, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('40', 'MAIL_PASSWORD', '', 'STRING', 'MAIL', 'SMTP 密码或应用专用密码', 1, 1, 50, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('41', 'MAIL_FROM', '', 'STRING', 'MAIL', '发件人地址；为空则使用 MAIL_USERNAME', 1, 1, 60, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('42', 'MAIL_SSL', 'true', 'BOOLEAN', 'MAIL', '启用 SMTPS/SSL（465）', 1, 1, 70, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('43', 'MAIL_STARTTLS', 'false', 'BOOLEAN', 'MAIL', '启用 STARTTLS（587）；与 SSL 二选一为主', 1, 1, 80, NULL, '2026-07-22 22:32:36', NULL, '2026-07-22 22:32:36'),
('44', 'LOG_EXECUTION_RETENTION_DAYS', '30', 'NUMBER', 'LOG', 'API 执行日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 10, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('45', 'LOG_LOGIN_RETENTION_DAYS', '90', 'NUMBER', 'LOG', '登录审计日志保留天数（超过此天数的记录将被自动清理，设置为 0 则不清理）', 1, 1, 20, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('46', 'LOG_TASK_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '定时任务执行日志保留天数（flow_log_task；0 = 不清理）', 1, 1, 30, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('47', 'LOG_SERVICE_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '服务编排执行日志保留天数（flow_log_service；0 = 不清理）', 1, 1, 40, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('48', 'LOG_THIRD_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '第三方调用日志保留天数（flow_log_third；0 = 不清理）', 1, 1, 50, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('49', 'LOG_OPEN_CALL_RETENTION_DAYS', '30', 'NUMBER', 'LOG', '开放平台调用日志保留天数（flow_log_open_call；0 = 不清理）', 1, 1, 60, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('50', 'LOG_AUDIT_RETENTION_DAYS', '180', 'NUMBER', 'LOG', '配置变更审计日志保留天数（flow_log_audit；0 = 不清理）', 1, 1, 70, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('51', 'LOG_ALERT_EVENT_RETENTION_DAYS', '90', 'NUMBER', 'LOG', '告警历史事件保留天数（flow_log_alert；0 = 不清理）', 1, 1, 80, NULL, '2026-07-23 10:12:58', NULL, '2026-07-23 10:12:58'),
('52', 'SCRIPT_ALLOWED_LANGUAGES', 'aviator,spel,javascript,groovy', 'STRING', 'SECURITY', 'Evaluate/Switch 等节点允许的脚本语言白名单（逗号分隔）。空=不限制；未在白名单的语言将不可用。', 1, 1, 100, NULL, '2026-07-27 20:25:31', NULL, '2026-07-27 20:25:31'),
('53', 'ENGINE_DEFAULT_LOG_MODE', 'ERROR_ONLY', 'ENUM', 'LOG', '[ERROR_ONLY:仅错误|ALL:全量记录|OFF:完全关闭] 全局默认日志策略。当接口、任务、消息队列或服务编排设置为「继承全局」时，默认生效的日志落库策略。', 1, 1, 15, NULL, '2026-08-01 19:30:35', NULL, '2026-08-01 19:35:51'),
('54', 'MQ_LOG_PAYLOAD_MODE', 'FULL', 'ENUM', 'LOG', '[FULL:明文|MASK:脱敏占位|OFF:不存报文] MQ 消费日志原始报文全局默认策略。任务设为「继承全局」时生效。', 1, 1, 16, NULL, '2026-08-03 19:35:29', NULL, '2026-08-03 19:35:29'),
('55', 'PRIVACY_WRAP_TRANSPORT', 'true', 'BOOLEAN', 'PRIVACY', '已发布 JSON 明文档是否套传输 SM4（X-Privacy-Key）。true=套信封，无会话密钥则 REVEAL 降级脱敏；false=命中明文规则时直接返回明文（内网/调试，生产不建议关）。本项优先于 yml，修改后热更新', 1, 1, 10, NULL, '2026-08-20 17:00:00', NULL, '2026-08-20 17:00:00')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_sys_macro (8) =====

INSERT INTO `flow_sys_macro` (`id`, `macro_code`, `macro_name`, `macro_type`, `expression`, `scope`, `return_type`, `status`, `remark`, `macro_params`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('1', 'UUID', '32位UUID（无连字符）', 'VARIABLE', 'T(cn.hutool.core.util.IdUtil).fastSimpleUUID()', 'ALL', 'String', 1, '生成32位无连字符的UUID字符串', NULL, NULL, '2026-03-24 08:25:06', 'SYSTEM_INIT', '2026-03-28 00:00:35'),
('2', 'DATE_FORMAT', '日期格式化', 'FUNCTION', 'T(java.time.format.DateTimeFormatter).ofPattern(#format).format(#date)', 'ALL', 'String', 1, NULL, 'format, date', NULL, '2026-03-25 00:49:22', NULL, '2026-04-02 00:28:54'),
('3', 'DATE_TIME_FULL', '完整日期时间（含毫秒）', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd HH:mm:ss.SSS''))', 'ALL', 'String', 1, '格式：yyyy-MM-dd HH:mm:ss.SSS', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('4', 'DATE_TIME', '标准日期时间', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd HH:mm:ss''))', 'ALL', 'String', 1, '格式：yyyy-MM-dd HH:mm:ss', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('5', 'DATE', '当前日期', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd''))', 'ALL', 'String', 1, '格式：yyyy-MM-dd', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('6', 'TIME', '当前时间', 'VARIABLE', 'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''HH:mm:ss''))', 'ALL', 'String', 1, '格式：HH:mm:ss', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-03-27 00:45:34'),
('7', 'SNOWFLAKE', '雪花算法ID', 'VARIABLE', 'T(org.yu.flow.auto.util.SnowIdGenerator).getId()', 'ALL', 'String', 1, '基于雪花算法生成分布式唯一ID字符串', NULL, 'SYSTEM_INIT', '2026-03-27 00:18:42', 'SYSTEM_INIT', '2026-04-02 00:12:05'),
('8', 'GET_ENV', '获取环境配置', 'FUNCTION', '@environment.getProperty(#p0)', 'ALL', 'String', 1, '动态获取 Spring Environment 配置项，如 spring.datasource.url。调用方式：宏编码 GET_ENV，上下文参数 {p0: "配置key"}', 'p0', 'SYSTEM_INIT', '2026-03-27 00:45:34', NULL, '2026-03-27 00:45:34')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_env (3) =====

INSERT INTO `flow_env` (`id`, `code`, `name`, `require_suite_pass`, `pass_ttl_hours`, `enabled`, `sort_order`, `remark`, `create_time`, `update_time`) VALUES
('env_dev', 'DEV', '开发', 0, 72, 1, 10, '默认发布环境，不强制回归', '2026-07-24 11:28:05', '2026-07-24 11:28:05'),
('env_staging', 'STAGING', '预发', 1, 48, 1, 20, '发布前需回归通过', '2026-07-24 11:28:05', '2026-07-24 11:28:05'),
('env_prod', 'PROD', '生产', 1, 24, 1, 30, '发布前需回归通过（24h 内）', '2026-07-24 11:28:05', '2026-07-24 11:28:05')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_response_template (1) =====
-- 全局默认响应包装；缺省时网关无法套壳

INSERT INTO `flow_response_template` (`id`, `template_name`, `success_wrapper`, `page_wrapper`, `fail_wrapper`, `is_default`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
('tpl_default_standard', '标准响应模板',
 '{"code": 200, "message": "success", "data": "$"}',
 '{"code": 200, "message": "success", "data": {"items": "$.items", "page": "$.page", "total": "$.total", "current": "$.current", "size": "$.size", "pages": "$.pages"}}',
 '{"code": 500, "message": "$.msg", "data": null}',
 1, '系统内置标准响应格式，适用于大多数业务场景', 'SYSTEM_INIT', '2026-08-12 16:00:00', 'SYSTEM_INIT', '2026-08-12 16:00:00')
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- ===== flow_db_connection (1) =====

INSERT INTO `flow_db_connection` (`id`, `code`, `name`, `db_type`, `driver_class_name`, `url`, `username`, `password`, `initial_size`, `min_idle`, `max_active`, `status`, `wall_config`, `is_system`, `health_status`, `error_count`, `last_error_msg`, `create_time`, `update_time`) VALUES
('2080671779158224896', '[DEFAULT]', '系统默认数据源', 'mysql', 'com.mysql.cj.jdbc.Driver', 'jdbc:mysql://127.0.0.1:3306/flow?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true', 'root', '', 5, 5, 20, 1, '{"enabled":true,"multiStatementAllow":false,"commentAllow":false,"noneBaseStatementAllow":false,"selectAllow":true,"insertAllow":true,"updateAllow":true,"deleteAllow":true,"tableCheck":true,"tableWhiteList":[],"tableBlackList":[],"tableReadOnlyList":[],"functionBlackList":["sleep","benchmark","load_file","updatexml","extractvalue","pg_sleep"],"variantCheck":true}', 1, 'UNKNOWN', 0, NULL, '2026-07-24 23:09:43', '2026-07-24 23:09:43')
ON DUPLICATE KEY UPDATE id = VALUES(id);

