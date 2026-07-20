-- 资产历史版本保留数量（系统配置，可在「系统配置」页修改）
-- id 为 bigint 自增，不显式写入；幂等依赖 uk_config_key
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, create_time, update_time)
VALUES
    ('ASSET_VERSION_RETENTION_COUNT', '20', 'NUMBER', 'FLOW',
     '接口/任务/服务编排历史版本保留条数（每个资产最多保留最近 N 条，超出自动删除最旧版本；最小为 1）', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE config_key = config_key;
