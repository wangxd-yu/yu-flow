CREATE TABLE `flow_sys_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键 (唯一标识，如: SYSTEM_PREFIX, TOKEN_EXPIRE)',
  `config_value` text DEFAULT NULL COMMENT '配置值 (支持字符串、数字、JSON 等格式)',
  `value_type` varchar(20) NOT NULL DEFAULT 'STRING' COMMENT '值类型 (STRING / NUMBER / BOOLEAN / JSON)',
  `config_group` varchar(50) NOT NULL DEFAULT 'GENERAL' COMMENT '配置分组 (GENERAL / SECURITY / OSS / GATEWAY 等)',
  `remark` varchar(500) DEFAULT NULL COMMENT '配置说明',
  `is_builtin` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否内置 (1: 内置参数, 不允许删除; 0: 用户自定义)',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态 (1: 启用, 0: 停用)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`) COMMENT '保证配置键全局唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表 (System Configuration)';
