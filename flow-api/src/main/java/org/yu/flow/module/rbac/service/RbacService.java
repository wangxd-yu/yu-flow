package org.yu.flow.module.rbac.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.rbac.dto.AuthMeDTO;
import org.yu.flow.module.rbac.dto.SaveSysRoleDTO;
import org.yu.flow.module.rbac.dto.SaveSysUserDTO;
import org.yu.flow.module.rbac.dto.SysPermissionDTO;
import org.yu.flow.module.rbac.dto.SysRoleDTO;
import org.yu.flow.module.rbac.dto.SysUserDTO;
import org.yu.flow.module.rbac.dto.SysUserQueryDTO;
import org.yu.flow.module.rbac.domain.SysUserDO;

import java.util.List;
import java.util.Set;

public interface RbacService {

    boolean isRbacEnabled();

    /**
     * DB 用户登录；失败返回 null。
     */
    SysUserDO authenticateDbUser(String username, String rawPassword);

    /** 按用户名查找已启用的库用户（不含密码校验）；不存在返回 null */
    SysUserDO findEnabledUserByUsername(String username);

    AuthMeDTO buildMe(String username);

    Set<String> resolvePermissions(String userId);

    List<String> resolveRoleCodes(String userId);

    boolean hasAnyPerm(String username, String... codes);

    PageBean<SysUserDTO> pageUsers(SysUserQueryDTO query);

    SysUserDTO getUser(String id);

    SysUserDTO createUser(SaveSysUserDTO dto);

    SysUserDTO updateUser(String id, SaveSysUserDTO dto);

    void deleteUser(String id);

    /**
     * 当前登录用户修改自己的密码（需校验旧密码 + 复杂度）。
     *
     * @param username JWT 中的用户名
     */
    void changeOwnPassword(String username, String oldPassword, String newPassword);

    /** 启用角色简表（用户绑定下拉） */
    List<SysRoleDTO> listRoles();

    /** 角色管理列表（含权限摘要） */
    List<SysRoleDTO> listRolesDetail();

    SysRoleDTO getRole(String id);

    SysRoleDTO createRole(SaveSysRoleDTO dto);

    SysRoleDTO updateRole(String id, SaveSysRoleDTO dto);

    void deleteRole(String id);

    List<SysPermissionDTO> listPermissions();
}
