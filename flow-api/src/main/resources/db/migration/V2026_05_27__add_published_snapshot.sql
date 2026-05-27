-- ═══════════════════════════════════════════════════════════════════
-- V2026_05_27: 为 flow_api_info 增加发布快照字段
--
-- 设计思路：
--   发布时冻结全部运行时内容到 published_snapshot (JSON 快照)，
--   运行时引擎从快照读取，用户可自由编辑草稿而不影响线上。
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE flow_api_info
    ADD COLUMN published_snapshot MEDIUMTEXT COMMENT '发布时的完整内容快照 (JSON)，运行时引擎从此字段读取',
    ADD COLUMN publish_time DATETIME COMMENT '最近一次发布时间';
