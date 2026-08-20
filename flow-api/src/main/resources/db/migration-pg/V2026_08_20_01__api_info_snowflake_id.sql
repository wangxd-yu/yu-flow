-- 2026-08-20：flow_api_info 主键由 bigint 改为 varchar(32) 雪花
-- 与 db/migration/V2026_08_20_01__api_info_snowflake_id.sql 语义等价
-- 已有数字主键 USING trim(id::text)；去掉可能残留的 serial 默认值与序列

ALTER TABLE flow_api_info ALTER COLUMN id DROP DEFAULT;
ALTER TABLE flow_api_info ALTER COLUMN id TYPE varchar(32) USING trim(id::text);
ALTER TABLE flow_api_info ALTER COLUMN id SET NOT NULL;
COMMENT ON COLUMN flow_api_info.id IS '雪花ID';
DROP SEQUENCE IF EXISTS flow_api_info_id_seq;
