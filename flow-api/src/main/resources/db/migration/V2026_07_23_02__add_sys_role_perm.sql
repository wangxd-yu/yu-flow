-- 角色管理权限点
INSERT INTO `flow_sys_permission` (`id`, `perm_code`, `perm_name`, `group_code`, `remark`, `create_time`)
VALUES
    ('p_role_v', 'sys:role:view', '角色查看', 'sys', NULL, NOW()),
    ('p_role_w', 'sys:role:write', '角色管理', 'sys', NULL, NOW())
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`);

-- ADMIN 已有 *，无需再绑；此处显式绑定便于非 * 自定义管理员角色复用
-- （无操作）
