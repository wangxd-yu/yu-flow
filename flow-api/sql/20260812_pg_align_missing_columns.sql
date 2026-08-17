-- 【手工·瀚高/PG】补齐「旧表 + CREATE TABLE IF NOT EXISTS」漏掉的列
-- 现象：实体已查 sort_order / cache_config / wall_config，但库中无列
-- 原因：表早已存在时，00_all 不会改表结构
--
-- 用法（二选一）：
-- A) 空库/可清空：DROP 全部 flow_* 后重跑 00_all + 00_system_init（推荐）
-- B) 保留数据：执行本脚本（幂等 ADD COLUMN IF NOT EXISTS）后再启动应用

-- ── flow_sys_config ──
ALTER TABLE flow_sys_config ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 100;
COMMENT ON COLUMN flow_sys_config.sort_order IS '组内展示顺序，越小越靠前';

-- ── flow_api_info（常见演进列）──
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS directory_id varchar(32);
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS intercept_mode varchar(16) NOT NULL DEFAULT 'REPLACE';
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS host_binding text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS contract text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS dsl_content text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS sql_content text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS json_content text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS text_content text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS published_snapshot text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS publish_time timestamp;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS log_enabled boolean NOT NULL DEFAULT true;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS log_mode varchar(16) NOT NULL DEFAULT 'SYSTEM_DEFAULT';
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS log_retention_days integer;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS cache_config text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS security_config text;
ALTER TABLE flow_api_info ADD COLUMN IF NOT EXISTS view_export_config text;
COMMENT ON COLUMN flow_api_info.cache_config IS '响应缓存配置 JSON：enabled/ttlSeconds/keyParams/includePageable';
COMMENT ON COLUMN flow_api_info.security_config IS '入站防护 JSON';
COMMENT ON COLUMN flow_api_info.view_export_config IS '数据查看与导出 JSON';

-- ── flow_datasource ──
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS wall_config text;
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS is_system smallint NOT NULL DEFAULT 0;
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS code varchar(50);
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS health_status varchar(20) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS error_count integer NOT NULL DEFAULT 0;
ALTER TABLE flow_datasource ADD COLUMN IF NOT EXISTS last_error_msg text;
COMMENT ON COLUMN flow_datasource.wall_config IS 'SQL安全墙JSON(DataSourceWallConfig)';
