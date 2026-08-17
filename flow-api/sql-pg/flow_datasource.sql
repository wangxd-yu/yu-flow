-- Table: flow_datasource
-- 动态数据源配置表
CREATE TABLE IF NOT EXISTS flow_datasource (
  id varchar(64) NOT NULL,
  code varchar(50),
  name varchar(100) NOT NULL,
  db_type varchar(20) NOT NULL,
  driver_class_name varchar(200) NOT NULL,
  url varchar(500) NOT NULL,
  username varchar(100) NOT NULL,
  password varchar(100) NOT NULL,
  initial_size integer DEFAULT 5,
  min_idle integer DEFAULT 5,
  max_active integer DEFAULT 20,
  status smallint DEFAULT 1,
  wall_config text,
  is_system smallint NOT NULL DEFAULT 0,
  health_status varchar(20) NOT NULL DEFAULT 'UNKNOWN',
  error_count integer NOT NULL DEFAULT 0,
  last_error_msg text,
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  update_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_datasource_name UNIQUE (name),
  CONSTRAINT uk_flow_datasource_code UNIQUE (code)
);
COMMENT ON TABLE flow_datasource IS '动态数据源配置表';
COMMENT ON COLUMN flow_datasource.id IS '主键ID';
COMMENT ON COLUMN flow_datasource.code IS '数据源全局唯一编码，用于跨环境关联';
COMMENT ON COLUMN flow_datasource.name IS '数据源名称';
COMMENT ON COLUMN flow_datasource.db_type IS '数据库类型(mysql/postgresql/highgo)';
COMMENT ON COLUMN flow_datasource.driver_class_name IS '驱动类名';
COMMENT ON COLUMN flow_datasource.url IS 'JDBC URL';
COMMENT ON COLUMN flow_datasource.username IS '用户名';
COMMENT ON COLUMN flow_datasource.password IS '密码';
COMMENT ON COLUMN flow_datasource.initial_size IS '初始连接数';
COMMENT ON COLUMN flow_datasource.min_idle IS '最小空闲连接';
COMMENT ON COLUMN flow_datasource.max_active IS '最大活动连接';
COMMENT ON COLUMN flow_datasource.status IS '状态(0-停用,1-启用)';
COMMENT ON COLUMN flow_datasource.wall_config IS 'SQL安全墙JSON(DataSourceWallConfig)';
COMMENT ON COLUMN flow_datasource.is_system IS '系统数据源(1=不可删改连接，如[DEFAULT])';
COMMENT ON COLUMN flow_datasource.health_status IS '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知';
COMMENT ON COLUMN flow_datasource.error_count IS '连续连接失败次数';
COMMENT ON COLUMN flow_datasource.last_error_msg IS '最后一次连接失败的异常堆栈/简述';
COMMENT ON COLUMN flow_datasource.create_time IS '创建时间';
COMMENT ON COLUMN flow_datasource.update_time IS '更新时间';
