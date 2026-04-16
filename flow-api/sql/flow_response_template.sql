-- =====================================================================
-- flow_response_template — API 响应模板表
-- 用于管理不同业务线/调用方的返回 JSON 包装格式
-- =====================================================================

CREATE TABLE IF NOT EXISTS `flow_response_template` (
    `id`              VARCHAR(32)   NOT NULL COMMENT '主键 (雪花ID)',
    `template_name`   VARCHAR(100)  NOT NULL COMMENT '模板名称',
    `success_wrapper` TEXT          NULL     COMMENT '成功包装体 JSON 模板',
    `page_wrapper`    TEXT          NULL     COMMENT '分页包装体 JSON 模板',
    `fail_wrapper`    TEXT          NULL     COMMENT '失败包装体 JSON 模板',
    `is_default`      TINYINT       NOT NULL DEFAULT 0 COMMENT '是否全局默认 (1:默认 0:非默认)',
    `remark`          VARCHAR(500)  NULL     COMMENT '备注说明',
    `create_by`       VARCHAR(64)   NULL     COMMENT '创建者',
    `create_time`     DATETIME      NULL     COMMENT '创建时间',
    `update_by`       VARCHAR(64)   NULL     COMMENT '更新者',
    `update_time`     DATETIME      NULL     COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_name` (`template_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 响应模板表';

-- =====================================================================
-- 初始化数据：插入一条默认的标准响应模板
-- =====================================================================

INSERT INTO `flow_response_template` (`id`, `template_name`, `success_wrapper`, `page_wrapper`, `fail_wrapper`, `is_default`, `remark`, `create_time`, `update_time`)
VALUES (
    '1',
    '标准响应模板',
    '{"code": 200, "message": "success", "data": "$"}',
    '{"code": 200, "message": "success", "data": {"items": "$.items", "total": "$.total", "current": "$.current", "size": "$.size", "pages": "$.pages"}}',
    '{"code": 500, "message": "$.msg", "data": null}',
    1,
    '系统内置标准响应格式，适用于大多数业务场景',
    NOW(),
    NOW()
);
