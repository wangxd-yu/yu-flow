-- M2: 预签名下载、临时过期、软删清理状态
ALTER TABLE `flow_oss_connection`
  ADD COLUMN `private_download_mode` varchar(16) NOT NULL DEFAULT 'STREAM' COMMENT '隐私下载：STREAM / PRESIGN' AFTER `public_access_mode`,
  ADD COLUMN `presign_expire_seconds` int NOT NULL DEFAULT 300 COMMENT '预签名有效期（秒）' AFTER `private_download_mode`;

ALTER TABLE `flow_oss_object`
  ADD COLUMN `expires_at` datetime DEFAULT NULL COMMENT '临时文件过期时间，空=不过期' AFTER `status`,
  ADD COLUMN `object_purged` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'MinIO 对象是否已物理删除：0=否, 1=是' AFTER `expires_at`;

CREATE INDEX `idx_flow_oss_object_expires_at` ON `flow_oss_object` (`expires_at`);
CREATE INDEX `idx_flow_oss_object_purge` ON `flow_oss_object` (`status`, `object_purged`);
