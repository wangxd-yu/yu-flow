-- 1. 删除 service_id 字段
ALTER TABLE flow_controller DROP COLUMN service_id;

-- 2. 重命名 type 字段为 response_type，并添加注释
ALTER TABLE flow_controller CHANGE COLUMN type response_type VARCHAR(10) COMMENT '响应数据类型：PAGE(分页)、LIST(列表)、OBJECT(对象)';

-- 3. 在 method 之后新增 service_type 字段，并添加注释
ALTER TABLE flow_controller ADD COLUMN service_type VARCHAR(20) COMMENT '服务驱动类型：DB、FLOW、JSON、STRING' AFTER method;

-- 4. 修改 config 字段的类型从 text 扩容为 longtext，并添加注释
ALTER TABLE flow_controller MODIFY COLUMN config LONGTEXT COMMENT '核心逻辑配置，存储SQL、流编排JSON或静态数据';

-- 5. 新增 datasource 字段
ALTER TABLE flow_controller ADD COLUMN datasource VARCHAR(64) COMMENT '数据源' AFTER config;

-- 6. (可选) 清理废弃的 flow_service 表
-- DROP TABLE IF EXISTS flow_service;
