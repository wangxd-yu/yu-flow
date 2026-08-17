-- 2026-08-06 增量：OSS 预签名直传（单 PUT）场景级开关
-- 与 db/migration/V2026_08_06_01__oss_presign_upload.sql 语义等价
-- 默认 0（关闭）：存量场景保持仅走网关代理上传，需显式开启才允许客户端 PUT 直达 OSS

ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS presign_upload_enabled smallint NOT NULL DEFAULT 0;
COMMENT ON COLUMN flow_oss_upload_profile.presign_upload_enabled IS '是否开放预签名直传：0=仅网关代理上传, 1=允许客户端 PUT 直达 OSS';

-- flow_oss_object.status 追加 PENDING 语义（仅刷新列注释）
COMMENT ON COLUMN flow_oss_object.status IS 'ACTIVE / PENDING（预签名待确认）/ DELETED';
