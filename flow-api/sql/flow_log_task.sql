-- 定时任务执行日志表
CREATE TABLE IF NOT EXISTS `flow_log_task` (
    `id`            VARCHAR(32)    NOT NULL COMMENT '雪花ID',
    `task_id`       VARCHAR(32)    NOT NULL COMMENT '关联任务ID',
    `task_name`     VARCHAR(128)            COMMENT '任务名称（冗余）',
    `trigger_type`  VARCHAR(16)    NOT NULL DEFAULT 'CRON' COMMENT '触发类型：CRON=定时, MANUAL=手动',
    `status`        VARCHAR(16)    NOT NULL COMMENT '执行状态：SUCCESS / FAILED / RUNNING',
    `cost_time_ms`  BIGINT                  COMMENT '耗时（毫秒）',
    `error_msg`     TEXT                    COMMENT '失败信息',
    `trace_data`    LONGTEXT                COMMENT 'FlowTrace JSON 快照（logEnabled=true 时记录）',
    `create_time`   DATETIME                COMMENT '执行开始时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行日志';
