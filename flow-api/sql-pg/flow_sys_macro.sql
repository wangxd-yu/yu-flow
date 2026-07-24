-- Table: flow_sys_macro
-- 系统全局宏定义字典表 (Semantic Layer)
CREATE TABLE IF NOT EXISTS flow_sys_macro (
  id bigserial,
  macro_code varchar(100) NOT NULL,
  macro_name varchar(100) NOT NULL,
  macro_type varchar(50) NOT NULL,
  expression varchar(500) NOT NULL,
  scope varchar(50) NOT NULL DEFAULT 'ALL',
  return_type varchar(50),
  status smallint NOT NULL DEFAULT 1,
  remark varchar(500),
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64),
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  macro_params varchar(255),
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_sys_macro_macro_code UNIQUE (macro_code)
);
COMMENT ON TABLE flow_sys_macro IS '系统全局宏定义字典表 (Semantic Layer)';
COMMENT ON COLUMN flow_sys_macro.id IS '主键ID';
COMMENT ON COLUMN flow_sys_macro.macro_code IS '宏编码 (前端调用的唯一凭证，如: sys_user_id)';
COMMENT ON COLUMN flow_sys_macro.macro_name IS '宏名称 (如: 当前登录用户 ID)';
COMMENT ON COLUMN flow_sys_macro.macro_type IS '宏类型 (枚举: VARIABLE 变量, FUNCTION 方法)';
COMMENT ON COLUMN flow_sys_macro.expression IS '真实的 SpEL 表达式 (如: @userContext.getUserId())';
COMMENT ON COLUMN flow_sys_macro.scope IS '作用域 (枚举: ALL 全局, SQL_ONLY 仅SQL, JS_ONLY 仅JS)';
COMMENT ON COLUMN flow_sys_macro.return_type IS '返回值类型 (用于前端 JS 类型推导提示，如 String, Number)';
COMMENT ON COLUMN flow_sys_macro.status IS '状态 (1: 启用, 0: 停用)';
COMMENT ON COLUMN flow_sys_macro.remark IS '备注说明';
COMMENT ON COLUMN flow_sys_macro.create_by IS '创建者';
COMMENT ON COLUMN flow_sys_macro.create_time IS '创建时间';
COMMENT ON COLUMN flow_sys_macro.update_by IS '更新者';
COMMENT ON COLUMN flow_sys_macro.update_time IS '更新时间';
COMMENT ON COLUMN flow_sys_macro.macro_params IS '入参列表 (仅 FUNCTION 类型有效，逗号分隔，如 date,format)';
