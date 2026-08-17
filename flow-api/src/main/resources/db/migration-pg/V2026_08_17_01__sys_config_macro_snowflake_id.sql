-- 2026-08-17：flow_sys_config / flow_sys_macro 主键由 bigserial 改为 varchar(32) 雪花
-- 与 db/migration/V2026_08_17_01__sys_config_macro_snowflake_id.sql 语义等价
-- 已有数字主键 USING id::text；去掉 serial 默认值与序列

ALTER TABLE flow_sys_config ALTER COLUMN id DROP DEFAULT;
ALTER TABLE flow_sys_config ALTER COLUMN id TYPE varchar(32) USING trim(id::text);
ALTER TABLE flow_sys_config ALTER COLUMN id SET NOT NULL;
COMMENT ON COLUMN flow_sys_config.id IS '雪花ID';
DROP SEQUENCE IF EXISTS flow_sys_config_id_seq;

ALTER TABLE flow_sys_macro ALTER COLUMN id DROP DEFAULT;
ALTER TABLE flow_sys_macro ALTER COLUMN id TYPE varchar(32) USING trim(id::text);
ALTER TABLE flow_sys_macro ALTER COLUMN id SET NOT NULL;
COMMENT ON COLUMN flow_sys_macro.id IS '雪花ID';
DROP SEQUENCE IF EXISTS flow_sys_macro_id_seq;
