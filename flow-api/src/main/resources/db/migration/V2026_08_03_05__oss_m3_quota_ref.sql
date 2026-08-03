-- M3: 配额字段 + 对象引用计数
ALTER TABLE `flow_oss_upload_profile`
  ADD COLUMN `quota_max_bytes` bigint DEFAULT NULL COMMENT '场景容量配额（字节），空=不限' AFTER `max_files_per_request`,
  ADD COLUMN `quota_max_files` int DEFAULT NULL COMMENT '场景文件数配额，空=不限' AFTER `quota_max_bytes`;

CREATE TABLE IF NOT EXISTS `flow_oss_object_ref` (
  `id` varchar(32) NOT NULL COMMENT '雪花ID',
  `object_id` varchar(32) NOT NULL COMMENT '台账ID',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` varchar(128) NOT NULL COMMENT '业务单据ID',
  `create_time` datetime COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_oss_object_ref_biz` (`object_id`, `biz_type`, `biz_id`),
  KEY `idx_flow_oss_object_ref_object_id` (`object_id`),
  KEY `idx_flow_oss_object_ref_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储业务引用';
