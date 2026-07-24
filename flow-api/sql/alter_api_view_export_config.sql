-- 与 Flyway V2026_07_24_02__add_api_view_export_config.sql 对齐
ALTER TABLE `flow_api_info`
    ADD COLUMN `view_export_config` TEXT NULL COMMENT '数据查看与导出 JSON：columns/sheetName/maxExportRows' AFTER `security_config`;
