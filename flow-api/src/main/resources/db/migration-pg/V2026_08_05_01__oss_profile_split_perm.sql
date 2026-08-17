-- 2026-08-05 OSS 上传场景：access_perm 拆分为 upload_perm（上传权限） + download_perm（下载权限）
-- 与 db/migration/V2026_08_05_01__oss_profile_split_perm.sql 语义等价
-- 迁移策略：原 access_perm 值复制到 upload_perm，access_perm 保留列但已废弃（不再由代码读写）

ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS upload_perm varchar(128) DEFAULT NULL;
COMMENT ON COLUMN flow_oss_upload_profile.upload_perm IS '上传权限码：哪些 RBAC 权限才能调用该场景的上传 API；留空=仅 requireAuth 控制';

ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS download_perm varchar(128) DEFAULT NULL;
COMMENT ON COLUMN flow_oss_upload_profile.download_perm IS '下载权限码：哪些 RBAC 权限可突破 DataScope 访问私有文件；留空=仅 DataScope 控制';

-- 数据迁移：原 access_perm 的值迁移到 upload_perm
UPDATE flow_oss_upload_profile
SET upload_perm = access_perm
WHERE access_perm IS NOT NULL
  AND upload_perm IS NULL;
