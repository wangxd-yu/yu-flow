-- Table: flow_release_item
-- 版本单明细
CREATE TABLE IF NOT EXISTS flow_release_item (
  id varchar(32) NOT NULL,
  release_id varchar(32) NOT NULL,
  asset_type varchar(32) NOT NULL,
  asset_id varchar(64) NOT NULL,
  asset_name varchar(255),
  asset_key varchar(128),
  action varchar(16) NOT NULL,
  origin varchar(16) NOT NULL,
  content_hash varchar(64),
  create_by varchar(64),
  create_time timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_release_item_release_id_atype_aid UNIQUE (release_id, asset_type, asset_id)
);
COMMENT ON TABLE flow_release_item IS '版本单明细';
COMMENT ON COLUMN flow_release_item.id IS '雪花ID';
COMMENT ON COLUMN flow_release_item.release_id IS '版本单ID（flow_release.id）';
COMMENT ON COLUMN flow_release_item.asset_type IS '资产类型：API / SERVICE / TASK / MQ_TASK / RESPONSE_TEMPLATE / PAGE / MODEL / SYS_MACRO / SYS_CONFIG / OPEN_PLATFORM / ALERT_RULE';
COMMENT ON COLUMN flow_release_item.asset_id IS '资产ID';
COMMENT ON COLUMN flow_release_item.asset_name IS '资产名称（冗余，资产删除后仍可显示）';
COMMENT ON COLUMN flow_release_item.asset_key IS '按编码匹配的类型（全局宏/系统配置/开放平台）在目标环境的匹配键';
COMMENT ON COLUMN flow_release_item.action IS '动作：UPSERT 新增或更新 / OFFLINE 下线';
COMMENT ON COLUMN flow_release_item.origin IS '来源：MANUAL 手工加入 / DEPENDENCY 依赖补齐 / SCAN 变更扫描';
COMMENT ON COLUMN flow_release_item.content_hash IS '冻结时的内容指纹（SHA-256），冻结后内容变化即视为漂移';
COMMENT ON COLUMN flow_release_item.create_by IS '创建人';
COMMENT ON COLUMN flow_release_item.create_time IS '创建时间';
