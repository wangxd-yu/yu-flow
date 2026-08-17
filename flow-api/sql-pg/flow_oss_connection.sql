-- Table: flow_oss_connection
-- MinIO / S3 兼容对象存储连接配置
CREATE TABLE IF NOT EXISTS flow_oss_connection (
  id varchar(32) NOT NULL,
  code varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  endpoint varchar(512) NOT NULL,
  access_key varchar(128),
  secret_key varchar(512),
  region varchar(64),
  path_style boolean NOT NULL DEFAULT true,
  public_bucket varchar(128),
  private_bucket varchar(128),
  public_base_url varchar(512),
  key_prefix varchar(256),
  public_access_mode varchar(32) DEFAULT 'NGINX_PROXY',
  private_download_mode varchar(16) NOT NULL DEFAULT 'STREAM',
  presign_expire_seconds integer NOT NULL DEFAULT 300,
  enabled boolean NOT NULL DEFAULT true,
  health_status varchar(32),
  last_error_msg varchar(1024),
  last_test_time timestamp,
  info varchar(512),
  deleted integer NOT NULL DEFAULT 0,
  create_time timestamp,
  update_time timestamp,
  PRIMARY KEY (id)
);
COMMENT ON TABLE flow_oss_connection IS 'OSS 连接配置';
COMMENT ON COLUMN flow_oss_connection.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_connection.code IS '连接编码（未删除记录内唯一）';
COMMENT ON COLUMN flow_oss_connection.name IS '连接名称';
COMMENT ON COLUMN flow_oss_connection.endpoint IS 'MinIO endpoint，如 http://minio:9000';
COMMENT ON COLUMN flow_oss_connection.access_key IS 'Access Key';
COMMENT ON COLUMN flow_oss_connection.secret_key IS 'Secret Key（AES 密文）';
COMMENT ON COLUMN flow_oss_connection.region IS '区域（可空）';
COMMENT ON COLUMN flow_oss_connection.path_style IS '是否 path-style 访问';
COMMENT ON COLUMN flow_oss_connection.public_bucket IS '公有桶名';
COMMENT ON COLUMN flow_oss_connection.private_bucket IS '私有桶名';
COMMENT ON COLUMN flow_oss_connection.public_base_url IS '公有访问前缀（Nginx 对外 URL）';
COMMENT ON COLUMN flow_oss_connection.key_prefix IS '对象键强制前缀';
COMMENT ON COLUMN flow_oss_connection.public_access_mode IS 'ANON / NGINX_PROXY';
COMMENT ON COLUMN flow_oss_connection.private_download_mode IS '隐私下载：STREAM / PRESIGN';
COMMENT ON COLUMN flow_oss_connection.presign_expire_seconds IS '预签名有效期（秒）';
COMMENT ON COLUMN flow_oss_connection.enabled IS '启用状态';
COMMENT ON COLUMN flow_oss_connection.health_status IS 'HEALTHY / UNHEALTHY / UNKNOWN';
COMMENT ON COLUMN flow_oss_connection.last_error_msg IS '最近测试错误';
COMMENT ON COLUMN flow_oss_connection.last_test_time IS '最近测试时间';
COMMENT ON COLUMN flow_oss_connection.info IS '备注';
COMMENT ON COLUMN flow_oss_connection.deleted IS '软删除：0=正常, 1=已删除';
COMMENT ON COLUMN flow_oss_connection.create_time IS '创建时间';
COMMENT ON COLUMN flow_oss_connection.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_code ON flow_oss_connection (code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_enabled ON flow_oss_connection (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_create_time ON flow_oss_connection (create_time);
