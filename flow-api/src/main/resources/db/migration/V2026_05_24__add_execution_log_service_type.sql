-- ═══════════════════════════════════════════════════════════════════════════
--  Add service_type column to flow_execution_log
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE flow_execution_log
  ADD COLUMN service_type VARCHAR(32) DEFAULT NULL COMMENT '接口类型: FLOW/DB/JSON/STRING';
