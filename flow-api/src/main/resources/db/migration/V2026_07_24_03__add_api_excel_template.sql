-- API 导出 Excel 公司模板（按接口一份，上传覆盖）
CREATE TABLE IF NOT EXISTS `flow_api_excel_template` (
    `id`            VARCHAR(64)  NOT NULL COMMENT '主键',
    `api_id`        VARCHAR(64)  NOT NULL COMMENT '接口 ID',
    `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `content_type`  VARCHAR(120) NULL COMMENT 'MIME',
    `content`       MEDIUMBLOB   NOT NULL COMMENT 'xlsx 二进制',
    `file_size`     INT          NOT NULL DEFAULT 0,
    `create_time`   DATETIME     NULL,
    `update_time`   DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_excel_tpl_api` (`api_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Excel 导出模板';
