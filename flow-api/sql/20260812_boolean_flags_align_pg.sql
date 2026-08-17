-- 【可选·运维手工】瀚高/PG：把 Java Boolean 映射列从 smallint 改成 boolean
-- 不要进 Flyway：现网类型可能已混杂，自动跑风险大；按需在目标库执行。
-- 新环境直接用 sql-pg 全量即可（已是 boolean），无需本脚本。
--
-- 若某列已是 boolean，对应 ALTER 会报错，跳过该段即可。
-- 对照：现网已正常的 flow_task_info.enabled 即为 boolean。

-- ── MQ ──
ALTER TABLE flow_mq_task_info ALTER COLUMN enabled DROP DEFAULT;
ALTER TABLE flow_mq_task_info ALTER COLUMN enabled TYPE boolean USING (enabled::integer <> 0);
ALTER TABLE flow_mq_task_info ALTER COLUMN enabled SET DEFAULT true;
ALTER TABLE flow_mq_task_info ALTER COLUMN enabled SET NOT NULL;

ALTER TABLE flow_mq_task_info ALTER COLUMN log_enabled DROP DEFAULT;
ALTER TABLE flow_mq_task_info ALTER COLUMN log_enabled TYPE boolean USING (log_enabled::integer <> 0);
ALTER TABLE flow_mq_task_info ALTER COLUMN log_enabled SET DEFAULT true;
ALTER TABLE flow_mq_task_info ALTER COLUMN log_enabled SET NOT NULL;

ALTER TABLE flow_mq_connection ALTER COLUMN enabled DROP DEFAULT;
ALTER TABLE flow_mq_connection ALTER COLUMN enabled TYPE boolean USING (enabled::integer <> 0);
ALTER TABLE flow_mq_connection ALTER COLUMN enabled SET DEFAULT true;
ALTER TABLE flow_mq_connection ALTER COLUMN enabled SET NOT NULL;

-- ── 服务编排（若仍为 smallint）──
ALTER TABLE flow_service_info ALTER COLUMN enabled DROP DEFAULT;
ALTER TABLE flow_service_info ALTER COLUMN enabled TYPE boolean USING (enabled::integer <> 0);
ALTER TABLE flow_service_info ALTER COLUMN enabled SET DEFAULT true;
ALTER TABLE flow_service_info ALTER COLUMN enabled SET NOT NULL;

ALTER TABLE flow_service_info ALTER COLUMN log_enabled DROP DEFAULT;
ALTER TABLE flow_service_info ALTER COLUMN log_enabled TYPE boolean USING (log_enabled::integer <> 0);
ALTER TABLE flow_service_info ALTER COLUMN log_enabled SET DEFAULT true;
ALTER TABLE flow_service_info ALTER COLUMN log_enabled SET NOT NULL;

-- ── 接口 ──
ALTER TABLE flow_api_info ALTER COLUMN log_enabled DROP DEFAULT;
ALTER TABLE flow_api_info ALTER COLUMN log_enabled TYPE boolean USING (log_enabled::integer <> 0);
ALTER TABLE flow_api_info ALTER COLUMN log_enabled SET DEFAULT true;
ALTER TABLE flow_api_info ALTER COLUMN log_enabled SET NOT NULL;

-- ── OSS（按需；未用 OSS 可整段跳过）──
ALTER TABLE flow_oss_connection ALTER COLUMN enabled DROP DEFAULT;
ALTER TABLE flow_oss_connection ALTER COLUMN enabled TYPE boolean USING (enabled::integer <> 0);
ALTER TABLE flow_oss_connection ALTER COLUMN enabled SET DEFAULT true;
ALTER TABLE flow_oss_connection ALTER COLUMN enabled SET NOT NULL;

ALTER TABLE flow_oss_connection ALTER COLUMN path_style DROP DEFAULT;
ALTER TABLE flow_oss_connection ALTER COLUMN path_style TYPE boolean USING (path_style::integer <> 0);
ALTER TABLE flow_oss_connection ALTER COLUMN path_style SET DEFAULT true;
ALTER TABLE flow_oss_connection ALTER COLUMN path_style SET NOT NULL;

ALTER TABLE flow_oss_upload_profile ALTER COLUMN enabled DROP DEFAULT;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN enabled TYPE boolean USING (enabled::integer <> 0);
ALTER TABLE flow_oss_upload_profile ALTER COLUMN enabled SET DEFAULT true;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN enabled SET NOT NULL;

ALTER TABLE flow_oss_upload_profile ALTER COLUMN thumbnail_enabled DROP DEFAULT;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN thumbnail_enabled TYPE boolean USING (thumbnail_enabled::integer <> 0);
ALTER TABLE flow_oss_upload_profile ALTER COLUMN thumbnail_enabled SET DEFAULT false;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN thumbnail_enabled SET NOT NULL;

ALTER TABLE flow_oss_upload_profile ALTER COLUMN require_auth DROP DEFAULT;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN require_auth TYPE boolean USING (require_auth::integer <> 0);
ALTER TABLE flow_oss_upload_profile ALTER COLUMN require_auth SET DEFAULT true;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN require_auth SET NOT NULL;

ALTER TABLE flow_oss_upload_profile ALTER COLUMN presign_upload_enabled DROP DEFAULT;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN presign_upload_enabled TYPE boolean USING (presign_upload_enabled::integer <> 0);
ALTER TABLE flow_oss_upload_profile ALTER COLUMN presign_upload_enabled SET DEFAULT false;
ALTER TABLE flow_oss_upload_profile ALTER COLUMN presign_upload_enabled SET NOT NULL;

ALTER TABLE flow_oss_object ALTER COLUMN object_purged DROP DEFAULT;
ALTER TABLE flow_oss_object ALTER COLUMN object_purged TYPE boolean USING (object_purged::integer <> 0);
ALTER TABLE flow_oss_object ALTER COLUMN object_purged SET DEFAULT false;
ALTER TABLE flow_oss_object ALTER COLUMN object_purged SET NOT NULL;
