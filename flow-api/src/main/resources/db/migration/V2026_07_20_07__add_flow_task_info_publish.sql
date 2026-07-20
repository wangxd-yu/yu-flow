-- 定时任务发布快照（对齐接口 / 服务编排）
ALTER TABLE `flow_task_info`
    ADD COLUMN `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '发布状态：0=未发布，1=已发布' AFTER `dsl_content`,
    ADD COLUMN `published_snapshot` MEDIUMTEXT NULL COMMENT '发布快照 JSON：dslContent' AFTER `publish_status`,
    ADD COLUMN `publish_time` DATETIME NULL COMMENT '最近发布时间' AFTER `published_snapshot`;

-- 已有任务：有 DSL 的视为已发布，避免升级后调度中断
UPDATE `flow_task_info`
SET `publish_status` = 1,
    `published_snapshot` = JSON_OBJECT('dslContent', `dsl_content`),
    `publish_time` = COALESCE(`update_time`, `create_time`, NOW())
WHERE (`deleted` = 0 OR `deleted` IS NULL)
  AND `dsl_content` IS NOT NULL
  AND TRIM(`dsl_content`) <> '';

CREATE INDEX `idx_task_publish_status` ON `flow_task_info` (`publish_status`);
