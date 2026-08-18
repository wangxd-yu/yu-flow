-- 2026-08-18：日志表对齐 flow_log_* 前缀
-- flow_mq_task_log → flow_log_mq_task
-- flow_oss_download_log → flow_log_oss_download
-- 与 db/migration/V2026_08_18_01__rename_log_tables_flow_log_prefix.sql 语义等价
-- 新环境 00_all 已是新表名，旧表不存在时跳过

DO $$
BEGIN
  IF to_regclass('flow_mq_task_log') IS NOT NULL AND to_regclass('flow_log_mq_task') IS NULL THEN
    ALTER TABLE flow_mq_task_log RENAME TO flow_log_mq_task;
  END IF;
  IF to_regclass('idx_flow_mq_task_log_create_time') IS NOT NULL THEN
    ALTER INDEX idx_flow_mq_task_log_create_time RENAME TO idx_flow_log_mq_task_create_time;
  END IF;
  IF to_regclass('idx_flow_mq_task_log_status') IS NOT NULL THEN
    ALTER INDEX idx_flow_mq_task_log_status RENAME TO idx_flow_log_mq_task_status;
  END IF;
  IF to_regclass('idx_flow_mq_task_log_task_id') IS NOT NULL THEN
    ALTER INDEX idx_flow_mq_task_log_task_id RENAME TO idx_flow_log_mq_task_task_id;
  END IF;
  IF to_regclass('idx_flow_mq_task_log_message_id') IS NOT NULL THEN
    ALTER INDEX idx_flow_mq_task_log_message_id RENAME TO idx_flow_log_mq_task_message_id;
  END IF;

  IF to_regclass('flow_oss_download_log') IS NOT NULL AND to_regclass('flow_log_oss_download') IS NULL THEN
    ALTER TABLE flow_oss_download_log RENAME TO flow_log_oss_download;
  END IF;
  IF to_regclass('idx_flow_oss_download_log_object_id') IS NOT NULL THEN
    ALTER INDEX idx_flow_oss_download_log_object_id RENAME TO idx_flow_log_oss_download_object_id;
  END IF;
  IF to_regclass('idx_flow_oss_download_log_create_time') IS NOT NULL THEN
    ALTER INDEX idx_flow_oss_download_log_create_time RENAME TO idx_flow_log_oss_download_create_time;
  END IF;
  IF to_regclass('idx_flow_oss_download_log_result') IS NOT NULL THEN
    ALTER INDEX idx_flow_oss_download_log_result RENAME TO idx_flow_log_oss_download_result;
  END IF;

  IF to_regclass('flow_log_mq_task') IS NOT NULL THEN
    COMMENT ON TABLE flow_log_mq_task IS 'MQ 任务执行日志';
  END IF;
  IF to_regclass('flow_log_oss_download') IS NOT NULL THEN
    COMMENT ON TABLE flow_log_oss_download IS 'OSS 隐私下载审计';
  END IF;
END $$;
