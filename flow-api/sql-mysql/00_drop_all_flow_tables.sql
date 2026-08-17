-- Yu Flow 全量删表（验证初始化前清空用）
-- 由 scripts/gen-drop-all-flow-tables.js 生成，勿手工维护表清单
-- 用法：本文件 → 00_all_flow_tables.sql → 00_system_init.sql
-- 警告：删除全部 flow_* 业务表及数据，不可恢复

DROP TABLE IF EXISTS `flow_task_info`;
DROP TABLE IF EXISTS `flow_sys_user_role`;
DROP TABLE IF EXISTS `flow_sys_user`;
DROP TABLE IF EXISTS `flow_sys_role_permission`;
DROP TABLE IF EXISTS `flow_sys_role`;
DROP TABLE IF EXISTS `flow_sys_permission`;
DROP TABLE IF EXISTS `flow_sys_macro`;
DROP TABLE IF EXISTS `flow_sys_config`;
DROP TABLE IF EXISTS `flow_service_info`;
DROP TABLE IF EXISTS `flow_response_template`;
DROP TABLE IF EXISTS `flow_regression_suite`;
DROP TABLE IF EXISTS `flow_regression_run_case`;
DROP TABLE IF EXISTS `flow_regression_run`;
DROP TABLE IF EXISTS `flow_regression_case`;
DROP TABLE IF EXISTS `flow_page_info`;
DROP TABLE IF EXISTS `flow_page_directory`;
DROP TABLE IF EXISTS `flow_oss_upload_profile`;
DROP TABLE IF EXISTS `flow_oss_object_ref`;
DROP TABLE IF EXISTS `flow_oss_object`;
DROP TABLE IF EXISTS `flow_oss_download_log`;
DROP TABLE IF EXISTS `flow_oss_connection`;
DROP TABLE IF EXISTS `flow_open_platform`;
DROP TABLE IF EXISTS `flow_open_credential`;
DROP TABLE IF EXISTS `flow_open_api_grant`;
DROP TABLE IF EXISTS `flow_mq_task_log`;
DROP TABLE IF EXISTS `flow_mq_task_info`;
DROP TABLE IF EXISTS `flow_mq_connection`;
DROP TABLE IF EXISTS `flow_model_info`;
DROP TABLE IF EXISTS `flow_model_directory`;
DROP TABLE IF EXISTS `flow_metrics_minute`;
DROP TABLE IF EXISTS `flow_metrics_meta`;
DROP TABLE IF EXISTS `flow_log_third`;
DROP TABLE IF EXISTS `flow_log_task`;
DROP TABLE IF EXISTS `flow_log_service`;
DROP TABLE IF EXISTS `flow_log_open_call`;
DROP TABLE IF EXISTS `flow_log_login`;
DROP TABLE IF EXISTS `flow_log_execution`;
DROP TABLE IF EXISTS `flow_log_audit`;
DROP TABLE IF EXISTS `flow_env`;
DROP TABLE IF EXISTS `flow_directory`;
DROP TABLE IF EXISTS `flow_datasource`;
DROP TABLE IF EXISTS `flow_asset_version`;
DROP TABLE IF EXISTS `flow_api_info`;
DROP TABLE IF EXISTS `flow_api_excel_template`;
DROP TABLE IF EXISTS `flow_alert_rule`;
DROP TABLE IF EXISTS `flow_alert_event`;
DROP TABLE IF EXISTS `flow_alert_channel`;
