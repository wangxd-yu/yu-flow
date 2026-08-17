-- 2026-08-14 增量：OSS 上传场景下载可见范围
-- 与 db/migration/V2026_08_14_01__oss_profile_download_scope.sql 语义等价

ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS download_scope varchar(32) DEFAULT 'HOST';
ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS download_admin_user_types varchar(512);
COMMENT ON COLUMN flow_oss_upload_profile.download_scope IS '下载可见范围：OWNER_AND_ADMIN / ALL / OWNER_ONLY / HOST';
COMMENT ON COLUMN flow_oss_upload_profile.download_admin_user_types IS 'OWNER_AND_ADMIN 时看全部的宿主用户类型，逗号分隔';
