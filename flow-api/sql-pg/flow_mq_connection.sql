-- Table: flow_mq_connection
-- MQ 连接配置（RabbitMQ / Kafka）
CREATE TABLE IF NOT EXISTS flow_mq_connection (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  code varchar(64) NOT NULL,
  mq_type varchar(32) NOT NULL,
  servers varchar(512) NOT NULL,
  virtual_host varchar(128),
  username varchar(128),
  password varchar(512),
  enabled smallint NOT NULL DEFAULT 1,
  health_status varchar(32),
  last_error_msg varchar(1024),
  last_test_time timestamp,
  info varchar(512),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_mq_connection IS 'MQ 连接配置';
COMMENT ON COLUMN flow_mq_connection.id IS '雪花ID';
COMMENT ON COLUMN flow_mq_connection.name IS '连接名称';
COMMENT ON COLUMN flow_mq_connection.code IS '连接编码（未删除记录内唯一），流程 DSL / MQ 任务通过 code 引用';
COMMENT ON COLUMN flow_mq_connection.mq_type IS 'MQ 类型：RABBITMQ / KAFKA';
COMMENT ON COLUMN flow_mq_connection.servers IS '服务器地址 host:port，多个逗号分隔（Kafka 即 bootstrap.servers）';
COMMENT ON COLUMN flow_mq_connection.virtual_host IS '虚拟主机（仅 RabbitMQ，默认 /）';
COMMENT ON COLUMN flow_mq_connection.username IS '用户名（可空；Kafka 有值时启用 SASL/PLAIN）';
COMMENT ON COLUMN flow_mq_connection.password IS '密码（AES 密文存储）';
COMMENT ON COLUMN flow_mq_connection.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_mq_connection.health_status IS '健康状态：HEALTHY / UNHEALTHY / UNKNOWN';
COMMENT ON COLUMN flow_mq_connection.last_error_msg IS '最近一次连接测试错误信息';
COMMENT ON COLUMN flow_mq_connection.last_test_time IS '最近一次连接测试时间';
COMMENT ON COLUMN flow_mq_connection.info IS '备注描述';
COMMENT ON COLUMN flow_mq_connection.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_mq_connection.create_time IS '创建时间';
COMMENT ON COLUMN flow_mq_connection.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_mq_connection_code ON flow_mq_connection (code);
CREATE INDEX IF NOT EXISTS idx_flow_mq_connection_enabled ON flow_mq_connection (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_mq_connection_create_time ON flow_mq_connection (create_time);
