CREATE TABLE `flow_sys_macro` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `macro_code` varchar(100) NOT NULL COMMENT '宏编码 (前端调用的唯一凭证，如: sys_user_id)',
  `macro_name` varchar(100) NOT NULL COMMENT '宏名称 (如: 当前登录用户 ID)',
  `macro_type` varchar(50) NOT NULL COMMENT '宏类型 (枚举: VARIABLE 变量, FUNCTION 方法)',
  `expression` varchar(500) NOT NULL COMMENT '真实的 SpEL 表达式 (如: @userContext.getUserId())',
  `scope` varchar(50) NOT NULL DEFAULT 'ALL' COMMENT '作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS)',
  `return_type` varchar(50) DEFAULT NULL COMMENT '返回值类型 (用于前端 JS 类型推导提示，如 String, Number)',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态 (1: 启用, 0: 停用)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_macro_code` (`macro_code`) COMMENT '保证宏编码全局唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局宏定义字典表 (Semantic Layer)';