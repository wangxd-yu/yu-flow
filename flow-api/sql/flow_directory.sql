-- ============================================================
-- 全局目录架构改造 — DDL 脚本
-- ============================================================

-- ============================================================
-- 任务一：创建全局目录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `flow_directory` (
    `id`          VARCHAR(64)  NOT NULL COMMENT '主键（雪花ID）',
    `parent_id`   VARCHAR(64)  DEFAULT NULL COMMENT '父节点ID，NULL 表示根节点',
    `name`        VARCHAR(128) NOT NULL COMMENT '目录名称',
    `sort`        INT          DEFAULT 0 COMMENT '排序（升序）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局目录表';

-- 初始化根目录
INSERT INTO `flow_directory` (`id`, `parent_id`, `name`, `sort`)
VALUES ('root', NULL, '全部', 0);

-- ============================================================
-- 任务二：核心资产表关联改造
-- ============================================================

-- 2.1 flow_api_info：废弃 module 字段，新增 directory_id
ALTER TABLE `flow_api_info` ADD COLUMN `directory_id` VARCHAR(64) DEFAULT NULL COMMENT '关联全局目录树' AFTER `name`;
ALTER TABLE `flow_api_info` ADD INDEX `idx_directory_id` (`directory_id`);
-- 如需删除旧字段（请确认无业务依赖后执行）：
-- ALTER TABLE `flow_api_info` DROP COLUMN `module`;

-- 2.2 flow_model_info：确认 directory_id 注释
ALTER TABLE `flow_model_info` MODIFY COLUMN `directory_id` VARCHAR(64) DEFAULT NULL COMMENT '关联全局目录树';

-- 2.3 flow_page_info：确认 directory_id 注释
ALTER TABLE `flow_page_info` MODIFY COLUMN `directory_id` VARCHAR(64) DEFAULT NULL COMMENT '关联全局目录树';

-- ============================================================
-- 任务三：清理废弃目录表
-- ============================================================

-- ⚠ 请确认已将旧目录数据迁移至 flow_directory 后再执行
-- DROP TABLE IF EXISTS `flow_model_directory`;
-- DROP TABLE IF EXISTS `flow_page_directory`;
