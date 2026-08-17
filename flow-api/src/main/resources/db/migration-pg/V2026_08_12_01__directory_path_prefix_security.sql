-- 目录：path_prefix / security_config / remark（PostgreSQL / 瀚高）
-- 与 db/migration/V2026_08_12_01__directory_path_prefix_security.sql 语义等价
-- 三列均可空：存量目录为 NULL 时行为与改前一致

ALTER TABLE flow_directory
    ADD COLUMN IF NOT EXISTS path_prefix VARCHAR(256) NULL,
    ADD COLUMN IF NOT EXISTS security_config TEXT NULL,
    ADD COLUMN IF NOT EXISTS remark VARCHAR(512) NULL;

COMMENT ON COLUMN flow_directory.path_prefix IS 'URL路径前缀，可空；新建接口默认继承';
COMMENT ON COLUMN flow_directory.security_config IS '目录级入站防护JSON，结构同ApiSecurityConfig';
COMMENT ON COLUMN flow_directory.remark IS '备注';
