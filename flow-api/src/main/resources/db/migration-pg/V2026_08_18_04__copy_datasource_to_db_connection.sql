-- 2026-08-18：JDBC 连接表对齐 oss/mq 的 *_connection 命名
-- 新建 flow_db_connection，从 flow_datasource 复制结构+数据
-- 现网旧表 flow_datasource 保留不删（应用改读新表）
-- 与 db/migration/V2026_08_18_04__copy_datasource_to_db_connection.sql 语义等价

CREATE TABLE IF NOT EXISTS flow_db_connection (
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
  CONSTRAINT uk_flow_db_connection_name UNIQUE (name),
  CONSTRAINT uk_flow_db_connection_code UNIQUE (code)
);
COMMENT ON TABLE flow_db_connection IS 'JDBC 数据库连接配置';
COMMENT ON COLUMN flow_db_connection.id IS '主键ID';
COMMENT ON COLUMN flow_db_connection.code IS '连接全局唯一编码，用于跨环境关联';
COMMENT ON COLUMN flow_db_connection.name IS '连接名称';
COMMENT ON COLUMN flow_db_connection.db_type IS '数据库类型(mysql/postgresql/highgo)';
COMMENT ON COLUMN flow_db_connection.driver_class_name IS '驱动类名';
COMMENT ON COLUMN flow_db_connection.url IS 'JDBC URL';
COMMENT ON COLUMN flow_db_connection.username IS '用户名';
COMMENT ON COLUMN flow_db_connection.password IS '密码';
COMMENT ON COLUMN flow_db_connection.initial_size IS '初始连接数';
COMMENT ON COLUMN flow_db_connection.min_idle IS '最小空闲连接';
COMMENT ON COLUMN flow_db_connection.max_active IS '最大活动连接';
COMMENT ON COLUMN flow_db_connection.status IS '状态(0-停用,1-启用)';
COMMENT ON COLUMN flow_db_connection.wall_config IS 'SQL安全墙JSON(DataSourceWallConfig)';
COMMENT ON COLUMN flow_db_connection.is_system IS '系统连接(1=不可删改连接，如[DEFAULT])';
COMMENT ON COLUMN flow_db_connection.health_status IS '连接健康度：HEALTHY-健康, UNHEALTHY-异常, UNKNOWN-未知';
COMMENT ON COLUMN flow_db_connection.error_count IS '连续连接失败次数';
COMMENT ON COLUMN flow_db_connection.last_error_msg IS '最后一次连接失败的异常堆栈/简述';
COMMENT ON COLUMN flow_db_connection.create_time IS '创建时间';
COMMENT ON COLUMN flow_db_connection.update_time IS '更新时间';

DO $$
BEGIN
  IF to_regclass('flow_datasource') IS NOT NULL THEN
    INSERT INTO flow_db_connection (
      id, code, name, db_type, driver_class_name, url, username, password,
      initial_size, min_idle, max_active, status, wall_config, is_system,
      health_status, error_count, last_error_msg, create_time, update_time
    )
    SELECT
      s.id, s.code, s.name, s.db_type, s.driver_class_name, s.url, s.username, s.password,
      s.initial_size, s.min_idle, s.max_active, s.status, s.wall_config, s.is_system,
      s.health_status, s.error_count, s.last_error_msg, s.create_time, s.update_time
    FROM flow_datasource s
    WHERE NOT EXISTS (SELECT 1 FROM flow_db_connection t WHERE t.id = s.id)
      AND NOT EXISTS (SELECT 1 FROM flow_db_connection t WHERE t.code IS NOT DISTINCT FROM s.code AND s.code IS NOT NULL)
      AND NOT EXISTS (SELECT 1 FROM flow_db_connection t WHERE t.name = s.name);
  END IF;
END $$;
