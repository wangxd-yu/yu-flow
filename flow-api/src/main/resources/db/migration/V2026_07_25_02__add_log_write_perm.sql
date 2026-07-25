-- 权限矩阵加固：日志清理属写操作，新增 log:write 权限码
-- 背景：日志 Controller 类级仅 log:view，DELETE /clear/{id} 存在只读账号越权清日志风险
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES ('p_log_w', 'log:write', '日志清理', 'ops', '清空任务/服务日志等写操作', NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- OPERATOR 授予日志清理（ADMIN 持有 * 通配无需单独授予；VIEWER 保持只读）
INSERT INTO `flow_sys_role_permission` (`role_id`, `perm_code`)
VALUES ('role_operator', 'log:write')
ON DUPLICATE KEY UPDATE `perm_code` = VALUES(`perm_code`);
