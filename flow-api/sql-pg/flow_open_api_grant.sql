-- Table: flow_open_api_grant
-- 开放平台接口授权
CREATE TABLE IF NOT EXISTS flow_open_api_grant (
  id varchar(32) NOT NULL,
  platform_id varchar(32) NOT NULL,
  api_id varchar(32) NOT NULL,
  allow_methods varchar(64),
  create_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_open_api_grant_platform_id_api_id UNIQUE (platform_id, api_id)
);
COMMENT ON TABLE flow_open_api_grant IS '开放平台接口授权';
COMMENT ON COLUMN flow_open_api_grant.allow_methods IS '空=跟随接口方法';
CREATE INDEX IF NOT EXISTS idx_flow_open_api_grant_api_id ON flow_open_api_grant (api_id);
