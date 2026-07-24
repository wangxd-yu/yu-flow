-- Table: flow_open_credential
-- 开放平台凭证
CREATE TABLE IF NOT EXISTS flow_open_credential (
  id varchar(32) NOT NULL,
  platform_id varchar(32) NOT NULL,
  app_key varchar(64) NOT NULL,
  app_secret_enc varchar(512) NOT NULL,
  secret_hint varchar(16),
  status smallint NOT NULL DEFAULT 1,
  rotated_from_id varchar(32),
  expire_at timestamp,
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_open_credential_app_key UNIQUE (app_key)
);
COMMENT ON TABLE flow_open_credential IS '开放平台凭证';
COMMENT ON COLUMN flow_open_credential.app_secret_enc IS 'AES加密后的secret';
COMMENT ON COLUMN flow_open_credential.secret_hint IS '末4位提示';
COMMENT ON COLUMN flow_open_credential.status IS '0停用 1启用 2已轮换废弃';
COMMENT ON COLUMN flow_open_credential.rotated_from_id IS '轮换来源凭证';
CREATE INDEX IF NOT EXISTS idx_flow_open_credential_platform_id ON flow_open_credential (platform_id);
