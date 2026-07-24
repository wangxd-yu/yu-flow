-- API 数据查看 / Excel 导出列配置
ALTER TABLE `flow_api_info`
    ADD COLUMN `view_export_config` TEXT NULL COMMENT '数据查看与导出 JSON：columns/sheetName/maxExportRows' AFTER `security_config`;
