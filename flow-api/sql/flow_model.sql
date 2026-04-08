-- ============================================================
-- 数据模型管理 — 建表脚本
-- ============================================================

-- 1. 模型目录表
CREATE TABLE IF NOT EXISTS `flow_model_directory` (
    `id`          VARCHAR(64)  NOT NULL COMMENT '主键（雪花ID）',
    `parent_id`   VARCHAR(64)  DEFAULT NULL COMMENT '父节点ID，NULL 表示根节点',
    `name`        VARCHAR(128) NOT NULL COMMENT '目录名称',
    `sort`        INT          DEFAULT 0 COMMENT '排序（升序）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型目录表';

-- 2. 数据模型信息表
CREATE TABLE IF NOT EXISTS `flow_model_info` (
    `id`            VARCHAR(64)  NOT NULL COMMENT '主键（雪花ID）',
    `directory_id`  VARCHAR(64)  DEFAULT NULL COMMENT '关联目录ID',
    `name`          VARCHAR(256) NOT NULL COMMENT '模型中文名，如：用户信息',
    `table_name`    VARCHAR(256) NOT NULL COMMENT '底层物理表名，如：t_user',
    `fields_schema` LONGTEXT     DEFAULT NULL COMMENT '核心元数据 JSON 数组（包含字段名、类型、UI配置等）',
    `status`        TINYINT      DEFAULT 0 COMMENT '状态：0=停用，1=启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_table_name` (`table_name`),
    INDEX `idx_directory_id` (`directory_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据模型信息表';

-- 初始化根目录
INSERT INTO `flow_model_directory` (`id`, `parent_id`, `name`, `sort`)
VALUES ('root', NULL, '全部数据模型', 0);
