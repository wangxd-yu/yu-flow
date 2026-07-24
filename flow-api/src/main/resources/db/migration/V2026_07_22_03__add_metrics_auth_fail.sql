-- 鉴权失败计数（不计入业务失败率分母）
ALTER TABLE `flow_metrics_minute`
    ADD COLUMN `auth_fail_cnt` BIGINT NOT NULL DEFAULT 0 COMMENT '鉴权失败次数（如开放平台 401/403）' AFTER `fail_cnt`;
