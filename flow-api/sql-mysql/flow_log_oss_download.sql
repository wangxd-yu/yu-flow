-- Table: flow_log_oss_download
-- OSS 隐私下载审计
CREATE TABLE IF NOT EXISTS `flow_log_oss_download` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `object_id` varchar(32) COMMENT '台账 ID',
  `downloaded_by` varchar(64) COMMENT '下载人 userId',
  `downloaded_by_name` varchar(128) COMMENT '下载人展示名',
  `client_ip` varchar(64) COMMENT '客户端 IP',
  `user_agent` varchar(512) COMMENT 'User-Agent',
  `result` varchar(16) COMMENT 'SUCCESS / DENIED / NOT_FOUND / ERROR',
  `deny_reason` varchar(512) COMMENT '拒绝原因',
  `time_ms` bigint COMMENT '耗时毫秒',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_log_oss_download_object_id` (`object_id`),
  KEY `idx_flow_log_oss_download_create_time` (`create_time`),
  KEY `idx_flow_log_oss_download_result` (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS 隐私下载审计';
