-- =====================================================================
-- ALTER flow_api_info — 新增返回包装模式字段
-- 支持三种模式: global_default / preset_template / custom (这部分逻辑交由代码中通过 strategy resolver 处理)
-- =====================================================================

ALTER TABLE `flow_api_info`
    ADD COLUMN `template_id` VARCHAR(32) NULL COMMENT '基座模板ID' AFTER `level`,
    ADD COLUMN `custom_success_wrapper` TEXT NULL COMMENT '自定义成功返回包装' AFTER `template_id`,
    ADD COLUMN `custom_page_wrapper` TEXT NULL COMMENT '自定义分页返回包装' AFTER `custom_success_wrapper`,
    ADD COLUMN `custom_fail_wrapper` TEXT NULL COMMENT '自定义失败返回包装' AFTER `custom_page_wrapper`;

-- 将现有手写包装的 API 自动迁移为 custom
UPDATE `flow_api_info`
SET `custom_success_wrapper` = `wrap_success`,
    `custom_fail_wrapper` = `wrap_error`
WHERE (`wrap_success` IS NOT NULL AND `wrap_success` != '')
   OR (`wrap_error` IS NOT NULL AND `wrap_error` != '');

-- 删除旧字段
ALTER TABLE `flow_api_info`
    DROP COLUMN `wrap_success`,
    DROP COLUMN `wrap_error`,
    DROP COLUMN `wrapper_mode`,
    DROP COLUMN `wrapper_template_id`;
