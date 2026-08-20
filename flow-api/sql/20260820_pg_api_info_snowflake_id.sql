-- 【手工·瀚高/PG】flow_api_info.id：bigint → varchar(32) 雪花主键
-- 现象：POST 创建 API 报 SQLState 42804
--   字段 "id" 的类型为 bigint，但表达式的类型为 character varying
-- 原因：JPA FlowApiDO.id 为 String（SnowIdGenerator）；MySQL 对数字字符串会隐式转入 bigint，
--       瀚高/PG 严格拒绝。CREATE TABLE IF NOT EXISTS 不会改已有列类型。
--
-- 用法：在业务 schema 下执行（例：SET search_path TO "ssp-flow";）
-- 已是 varchar 时本脚本幂等跳过。无外键指向该主键（仅逻辑引用 api_id）。

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'flow_api_info'
      AND column_name = 'id'
      AND data_type IN ('bigint', 'integer', 'int', 'int8', 'int4')
  ) THEN
    ALTER TABLE flow_api_info ALTER COLUMN id DROP DEFAULT;
    ALTER TABLE flow_api_info ALTER COLUMN id TYPE varchar(32) USING trim(id::text);
    ALTER TABLE flow_api_info ALTER COLUMN id SET NOT NULL;
  END IF;
END $$;

COMMENT ON COLUMN flow_api_info.id IS '雪花ID';
DROP SEQUENCE IF EXISTS flow_api_info_id_seq;
