-- 定时任务定义表
CREATE TABLE IF NOT EXISTS `flow_task_info` (
    `id`            VARCHAR(32)   NOT NULL COMMENT '雪花ID',
    `name`          VARCHAR(128)  NOT NULL COMMENT '任务名称',
    `directory_id`  VARCHAR(32)            COMMENT '关联目录ID（复用全局目录树）',
    `cron`          VARCHAR(64)   NOT NULL COMMENT 'Cron 表达式，如 0/5 * * * * ?',
    `enabled`       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '启用状态：0=停用, 1=启用',
    `log_enabled`   TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否记录执行日志',
    `dsl_content`   MEDIUMTEXT             COMMENT '流程定义 DSL JSON（草稿）',
    `info`          VARCHAR(512)           COMMENT '任务描述',
    `tags`          VARCHAR(255)           COMMENT '标签，英文逗号分隔',
    `deleted`       INT           NOT NULL DEFAULT 0 COMMENT '软删除：0=正常, 1=已删除',
    `create_time`   DATETIME               COMMENT '创建时间',
    `update_time`   DATETIME               COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_directory_id` (`directory_id`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务定义';
