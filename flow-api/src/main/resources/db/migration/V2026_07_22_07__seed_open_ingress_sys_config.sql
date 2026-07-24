-- 开放平台 / 入站防护：系统配置（可热更新）
-- 读取优先级：flow_sys_config（启用） > application.yml / 环境变量 > 代码默认
-- 停用(status=0)或删除后自动回退 yml，避免初始化无库配置时不可用
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('OPEN_ENABLED', 'true', 'BOOLEAN', 'OPEN',
     '开放入口总开关（/flow-api/open/**）。停用本项后回退 yu.flow.open.enabled', 1, 1, NOW(), NOW()),
    ('OPEN_ALLOW_PLAIN_SECRET', 'false', 'BOOLEAN', 'OPEN',
     '是否允许 X-Yu-App-Secret 明文头（生产务必 false）', 1, 1, NOW(), NOW()),
    ('OPEN_ALLOW_DIRECT_PATH', 'false', 'BOOLEAN', 'OPEN',
     '是否允许 AppKey 直打真实发布 path（默认仅开放前缀入口）', 1, 1, NOW(), NOW()),
    ('OPEN_REQUIRE_HOST_AUTH', 'false', 'BOOLEAN', 'OPEN',
     'ingress 关闭时：无 AppKey 访问已发布 API 是否强制宿主登录', 1, 1, NOW(), NOW()),
    ('OPEN_CALL_LOG_ENABLED', 'true', 'BOOLEAN', 'OPEN',
     '开放入站摘要日志全局开关（平台可单独关闭）', 1, 1, NOW(), NOW()),
    ('OPEN_SKEW_SECONDS', '300', 'NUMBER', 'OPEN',
     'HMAC 签名时钟偏差（秒）', 1, 1, NOW(), NOW()),
    ('OPEN_NONCE_FAIL_CLOSED', 'true', 'BOOLEAN', 'OPEN',
     'nonce 写入 Redis 失败时是否拒绝请求（生产建议 true）', 1, 1, NOW(), NOW()),
    ('OPEN_INCLUDE_BODY_HASH', 'true', 'BOOLEAN', 'OPEN',
     'HMAC 是否纳入 body SHA-256', 1, 1, NOW(), NOW()),
    ('OPEN_ROTATE_GRACE_HOURS', '24', 'NUMBER', 'OPEN',
     '密钥轮换后旧密可用宽限期（小时）；≤0 立即失效', 1, 1, NOW(), NOW()),

    ('INGRESS_ENABLED', 'false', 'BOOLEAN', 'INGRESS',
     '已发布 API 入站防护总开关。false=信任宿主网关。停用本项后回退 yu.flow.ingress.enabled', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_AUTH_MODE', 'NONE', 'STRING', 'INGRESS',
     '默认鉴权：NONE | HOST | OPEN（接口可覆盖）', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_ANTI_REPLAY', 'true', 'BOOLEAN', 'INGRESS',
     '默认防重放（仅 OPEN 鉴权生效）', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_RATE_LIMIT_ENABLED', 'false', 'BOOLEAN', 'INGRESS',
     '默认是否启用接口限流', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_RATE_LIMIT_QPS', '100', 'NUMBER', 'INGRESS',
     '默认限流 QPS（秒级固定窗口）', 1, 1, NOW(), NOW()),
    ('INGRESS_DEFAULT_IP_ALLOWLIST', '', 'STRING', 'INGRESS',
     '默认 IP 白名单（空=不限制；逗号分隔 IP/CIDR）', 1, 1, NOW(), NOW()),
    ('INGRESS_RATE_LIMIT_FAIL_OPEN', 'true', 'BOOLEAN', 'INGRESS',
     '限流 Redis 失败时是否放行（fail-open）', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE config_key = config_key;
