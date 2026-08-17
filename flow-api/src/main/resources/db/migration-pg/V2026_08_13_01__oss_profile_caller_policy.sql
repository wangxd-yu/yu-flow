-- 2026-08-13 增量：OSS 上传场景宿主调用方策略
-- 与 db/migration/V2026_08_13_01__oss_profile_caller_policy.sql 语义等价

ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS caller_policy text;
COMMENT ON COLUMN flow_oss_upload_profile.caller_policy IS '宿主调用方策略 JSON：{"upload":CallerPolicy,"download":CallerPolicy}';
