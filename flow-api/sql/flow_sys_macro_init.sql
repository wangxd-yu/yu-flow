-- ============================================================================
-- 系统内置宏初始化脚本
-- 说明：将原先硬编码在 FlowSystemParamsUtil 中的系统变量
--       迁入 flow_sys_macro 全局宏字典表，由 SpEL 表达式统一管理。
-- 执行方式：可重复执行（INSERT ... ON DUPLICATE KEY UPDATE）
-- ============================================================================

INSERT INTO `flow_sys_macro`
    (`macro_code`, `macro_name`, `macro_type`, `expression`, `scope`, `return_type`, `macro_params`, `status`, `remark`, `create_by`)
VALUES
    -- ======================== 时间类（VARIABLE） ========================
    ('DATE_TIME_FULL', '完整日期时间（含毫秒）', 'VARIABLE',
     'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd HH:mm:ss.SSS''))',
     'ALL', 'String', NULL, 1,
     '格式：yyyy-MM-dd HH:mm:ss.SSS', 'SYSTEM_INIT'),

    ('DATE_TIME', '标准日期时间', 'VARIABLE',
     'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd HH:mm:ss''))',
     'ALL', 'String', NULL, 1,
     '格式：yyyy-MM-dd HH:mm:ss', 'SYSTEM_INIT'),

    ('DATE', '当前日期', 'VARIABLE',
     'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''yyyy-MM-dd''))',
     'ALL', 'String', NULL, 1,
     '格式：yyyy-MM-dd', 'SYSTEM_INIT'),

    ('TIME', '当前时间', 'VARIABLE',
     'T(java.time.LocalDateTime).now().format(T(java.time.format.DateTimeFormatter).ofPattern(''HH:mm:ss''))',
     'ALL', 'String', NULL, 1,
     '格式：HH:mm:ss', 'SYSTEM_INIT'),

    -- ======================== 主键类（FUNCTION） ========================
    ('UUID', '32位UUID（无连字符）', 'FUNCTION',
     'T(cn.hutool.core.util.IdUtil).fastSimpleUUID()',
     'ALL', 'String', NULL, 1,
     '生成32位无连字符的UUID字符串', 'SYSTEM_INIT'),

    ('SNOWFLAKE', '雪花算法ID', 'FUNCTION',
     'T(util.auto.org.yu.flow.SnowIdGenerator).getId()',
     'ALL', 'String', NULL, 1,
     '基于雪花算法生成分布式唯一ID字符串', 'SYSTEM_INIT'),

    -- ======================== 环境变量（FUNCTION） ========================
    ('GET_ENV', '获取环境配置', 'FUNCTION',
     '@environment.getProperty(#p0)',
     'ALL', 'String', 'p0', 1,
     '动态获取 Spring Environment 配置项，如 spring.datasource.url。调用方式：宏编码 GET_ENV，上下文参数 {p0: "配置key"}',
     'SYSTEM_INIT')

ON DUPLICATE KEY UPDATE
    `macro_name`  = VALUES(`macro_name`),
    `macro_type`  = VALUES(`macro_type`),
    `expression`  = VALUES(`expression`),
    `scope`       = VALUES(`scope`),
    `return_type` = VALUES(`return_type`),
    `macro_params`= VALUES(`macro_params`),
    `status`      = VALUES(`status`),
    `remark`      = VALUES(`remark`),
    `update_by`   = 'SYSTEM_INIT';
