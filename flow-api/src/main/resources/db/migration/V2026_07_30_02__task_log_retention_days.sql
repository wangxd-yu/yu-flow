-- 任务级日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数
ALTER TABLE `flow_task_info`
    ADD COLUMN `log_retention_days` int NULL COMMENT '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数' AFTER `log_enabled`;

ALTER TABLE `flow_mq_task_info`
    ADD COLUMN `log_retention_days` int NULL COMMENT '日志保留天数：NULL=跟随系统配置，0=永久保留，>0=自定义天数' AFTER `log_enabled`;
