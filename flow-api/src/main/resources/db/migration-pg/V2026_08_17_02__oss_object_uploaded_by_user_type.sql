-- 2026-08-17：OSS 台账落库上传人 userType，与 uploaded_by 组成本人/配额匹配键
-- 与 db/migration/V2026_08_17_02__oss_object_uploaded_by_user_type.sql 语义等价

ALTER TABLE flow_oss_object
    ADD COLUMN IF NOT EXISTS uploaded_by_user_type varchar(32);
COMMENT ON COLUMN flow_oss_object.uploaded_by_user_type IS '上传人 userType：ADMIN / END_USER / OPEN_APP（宿主可扩展）';
COMMENT ON COLUMN flow_oss_object.uploaded_by IS '上传人 userId（各用户体系内主键，不带类型前缀）';

DROP INDEX IF EXISTS idx_flow_oss_object_uploaded_by;
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_uploaded_by_uploaded_by_user_type
    ON flow_oss_object (uploaded_by, uploaded_by_user_type);
