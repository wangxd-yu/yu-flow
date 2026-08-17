-- 宿主目录保留接口属于系统依赖：保留历史指标，但从业务 API 资产中独立分类。
UPDATE flow_metrics_minute
SET asset_type = 'SYSTEM'
WHERE asset_type = 'API'
  AND asset_id IN ('88060813001', '88060813002', '88060813003', '88060813004', '88060813005');

UPDATE flow_metrics_meta
SET asset_type = 'SYSTEM'
WHERE asset_type = 'API'
  AND asset_id IN ('88060813001', '88060813002', '88060813003', '88060813004', '88060813005');
