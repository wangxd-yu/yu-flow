-- ============================================================
-- 目录：path_prefix / security_config / remark
-- 线上注意：
-- 1) 必须先执行本脚本，再发布含新字段的后端；否则 JPA 查 flow_directory 会失败。
-- 2) 三列均可空：存量目录全为 NULL 时，入站行为与改前完全一致（接口→全局）。
-- 3) 一旦在目录上配置 security_config，其下属「接口未覆盖」的字段会即时按目录生效
--    （无需重新发布接口）——改生产目录限流/IP 前请评估影响面。
-- 4) path_prefix 仅影响新建接口默认 path，不会改写已有接口 path。
-- ============================================================

-- MySQL / MariaDB
ALTER TABLE `flow_directory`
    ADD COLUMN `path_prefix` VARCHAR(256) NULL COMMENT 'URL路径前缀，可空；新建接口默认继承' AFTER `sort`,
    ADD COLUMN `security_config` TEXT NULL COMMENT '目录级入站防护JSON，结构同ApiSecurityConfig' AFTER `path_prefix`,
    ADD COLUMN `remark` VARCHAR(512) NULL COMMENT '备注' AFTER `security_config`;
