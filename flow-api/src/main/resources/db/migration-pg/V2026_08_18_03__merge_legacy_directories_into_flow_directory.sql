-- 2026-08-18：遗留分目录并入全局 flow_directory 后删除
-- flow_page_directory → flow_directory (biz_type=page；demo_dir_001 为 api)
-- flow_model_directory → flow_directory (biz_type=model)
-- 与 db/migration/V2026_08_18_03__merge_legacy_directories_into_flow_directory.sql 语义等价
-- 新环境 00_all 已无遗留表时跳过拷贝

DO $$
BEGIN
  IF to_regclass('flow_page_directory') IS NOT NULL THEN
    INSERT INTO flow_directory (id, parent_id, name, biz_type, sort, create_time, update_time)
    SELECT id, parent_id, name,
           CASE WHEN id = 'demo_dir_001' THEN 'api' ELSE 'page' END,
           COALESCE(sort, 0), create_time, update_time
    FROM flow_page_directory p
    WHERE NOT EXISTS (SELECT 1 FROM flow_directory d WHERE d.id = p.id);
  END IF;

  IF to_regclass('flow_model_directory') IS NOT NULL THEN
    INSERT INTO flow_directory (id, parent_id, name, biz_type, sort, create_time, update_time)
    SELECT id, parent_id, name, 'model', COALESCE(sort, 0), create_time, update_time
    FROM flow_model_directory p
    WHERE NOT EXISTS (SELECT 1 FROM flow_directory d WHERE d.id = p.id);
  END IF;
END $$;

INSERT INTO flow_directory (id, parent_id, name, biz_type, sort, create_time, update_time)
SELECT 'demo_dir_001', NULL, '✨ 官方演示案例', 'api', 999, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM flow_api_info WHERE directory_id = 'demo_dir_001')
  AND NOT EXISTS (SELECT 1 FROM flow_directory WHERE id = 'demo_dir_001');

UPDATE flow_directory
SET biz_type = 'api'
WHERE id = 'demo_dir_001'
  AND (biz_type IS NULL OR btrim(biz_type) = '' OR biz_type = 'page');

DROP TABLE IF EXISTS flow_page_directory CASCADE;
DROP TABLE IF EXISTS flow_model_directory CASCADE;
