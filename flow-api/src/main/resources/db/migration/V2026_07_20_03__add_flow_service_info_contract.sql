-- 内部服务编排：入参/返回契约（JSON，SchemaNode 风格）
ALTER TABLE `flow_service_info`
    ADD COLUMN `contract` MEDIUMTEXT NULL COMMENT '服务契约 JSON：inputs/outputs/outputDescription' AFTER `dsl_content`;
