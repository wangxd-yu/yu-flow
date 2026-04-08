-- ============================================================
-- 为 flow_datasource 表新增 code 字段（全局唯一逻辑编码）
-- 用途：跨环境导出/导入时，通过 code 关联而非物理 ID
-- ============================================================

ALTER TABLE flow_datasource
    ADD COLUMN `code` VARCHAR(50) DEFAULT NULL COMMENT '数据源全局唯一编码，用于跨环境关联';

ALTER TABLE flow_datasource
    ADD UNIQUE KEY `uk_datasource_code` (`code`);
