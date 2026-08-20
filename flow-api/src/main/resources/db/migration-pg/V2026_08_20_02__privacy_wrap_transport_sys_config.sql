-- 2026-08-20：已发布 JSON 传输封装开关改为系统参数（热更新）
-- 优先级：flow_sys_config（启用）> yu.flow.privacy.wrap-transport > 代码默认 true
-- 与 db/migration/V2026_08_20_02__privacy_wrap_transport_sys_config.sql 语义等价
-- ON CONFLICT 不覆盖 config_value，避免冲掉运维手动改过的值

INSERT INTO flow_sys_config (id, config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES (
  '55',
  'PRIVACY_WRAP_TRANSPORT',
  'true',
  'BOOLEAN',
  'PRIVACY',
  '已发布 JSON 明文档是否套传输 SM4（X-Privacy-Key）。true=套信封，无会话密钥则 REVEAL 降级脱敏；false=命中明文规则时直接返回明文（内网/调试，生产不建议关）。本项优先于 yml，修改后热更新',
  1,
  1,
  10,
  NOW(),
  NOW()
)
ON CONFLICT (config_key) DO UPDATE SET
  remark = EXCLUDED.remark,
  value_type = EXCLUDED.value_type,
  config_group = EXCLUDED.config_group,
  is_builtin = EXCLUDED.is_builtin,
  sort_order = EXCLUDED.sort_order;
