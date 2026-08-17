-- Table: flow_metrics_minute
-- 资产运行计量分钟汇总
CREATE TABLE IF NOT EXISTS flow_metrics_minute (
  id varchar(32) NOT NULL,
  asset_type varchar(16) NOT NULL,
  asset_id varchar(32) NOT NULL,
  trigger_type varchar(16) NOT NULL DEFAULT '_',
  bucket_start timestamp NOT NULL,
  success_cnt bigint NOT NULL DEFAULT 0,
  fail_cnt bigint NOT NULL DEFAULT 0,
  auth_fail_cnt bigint NOT NULL DEFAULT 0,
  skipped_cnt bigint NOT NULL DEFAULT 0,
  sum_cost_ms bigint NOT NULL DEFAULT 0,
  latency_count bigint NOT NULL DEFAULT 0,
  hist_json varchar(1024),
  update_time timestamp,
  PRIMARY KEY (id),
  CONSTRAINT uk_flow_metrics_minute_atype_aid_ttype_bstart UNIQUE (asset_type, asset_id, trigger_type, bucket_start)
);
COMMENT ON TABLE flow_metrics_minute IS '资产运行计量分钟汇总';
COMMENT ON COLUMN flow_metrics_minute.id IS '雪花ID';
COMMENT ON COLUMN flow_metrics_minute.asset_type IS 'API / TASK / MQ_TASK / SERVICE / PLATFORM / SYSTEM';
COMMENT ON COLUMN flow_metrics_minute.asset_id IS '资产ID';
COMMENT ON COLUMN flow_metrics_minute.trigger_type IS '触发类型；API 固定为 _';
COMMENT ON COLUMN flow_metrics_minute.bucket_start IS '分钟桶起点（整分）';
COMMENT ON COLUMN flow_metrics_minute.success_cnt IS '成功次数';
COMMENT ON COLUMN flow_metrics_minute.fail_cnt IS '失败次数';
COMMENT ON COLUMN flow_metrics_minute.auth_fail_cnt IS '鉴权失败次数（如开放平台 401/403）';
COMMENT ON COLUMN flow_metrics_minute.skipped_cnt IS '跳过次数';
COMMENT ON COLUMN flow_metrics_minute.sum_cost_ms IS '耗时合计（毫秒）';
COMMENT ON COLUMN flow_metrics_minute.latency_count IS '参与延迟统计的次数';
COMMENT ON COLUMN flow_metrics_minute.hist_json IS '直方图桶计数 JSON 数组';
COMMENT ON COLUMN flow_metrics_minute.update_time IS '最后更新时间';
CREATE INDEX IF NOT EXISTS idx_flow_metrics_minute_bucket_start ON flow_metrics_minute (bucket_start);
CREATE INDEX IF NOT EXISTS idx_flow_metrics_minute_asset_type_bucket_start ON flow_metrics_minute (asset_type, bucket_start);
