-- 目录业务域分域：api / task / service / model / page；空=各模块共用（兼容旧数据）
ALTER TABLE `flow_directory`
    ADD COLUMN `biz_type` VARCHAR(32) NULL COMMENT '业务域：api/task/service/model/page，空=共用' AFTER `name`;

CREATE INDEX `idx_directory_biz_type` ON `flow_directory` (`biz_type`);
