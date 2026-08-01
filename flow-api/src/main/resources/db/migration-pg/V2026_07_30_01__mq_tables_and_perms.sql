-- MQ 输入输出节点：连接配置 / MQ 任务 / 执行日志三张表 + flow:mq 权限种子（PostgreSQL）
-- 背景：新增 mqTrigger 入口节点与 mqSend 输出节点，配套 MQ 连接管理与 MQ 任务资产

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

-- MQ 任务定义（mqTrigger 入口流程资产）
CREATE TABLE IF NOT EXISTS flow_mq_task_info (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  directory_id varchar(32),
  connection_code varchar(64) NOT NULL,
  topic varchar(255) NOT NULL,
  consumer_group varchar(128),
  concurrency integer NOT NULL DEFAULT 1,
  enabled smallint NOT NULL DEFAULT 1,
  log_enabled smallint NOT NULL DEFAULT 1,
  dsl_content text,
  publish_status smallint NOT NULL DEFAULT 0,
  published_snapshot text,
  publish_time timestamp,
  info varchar(512),
  tags varchar(255),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_mq_task_info IS 'MQ 任务定义';
COMMENT ON COLUMN flow_mq_task_info.id IS '雪花ID';
COMMENT ON COLUMN flow_mq_task_info.name IS '任务名称';
COMMENT ON COLUMN flow_mq_task_info.directory_id IS '关联目录ID（复用全局目录树）';
COMMENT ON COLUMN flow_mq_task_info.connection_code IS '绑定 MQ 连接编码（flow_mq_connection.code）';
COMMENT ON COLUMN flow_mq_task_info.topic IS '订阅 topic / 队列名';
COMMENT ON COLUMN flow_mq_task_info.consumer_group IS '消费组（Kafka group.id；Rabbit 忽略）';
COMMENT ON COLUMN flow_mq_task_info.concurrency IS '消费并发数';
COMMENT ON COLUMN flow_mq_task_info.enabled IS '启用状态：0=停用, 1=启用';
COMMENT ON COLUMN flow_mq_task_info.log_enabled IS '是否记录执行日志';
COMMENT ON COLUMN flow_mq_task_info.dsl_content IS '流程定义 DSL JSON（草稿）';
COMMENT ON COLUMN flow_mq_task_info.publish_status IS '发布状态：0=未发布，1=已发布';
COMMENT ON COLUMN flow_mq_task_info.published_snapshot IS '发布快照 JSON：dslContent';
COMMENT ON COLUMN flow_mq_task_info.publish_time IS '最近发布时间';
COMMENT ON COLUMN flow_mq_task_info.info IS '任务描述';
COMMENT ON COLUMN flow_mq_task_info.tags IS '标签，英文逗号分隔';
COMMENT ON COLUMN flow_mq_task_info.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_mq_task_info.create_time IS '创建时间';
COMMENT ON COLUMN flow_mq_task_info.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_create_time ON flow_mq_task_info (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_directory_id ON flow_mq_task_info (directory_id);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_enabled ON flow_mq_task_info (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_publish_status ON flow_mq_task_info (publish_status);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_info_connection_code ON flow_mq_task_info (connection_code);

-- MQ 任务执行日志
CREATE TABLE IF NOT EXISTS flow_mq_task_log (
  id varchar(32) NOT NULL,
  task_id varchar(32) NOT NULL,
  task_name varchar(128),
  topic varchar(255),
  message_id varchar(255),
  trigger_type varchar(16) NOT NULL DEFAULT 'MQ',
  status varchar(16) NOT NULL,
  cost_time_ms bigint,
  error_msg text,
  trace_data text,
  create_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_mq_task_log IS 'MQ 任务执行日志';
COMMENT ON COLUMN flow_mq_task_log.id IS '雪花ID';
COMMENT ON COLUMN flow_mq_task_log.task_id IS '关联 MQ 任务ID';
COMMENT ON COLUMN flow_mq_task_log.task_name IS '任务名称（冗余）';
COMMENT ON COLUMN flow_mq_task_log.topic IS '消息 topic / 队列名';
COMMENT ON COLUMN flow_mq_task_log.message_id IS '消息ID（幂等去重键）';
COMMENT ON COLUMN flow_mq_task_log.trigger_type IS '触发类型：MQ=消息触发, MANUAL=手动';
COMMENT ON COLUMN flow_mq_task_log.status IS '执行状态：SUCCESS / FAILED / SKIPPED / RUNNING';
COMMENT ON COLUMN flow_mq_task_log.cost_time_ms IS '耗时（毫秒）';
COMMENT ON COLUMN flow_mq_task_log.error_msg IS '失败信息';
COMMENT ON COLUMN flow_mq_task_log.trace_data IS 'FlowTrace JSON 快照（logEnabled=true 时记录）';
COMMENT ON COLUMN flow_mq_task_log.create_time IS '执行开始时间';
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_create_time ON flow_mq_task_log (create_time);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_status ON flow_mq_task_log (status);
CREATE INDEX IF NOT EXISTS idx_flow_mq_task_log_task_id ON flow_mq_task_log (task_id);

-- 权限种子：flow:mq:view / flow:mq:write（ADMIN 持有 * 通配无需单独授予）
INSERT INTO flow_sys_permission (id, perm_code, perm_name, group_code, remark, create_time)
VALUES
('p_mq_v', 'flow:mq:view', 'MQ查看', 'flow', 'MQ 连接与 MQ 任务查看', CURRENT_TIMESTAMP),
('p_mq_w', 'flow:mq:write', 'MQ编排', 'flow', 'MQ 连接与 MQ 任务编辑/发布/启停', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET perm_name = EXCLUDED.perm_name;

-- OPERATOR 授予查看+编排；VIEWER 仅查看
INSERT INTO flow_sys_role_permission (role_id, perm_code)
VALUES
('role_operator', 'flow:mq:view'),
('role_operator', 'flow:mq:write'),
('role_viewer', 'flow:mq:view')
ON CONFLICT (role_id, perm_code) DO NOTHING;
