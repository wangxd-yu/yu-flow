-- log_mode 日志策略模式列（PostgreSQL）
-- 历史数据平滑迁移：log_enabled 真/1 → ALL，假/0 → OFF，null → SYSTEM_DEFAULT
-- 现网可能混有 boolean 与 smallint，统一 ::integer 再比较（true→1 / false→0）

-- ── 接口管理 flow_api_info ──
ALTER TABLE flow_api_info
    ADD COLUMN IF NOT EXISTS log_mode VARCHAR(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT';
COMMENT ON COLUMN flow_api_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';

UPDATE flow_api_info
SET log_mode = CASE
    WHEN log_enabled::integer = 1 THEN 'ALL'
    WHEN log_enabled::integer = 0 THEN 'OFF'
    ELSE 'SYSTEM_DEFAULT'
END
WHERE (deleted = 0 OR deleted IS NULL);

-- ── 定时任务 flow_task_info ──
ALTER TABLE flow_task_info
    ADD COLUMN IF NOT EXISTS log_mode VARCHAR(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT';
COMMENT ON COLUMN flow_task_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';

UPDATE flow_task_info
SET log_mode = CASE
    WHEN log_enabled::integer = 1 THEN 'ALL'
    WHEN log_enabled::integer = 0 THEN 'OFF'
    ELSE 'SYSTEM_DEFAULT'
END
WHERE (deleted = 0 OR deleted IS NULL);

-- ── MQ 任务 flow_mq_task_info ──
ALTER TABLE flow_mq_task_info
    ADD COLUMN IF NOT EXISTS log_mode VARCHAR(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT';
COMMENT ON COLUMN flow_mq_task_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';

UPDATE flow_mq_task_info
SET log_mode = CASE
    WHEN log_enabled::integer = 1 THEN 'ALL'
    WHEN log_enabled::integer = 0 THEN 'OFF'
    ELSE 'SYSTEM_DEFAULT'
END
WHERE (deleted = 0 OR deleted IS NULL);

-- ── 服务编排 flow_service_info ──
ALTER TABLE flow_service_info
    ADD COLUMN IF NOT EXISTS log_mode VARCHAR(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT';
COMMENT ON COLUMN flow_service_info.log_mode IS '日志策略模式：SYSTEM_DEFAULT-继承全局，OFF-完全关闭，ERROR_ONLY-仅错误时记录，ALL-全量记录';

UPDATE flow_service_info
SET log_mode = CASE
    WHEN log_enabled::integer = 1 THEN 'ALL'
    WHEN log_enabled::integer = 0 THEN 'OFF'
    ELSE 'SYSTEM_DEFAULT'
END
WHERE (deleted = 0 OR deleted IS NULL);

-- ── 注册 ENGINE_DEFAULT_LOG_MODE 全局日志默认策略（系统配置可即时热更） ──
INSERT INTO flow_sys_config (config_key, config_value, value_type, config_group, remark, is_builtin, status, sort_order, create_time, update_time)
VALUES ('ENGINE_DEFAULT_LOG_MODE', 'ERROR_ONLY', 'ENUM', 'LOG', '[ERROR_ONLY:仅错误|ALL:全量记录|OFF:完全关闭] 全局默认日志策略。当接口、任务、消息队列或服务编排设置为「继承全局」时，默认生效的日志落库策略。', 1, 1, 15, NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;
