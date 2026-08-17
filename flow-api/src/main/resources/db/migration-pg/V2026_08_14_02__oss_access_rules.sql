-- 2026-08-14：OSS 访问规则改挂 caller_policy.rules
-- 与 db/migration/V2026_08_14_02__oss_access_rules.sql 语义等价

ALTER TABLE flow_oss_upload_profile DROP COLUMN IF EXISTS download_admin_user_types;
ALTER TABLE flow_oss_upload_profile DROP COLUMN IF EXISTS download_scope;
COMMENT ON COLUMN flow_oss_upload_profile.caller_policy IS '访问规则 JSON：{"rules":[OssAccessRule]}';
