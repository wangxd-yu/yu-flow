-- 内部服务编排：发布状态与快照（CALL 走已发布，调试/手动可用草稿）
ALTER TABLE `flow_service_info`
    ADD COLUMN `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发布状态：0=未发布, 1=已发布' AFTER `contract`,
    ADD COLUMN `published_snapshot` MEDIUMTEXT NULL COMMENT '发布快照 JSON：dslContent/contract' AFTER `publish_status`,
    ADD COLUMN `publish_time` DATETIME NULL COMMENT '最近发布时间' AFTER `published_snapshot`;

CREATE INDEX `idx_service_info_publish_status` ON `flow_service_info` (`publish_status`);
