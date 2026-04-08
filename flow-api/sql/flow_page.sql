-- ============================================================
-- 页面可视化管理 — 建表脚本
-- ============================================================

-- 1. 目录表
CREATE TABLE IF NOT EXISTS `flow_page_directory` (
    `id`          VARCHAR(64)  NOT NULL COMMENT '主键（雪花ID）',
    `parent_id`   VARCHAR(64)  DEFAULT NULL COMMENT '父节点ID，NULL 表示根节点',
    `name`        VARCHAR(128) NOT NULL COMMENT '目录名称',
    `sort`        INT          DEFAULT 0 COMMENT '排序（升序）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面目录表';

-- 2. 页面信息表
CREATE TABLE IF NOT EXISTS `flow_page_info` (
    `id`           VARCHAR(64)  NOT NULL COMMENT '主键（雪花ID）',
    `directory_id` VARCHAR(64)  DEFAULT NULL COMMENT '所属目录ID',
    `name`         VARCHAR(256) NOT NULL COMMENT '页面名称',
    `route_path`   VARCHAR(512) NOT NULL COMMENT '访问路径（唯一）',
    `json`       LONGTEXT     DEFAULT NULL COMMENT '页面配置 JSON Schema（Amis）',
    `status`       TINYINT      DEFAULT 0 COMMENT '状态：0=草稿，1=已发布',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_route_path` (`route_path`),
    INDEX `idx_directory_id` (`directory_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面信息表';

-- 初始化根目录
INSERT INTO `flow_page_directory` (`id`, `parent_id`, `name`, `sort`)
VALUES ('root', NULL, '全部页面', 0);
