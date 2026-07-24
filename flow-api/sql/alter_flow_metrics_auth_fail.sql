-- 手工执行：为已有库增加鉴权失败计数列
ALTER TABLE `flow_metrics_minute`
    ADD COLUMN `auth_fail_cnt` BIGINT NOT NULL DEFAULT 0 COMMENT '鉴权失败次数（不计入业务失败率）' AFTER `fail_cnt`;
