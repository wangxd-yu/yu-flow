-- ═══════════════════════════════════════════════════════════════════════════
--  State Isolation 迁移脚本
--  将 config / script 共用字段拆分为 4 个独立引擎字段
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── 1. flow_api_info 表：新增 4 个隔离字段 ───────────────────────────────

ALTER TABLE flow_api_info
  ADD COLUMN dsl_content  MEDIUMTEXT  COMMENT '逻辑编排 (FLOW) — Flow DSL JSON',
  ADD COLUMN sql_content  TEXT        COMMENT '数据库 (DB) — SQL 脚本',
  ADD COLUMN json_content MEDIUMTEXT  COMMENT '静态 JSON (JSON) — JSON 内容',
  ADD COLUMN text_content TEXT        COMMENT '静态文本 (STRING) — 纯文本内容';

-- ─── 2. flow_service 表：新增 4 个隔离字段 ─────────────────────────────────

ALTER TABLE flow_service
  ADD COLUMN dsl_content  MEDIUMTEXT  COMMENT '逻辑编排 (FLOW) — Flow DSL JSON',
  ADD COLUMN sql_content  TEXT        COMMENT '数据库 (DB) — SQL 脚本',
  ADD COLUMN json_content MEDIUMTEXT  COMMENT '静态 JSON (JSON) — JSON 内容',
  ADD COLUMN text_content TEXT        COMMENT '静态文本 (STRING) — 纯文本内容';

-- ─── 3. [可选] 数据迁移：将旧 config 按 service_type 分发到新字段 ──────────

-- flow_api_info
UPDATE flow_api_info SET dsl_content  = config WHERE service_type = 'FLOW'   AND config IS NOT NULL AND config != '';
UPDATE flow_api_info SET sql_content  = config WHERE service_type = 'DB'     AND config IS NOT NULL AND config != '';
UPDATE flow_api_info SET json_content = config WHERE service_type = 'JSON'   AND config IS NOT NULL AND config != '';
UPDATE flow_api_info SET text_content = config WHERE service_type = 'STRING' AND config IS NOT NULL AND config != '';

-- flow_service
UPDATE flow_service SET dsl_content  = script WHERE type = 'FLOW'   AND script IS NOT NULL AND script != '';
UPDATE flow_service SET sql_content  = script WHERE type = 'DB'     AND script IS NOT NULL AND script != '';
UPDATE flow_service SET json_content = script WHERE type = 'JSON'   AND script IS NOT NULL AND script != '';
UPDATE flow_service SET text_content = script WHERE type = 'STRING' AND script IS NOT NULL AND script != '';
