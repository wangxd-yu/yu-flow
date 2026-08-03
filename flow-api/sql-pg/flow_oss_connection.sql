-- Table: flow_oss_connection
CREATE TABLE IF NOT EXISTS flow_oss_connection (
  id varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  code varchar(64) NOT NULL,
  endpoint varchar(512) NOT NULL,
  access_key varchar(128),
  secret_key varchar(512),
  region varchar(64),
  path_style smallint NOT NULL DEFAULT 1,
  public_bucket varchar(128),
  private_bucket varchar(128),
  public_base_url varchar(512),
  key_prefix varchar(256),
  public_access_mode varchar(32) DEFAULT 'NGINX_PROXY',
  private_download_mode varchar(16) NOT NULL DEFAULT 'STREAM',
  presign_expire_seconds integer NOT NULL DEFAULT 300,
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
COMMENT ON TABLE flow_oss_connection IS 'OSS 连接配置';
COMMENT ON COLUMN flow_oss_connection.id IS '雪花ID';
COMMENT ON COLUMN flow_oss_connection.code IS '连接编码（未删除记录内唯一）';
COMMENT ON COLUMN flow_oss_connection.endpoint IS 'MinIO endpoint';
COMMENT ON COLUMN flow_oss_connection.secret_key IS 'Secret Key（AES 密文）';
COMMENT ON COLUMN flow_oss_connection.path_style IS '是否 path-style 访问';
COMMENT ON COLUMN flow_oss_connection.public_access_mode IS 'ANON / NGINX_PROXY';
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_code ON flow_oss_connection (code);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_enabled ON flow_oss_connection (enabled);
CREATE INDEX IF NOT EXISTS idx_flow_oss_connection_create_time ON flow_oss_connection (create_time);
