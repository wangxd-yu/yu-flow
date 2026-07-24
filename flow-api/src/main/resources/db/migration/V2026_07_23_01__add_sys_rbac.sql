-- RBAC：用户 / 角色 / 权限
CREATE TABLE IF NOT EXISTS `flow_sys_user` (
    `id`            VARCHAR(32)  NOT NULL,
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码',
    `display_name`  VARCHAR(64)  NULL COMMENT '显示名',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `is_builtin`    TINYINT      NOT NULL DEFAULT 0 COMMENT '1内置不可删',
    `remark`        VARCHAR(255) NULL,
    `create_time`   DATETIME     NULL,
    `update_time`   DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS `flow_sys_role` (
    `id`          VARCHAR(32)  NOT NULL,
    `role_code`   VARCHAR(64)  NOT NULL COMMENT 'ADMIN/OPERATOR/VIEWER',
    `role_name`   VARCHAR(64)  NOT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `is_builtin`  TINYINT      NOT NULL DEFAULT 0,
    `remark`      VARCHAR(255) NULL,
    `create_time` DATETIME     NULL,
    `update_time` DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS `flow_sys_permission` (
    `id`          VARCHAR(32)  NOT NULL,
    `perm_code`   VARCHAR(128) NOT NULL COMMENT '如 flow:api:write',
    `perm_name`   VARCHAR(64)  NOT NULL,
    `group_code`  VARCHAR(64)  NULL COMMENT '分组：flow/ops/infra/sys',
    `remark`      VARCHAR(255) NULL,
    `create_time` DATETIME     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

CREATE TABLE IF NOT EXISTS `flow_sys_user_role` (
    `user_id` VARCHAR(32) NOT NULL,
    `role_id` VARCHAR(32) NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

CREATE TABLE IF NOT EXISTS `flow_sys_role_permission` (
    `role_id`   VARCHAR(32)  NOT NULL,
    `perm_code` VARCHAR(128) NOT NULL,
    PRIMARY KEY (`role_id`, `perm_code`),
    KEY `idx_role_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- 内置角色
INSERT INTO `flow_sys_role` (`id`, `role_code`, `role_name`, `status`, `is_builtin`, `remark`, `create_time`, `update_time`)
VALUES
    ('role_admin', 'ADMIN', '管理员', 1, 1, '全部权限', NOW(), NOW()),
    ('role_operator', 'OPERATOR', '运维员', 1, 1, '编排与观测，无用户/系统配置写', NOW(), NOW()),
    ('role_viewer', 'VIEWER', '只读员', 1, 1, '只读查看', NOW(), NOW())
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- 权限点（菜单级）
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
    ('p_home', 'home:view', '首页', 'home', NULL, NOW()),
    ('p_api_v', 'flow:api:view', '接口查看', 'flow', NULL, NOW()),
    ('p_api_w', 'flow:api:write', '接口编排', 'flow', NULL, NOW()),
    ('p_task_v', 'flow:task:view', '任务查看', 'flow', NULL, NOW()),
    ('p_task_w', 'flow:task:write', '任务编排', 'flow', NULL, NOW()),
    ('p_svc_v', 'flow:service:view', '服务查看', 'flow', NULL, NOW()),
    ('p_svc_w', 'flow:service:write', '服务编排', 'flow', NULL, NOW()),
    ('p_page_v', 'flow:page:view', '页面查看', 'flow', NULL, NOW()),
    ('p_page_w', 'flow:page:write', '页面设计', 'flow', NULL, NOW()),
    ('p_rt_v', 'flow:runtime:view', '运行中心', 'ops', NULL, NOW()),
    ('p_open_v', 'flow:open:view', '开放平台查看', 'ops', NULL, NOW()),
    ('p_open_w', 'flow:open:write', '开放平台管理', 'ops', NULL, NOW()),
    ('p_log_v', 'log:view', '日志中心', 'ops', NULL, NOW()),
    ('p_ds_v', 'flow:ds:view', '数据源查看', 'infra', NULL, NOW()),
    ('p_ds_w', 'flow:ds:write', '数据源管理', 'infra', NULL, NOW()),
    ('p_model_v', 'flow:model:view', '模型查看', 'infra', NULL, NOW()),
    ('p_model_w', 'flow:model:write', '模型管理', 'infra', NULL, NOW()),
    ('p_tpl_v', 'sys:template:view', '响应模板查看', 'sys', NULL, NOW()),
    ('p_tpl_w', 'sys:template:write', '响应模板管理', 'sys', NULL, NOW()),
    ('p_macro_v', 'sys:macro:view', '全局参数查看', 'sys', NULL, NOW()),
    ('p_macro_w', 'sys:macro:write', '全局参数管理', 'sys', NULL, NOW()),
    ('p_cfg_v', 'sys:config:view', '系统配置查看', 'sys', NULL, NOW()),
    ('p_cfg_w', 'sys:config:write', '系统配置管理', 'sys', NULL, NOW()),
    ('p_user_v', 'sys:user:view', '用户查看', 'sys', NULL, NOW()),
    ('p_user_w', 'sys:user:write', '用户管理', 'sys', NULL, NOW()),
    ('p_docs', 'docs:view', '接口文档', 'sys', NULL, NOW()),
    ('p_all', '*', '全部权限', 'sys', 'ADMIN 超权', NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- ADMIN → *
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES ('role_admin', '*')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);

-- OPERATOR：编排写 + 观测 + 基础设施写，无用户/系统配置写
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
    ('role_operator', 'home:view'),
    ('role_operator', 'flow:api:view'),
    ('role_operator', 'flow:api:write'),
    ('role_operator', 'flow:task:view'),
    ('role_operator', 'flow:task:write'),
    ('role_operator', 'flow:service:view'),
    ('role_operator', 'flow:service:write'),
    ('role_operator', 'flow:page:view'),
    ('role_operator', 'flow:page:write'),
    ('role_operator', 'flow:runtime:view'),
    ('role_operator', 'flow:open:view'),
    ('role_operator', 'flow:open:write'),
    ('role_operator', 'log:view'),
    ('role_operator', 'flow:ds:view'),
    ('role_operator', 'flow:ds:write'),
    ('role_operator', 'flow:model:view'),
    ('role_operator', 'flow:model:write'),
    ('role_operator', 'sys:template:view'),
    ('role_operator', 'sys:template:write'),
    ('role_operator', 'sys:macro:view'),
    ('role_operator', 'sys:macro:write'),
    ('role_operator', 'sys:config:view'),
    ('role_operator', 'docs:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);

-- VIEWER：只读
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES
    ('role_viewer', 'home:view'),
    ('role_viewer', 'flow:api:view'),
    ('role_viewer', 'flow:task:view'),
    ('role_viewer', 'flow:service:view'),
    ('role_viewer', 'flow:page:view'),
    ('role_viewer', 'flow:runtime:view'),
    ('role_viewer', 'flow:open:view'),
    ('role_viewer', 'log:view'),
    ('role_viewer', 'flow:ds:view'),
    ('role_viewer', 'flow:model:view'),
    ('role_viewer', 'sys:template:view'),
    ('role_viewer', 'sys:macro:view'),
    ('role_viewer', 'sys:config:view'),
    ('role_viewer', 'docs:view')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);

-- RBAC 开关（默认开；停用后回退 yml 单账号）
INSERT INTO `flow_sys_config` (`config_key`, `config_value`, `value_type`, `config_group`, `remark`, `is_builtin`, `status`, `create_time`, `update_time`)
VALUES
    ('RBAC_ENABLED', 'true', 'BOOLEAN', 'SECURITY',
     '是否启用数据库用户 RBAC；false 时登录回退 yu.flow.username/password', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
