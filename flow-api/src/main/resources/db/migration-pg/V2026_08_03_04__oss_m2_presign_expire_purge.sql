-- M2: 预签名下载、临时过期、软删清理状态
ALTER TABLE flow_oss_connection
  ADD COLUMN IF NOT EXISTS private_download_mode varchar(16) NOT NULL DEFAULT 'STREAM',
  ADD COLUMN IF NOT EXISTS presign_expire_seconds integer NOT NULL DEFAULT 300;

COMMENT ON COLUMN flow_oss_connection.private_download_mode IS '隐私下载：STREAM / PRESIGN';
COMMENT ON COLUMN flow_oss_connection.presign_expire_seconds IS '预签名有效期（秒）';

ALTER TABLE flow_oss_object
  ADD COLUMN IF NOT EXISTS expires_at timestamp,
  ADD COLUMN IF NOT EXISTS object_purged smallint NOT NULL DEFAULT 0;

COMMENT ON COLUMN flow_oss_object.expires_at IS '临时文件过期时间，空=不过期';
COMMENT ON COLUMN flow_oss_object.object_purged IS 'MinIO 对象是否已物理删除：0=否, 1=是';

CREATE INDEX IF NOT EXISTS idx_flow_oss_object_expires_at ON flow_oss_object (expires_at);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_purge ON flow_oss_object (status, object_purged);
