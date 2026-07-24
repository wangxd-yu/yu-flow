ALTER TABLE `flow_open_platform`
    ADD COLUMN `rate_limit_qps` INT NULL COMMENT '平台级 QPS 上限，空=不限' AFTER `open_call_log_enabled`;
