-- 2026-10-19 增量：脚本语言白名单的默认值去掉 groovy
-- 只改两种库：配置仍是旧默认值，且接口 / 服务 / 任务 / MQ 任务的草稿和发布快照里都没出现 groovy。
-- 已经在用 Groovy 的库保持原样；之后要开放 Groovy，在「系统配置」里把 groovy 加回 SCRIPT_ALLOWED_LANGUAGES。可重复执行
-- 瀚高：按版本号手工执行

UPDATE flow_sys_config
SET config_value = 'aviator,spel,javascript', update_time = CURRENT_TIMESTAMP
WHERE config_key = 'SCRIPT_ALLOWED_LANGUAGES'
  AND config_value = 'aviator,spel,javascript,groovy'
  AND NOT EXISTS (SELECT 1 FROM flow_api_info
                  WHERE LOWER(dsl_content) LIKE '%groovy%' OR LOWER(published_snapshot) LIKE '%groovy%')
  AND NOT EXISTS (SELECT 1 FROM flow_service_info
                  WHERE LOWER(dsl_content) LIKE '%groovy%' OR LOWER(published_snapshot) LIKE '%groovy%')
  AND NOT EXISTS (SELECT 1 FROM flow_task_info
                  WHERE LOWER(dsl_content) LIKE '%groovy%' OR LOWER(published_snapshot) LIKE '%groovy%')
  AND NOT EXISTS (SELECT 1 FROM flow_mq_task_info
                  WHERE LOWER(dsl_content) LIKE '%groovy%' OR LOWER(published_snapshot) LIKE '%groovy%');
