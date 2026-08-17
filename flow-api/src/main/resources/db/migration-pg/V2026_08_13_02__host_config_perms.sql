-- 2026-08-13 增量：宿主机配置权限（身份目录）

INSERT INTO flow_sys_permission (id, perm_code, perm_name, group_code, remark, create_time)
VALUES
('p_host_v', 'sys:host:view', '宿主机配置查看', 'sys', '平台设置 · 宿主机配置', CURRENT_TIMESTAMP),
('p_host_w', 'sys:host:write', '宿主机配置管理', 'sys', '平台设置 · 宿主机配置', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET perm_name = EXCLUDED.perm_name;

INSERT INTO flow_sys_role_permission (role_id, perm_code)
VALUES
('role_operator', 'sys:host:view'),
('role_viewer', 'sys:host:view')
ON CONFLICT (role_id, perm_code) DO NOTHING;
