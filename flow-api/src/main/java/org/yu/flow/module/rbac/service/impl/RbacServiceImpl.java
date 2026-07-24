package org.yu.flow.module.rbac.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.rbac.domain.*;
import org.yu.flow.module.rbac.dto.*;
import org.yu.flow.module.rbac.repository.*;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.rbac.support.PasswordPolicy;
import org.yu.flow.module.rbac.support.RbacPasswordUtil;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;

import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RbacServiceImpl implements RbacService {

    public static final String CFG_RBAC_ENABLED = "RBAC_ENABLED";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SysUserRepository userRepository;
    @Resource
    private SysRoleRepository roleRepository;
    @Resource
    private SysUserRoleRepository userRoleRepository;
    @Resource
    private SysRolePermissionRepository rolePermissionRepository;
    @Resource
    private SysPermissionRepository permissionRepository;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;
    @Resource
    private YuFlowProperties yuFlowProperties;

    @Override
    public boolean isRbacEnabled() {
        return Boolean.TRUE.equals(sysConfigCacheManager.getBoolConfig(CFG_RBAC_ENABLED, true));
    }

    @Override
    public SysUserDO authenticateDbUser(String username, String rawPassword) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(rawPassword)) {
            return null;
        }
        Optional<SysUserDO> opt = userRepository.findByUsername(username.trim());
        if (opt.isEmpty()) {
            return null;
        }
        SysUserDO user = opt.get();
        if (user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        if (!RbacPasswordUtil.matches(rawPassword, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    @Override
    public SysUserDO findEnabledUserByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return userRepository.findByUsername(username.trim())
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .orElse(null);
    }

    @Override
    public AuthMeDTO buildMe(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        Optional<SysUserDO> opt = userRepository.findByUsername(username);
        if (opt.isPresent()) {
            SysUserDO u = opt.get();
            List<String> roles = resolveRoleCodes(u.getId());
            Set<String> perms = resolvePermissions(u.getId());
            return AuthMeDTO.builder()
                    .userId(u.getId())
                    .username(u.getUsername())
                    .displayName(StrUtil.blankToDefault(u.getDisplayName(), u.getUsername()))
                    .roles(roles)
                    .permissions(new ArrayList<>(perms))
                    .legacyAdmin(false)
                    .build();
        }
        // yml 兜底账号：视为 ADMIN
        if (username.equals(yuFlowProperties.getUsername())) {
            return AuthMeDTO.builder()
                    .userId(null)
                    .username(username)
                    .displayName(username)
                    .roles(List.of("ADMIN"))
                    .permissions(List.of("*"))
                    .legacyAdmin(true)
                    .build();
        }
        return AuthMeDTO.builder()
                .username(username)
                .displayName(username)
                .roles(List.of())
                .permissions(List.of())
                .legacyAdmin(false)
                .build();
    }

    @Override
    public Set<String> resolvePermissions(String userId) {
        if (StrUtil.isBlank(userId)) {
            return Set.of();
        }
        List<String> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRoleDO::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return rolePermissionRepository.findByRoleIdIn(roleIds).stream()
                .map(SysRolePermissionDO::getPermCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<String> resolveRoleCodes(String userId) {
        if (StrUtil.isBlank(userId)) {
            return List.of();
        }
        List<String> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRoleDO::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<String, String> idToCode = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(SysRoleDO::getId, SysRoleDO::getRoleCode, (a, b) -> a));
        return roleIds.stream()
                .map(idToCode::get)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasAnyPerm(String username, String... codes) {
        if (codes == null || codes.length == 0) {
            return true;
        }
        AuthMeDTO me = buildMe(username);
        if (me == null) {
            return false;
        }
        Set<String> perms = new HashSet<>(me.getPermissions() == null ? List.of() : me.getPermissions());
        if (perms.contains("*")) {
            return true;
        }
        for (String c : codes) {
            if (perms.contains(c)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PageBean<SysUserDTO> pageUsers(SysUserQueryDTO query) {
        int page = Math.max(query.getPage() - 1, 0);
        Specification<SysUserDO> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(query.getUsername())) {
                ps.add(cb.like(root.get("username"), "%" + query.getUsername().trim() + "%"));
            }
            if (query.getStatus() != null) {
                ps.add(cb.equal(root.get("status"), query.getStatus()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<SysUserDO> result = userRepository.findAll(spec,
                PageRequest.of(page, query.getSize(), Sort.by(Sort.Direction.DESC, "createTime")));
        List<SysUserDTO> content = result.getContent().stream().map(this::toDto).collect(Collectors.toList());
        return new PageBean<>(content, result.getNumber() + 1, result.getSize(),
                result.getTotalPages(), result.getTotalElements());
    }

    @Override
    public SysUserDTO getUser(String id) {
        SysUserDO u = userRepository.findById(id)
                .orElseThrow(() -> new FlowException("RBAC_USER_NOT_FOUND", "用户不存在"));
        return toDto(u);
    }

    @Override
    @Transactional
    public SysUserDTO createUser(SaveSysUserDTO dto) {
        if (StrUtil.isBlank(dto.getUsername()) || StrUtil.isBlank(dto.getPassword())) {
            throw new FlowException("RBAC_USER_INVALID", "用户名与密码不能为空");
        }
        try {
            PasswordPolicy.validate(dto.getPassword());
        } catch (IllegalArgumentException e) {
            throw new FlowException("RBAC_PASSWORD_WEAK", e.getMessage());
        }
        String username = dto.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new FlowException("RBAC_USER_DUP", "用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SysUserDO u = SysUserDO.builder()
                .username(username)
                .passwordHash(RbacPasswordUtil.hash(dto.getPassword()))
                .displayName(StrUtil.blankToDefault(dto.getDisplayName(), username))
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .isBuiltin(0)
                .remark(dto.getRemark())
                .createTime(now)
                .updateTime(now)
                .build();
        u = userRepository.save(u);
        bindRoles(u.getId(), dto.getRoleCodes());
        return toDto(u);
    }

    @Override
    @Transactional
    public SysUserDTO updateUser(String id, SaveSysUserDTO dto) {
        SysUserDO u = userRepository.findById(id)
                .orElseThrow(() -> new FlowException("RBAC_USER_NOT_FOUND", "用户不存在"));
        if (StrUtil.isNotBlank(dto.getDisplayName())) {
            u.setDisplayName(dto.getDisplayName().trim());
        }
        if (dto.getStatus() != null) {
            u.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            u.setRemark(dto.getRemark());
        }
        if (StrUtil.isNotBlank(dto.getPassword())) {
            try {
                PasswordPolicy.validate(dto.getPassword());
            } catch (IllegalArgumentException e) {
                throw new FlowException("RBAC_PASSWORD_WEAK", e.getMessage());
            }
            u.setPasswordHash(RbacPasswordUtil.hash(dto.getPassword()));
        }
        u.setUpdateTime(LocalDateTime.now());
        userRepository.save(u);
        if (dto.getRoleCodes() != null) {
            bindRoles(u.getId(), dto.getRoleCodes());
        }
        return toDto(u);
    }

    @Override
    @Transactional
    public void changeOwnPassword(String username, String oldPassword, String newPassword) {
        if (StrUtil.isBlank(username)) {
            throw new FlowException("AUTH_UNAUTHORIZED", "未登录");
        }
        if (StrUtil.isBlank(oldPassword) || StrUtil.isBlank(newPassword)) {
            throw new FlowException("RBAC_PASSWORD_INVALID", "请填写当前密码与新密码");
        }
        if (oldPassword.equals(newPassword)) {
            throw new FlowException("RBAC_PASSWORD_SAME", "新密码不能与当前密码相同");
        }
        try {
            PasswordPolicy.validate(newPassword);
        } catch (IllegalArgumentException e) {
            throw new FlowException("RBAC_PASSWORD_WEAK", e.getMessage());
        }

        String uname = username.trim();
        boolean isYmlAdmin = uname.equals(yuFlowProperties.getUsername());
        Optional<SysUserDO> opt = userRepository.findByUsername(uname);

        if (opt.isEmpty()) {
            // 纯 yml 兜底会话：校验 yml 旧密码后落库，此后可继续在线改密
            if (!isYmlAdmin || !oldPassword.equals(yuFlowProperties.getPassword())) {
                throw new FlowException("RBAC_PASSWORD_MISMATCH", "当前密码不正确");
            }
            ensureYmlAdminUserWithPassword(uname, newPassword);
            return;
        }

        SysUserDO user = opt.get();
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new FlowException("RBAC_USER_DISABLED", "账号已停用");
        }
        boolean dbOk = RbacPasswordUtil.matches(oldPassword, user.getPasswordHash());
        // admin：库密码与 yml 密码可能不一致（引导后改过 yml / 或只用 yml 登录），两者任一匹配即可
        boolean ymlOk = isYmlAdmin && oldPassword.equals(yuFlowProperties.getPassword());
        if (!dbOk && !ymlOk) {
            throw new FlowException("RBAC_PASSWORD_MISMATCH", "当前密码不正确");
        }
        user.setPasswordHash(RbacPasswordUtil.hash(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
    }

    /** 将 yml 管理员写入 flow_sys_user 并绑定 ADMIN */
    private void ensureYmlAdminUserWithPassword(String username, String rawPassword) {
        LocalDateTime now = LocalDateTime.now();
        SysRoleDO adminRole = roleRepository.findByRoleCode("ADMIN")
                .orElseThrow(() -> new FlowException("RBAC_ROLE_MISSING", "ADMIN 角色不存在，请先完成数据库初始化"));
        SysUserDO user = SysUserDO.builder()
                .username(username)
                .passwordHash(RbacPasswordUtil.hash(rawPassword))
                .displayName(username)
                .status(1)
                .isBuiltin(1)
                .remark("由在线改密从 yu.flow 账号落库")
                .createTime(now)
                .updateTime(now)
                .build();
        user = userRepository.save(user);
        userRoleRepository.save(new SysUserRoleDO(user.getId(), adminRole.getId()));
        log.info("[RBAC] yml 管理员已通过改密落库: {}", username);
    }

    @Override
    @Transactional
    public void deleteUser(String id) {
        SysUserDO u = userRepository.findById(id)
                .orElseThrow(() -> new FlowException("RBAC_USER_NOT_FOUND", "用户不存在"));
        if (u.getIsBuiltin() != null && u.getIsBuiltin() == 1) {
            throw new FlowException("RBAC_USER_BUILTIN", "内置用户不可删除");
        }
        userRoleRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    @Override
    public List<SysRoleDTO> listRoles() {
        return roleRepository.findAll(Sort.by("roleCode")).stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                .map(r -> SysRoleDTO.builder()
                        .id(r.getId())
                        .roleCode(r.getRoleCode())
                        .roleName(r.getRoleName())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<SysRoleDTO> listRolesDetail() {
        return roleRepository.findAll(Sort.by("roleCode")).stream()
                .map(this::toRoleDto)
                .collect(Collectors.toList());
    }

    @Override
    public SysRoleDTO getRole(String id) {
        SysRoleDO r = roleRepository.findById(id)
                .orElseThrow(() -> new FlowException("RBAC_ROLE_NOT_FOUND", "角色不存在"));
        return toRoleDto(r);
    }

    @Override
    @Transactional
    public SysRoleDTO createRole(SaveSysRoleDTO dto) {
        if (StrUtil.isBlank(dto.getRoleCode()) || StrUtil.isBlank(dto.getRoleName())) {
            throw new FlowException("RBAC_ROLE_INVALID", "角色编码与名称不能为空");
        }
        String code = dto.getRoleCode().trim().toUpperCase();
        if (!code.matches("^[A-Z][A-Z0-9_]{1,62}$")) {
            throw new FlowException("RBAC_ROLE_INVALID", "角色编码须为字母开头的大写字母/数字/下划线");
        }
        if (roleRepository.existsByRoleCode(code)) {
            throw new FlowException("RBAC_ROLE_DUP", "角色编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SysRoleDO role = SysRoleDO.builder()
                .roleCode(code)
                .roleName(dto.getRoleName().trim())
                .status(dto.getStatus() == null ? 1 : dto.getStatus())
                .isBuiltin(0)
                .remark(dto.getRemark())
                .createTime(now)
                .updateTime(now)
                .build();
        role = roleRepository.save(role);
        bindPerms(role.getId(), dto.getPermCodes());
        return toRoleDto(role);
    }

    @Override
    @Transactional
    public SysRoleDTO updateRole(String id, SaveSysRoleDTO dto) {
        SysRoleDO role = roleRepository.findById(id)
                .orElseThrow(() -> new FlowException("RBAC_ROLE_NOT_FOUND", "角色不存在"));
        if (StrUtil.isNotBlank(dto.getRoleName())) {
            role.setRoleName(dto.getRoleName().trim());
        }
        if (dto.getStatus() != null) {
            role.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            role.setRemark(dto.getRemark());
        }
        role.setUpdateTime(LocalDateTime.now());
        roleRepository.save(role);
        if (dto.getPermCodes() != null) {
            bindPerms(role.getId(), dto.getPermCodes());
        }
        return toRoleDto(role);
    }

    @Override
    @Transactional
    public void deleteRole(String id) {
        SysRoleDO role = roleRepository.findById(id)
                .orElseThrow(() -> new FlowException("RBAC_ROLE_NOT_FOUND", "角色不存在"));
        if (role.getIsBuiltin() != null && role.getIsBuiltin() == 1) {
            throw new FlowException("RBAC_ROLE_BUILTIN", "内置角色不可删除");
        }
        long used = userRoleRepository.countByRoleId(id);
        if (used > 0) {
            throw new FlowException("RBAC_ROLE_IN_USE", "角色仍被 " + used + " 个用户使用，无法删除");
        }
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.deleteById(id);
    }

    @Override
    public List<SysPermissionDTO> listPermissions() {
        return permissionRepository.findAll(Sort.by("groupCode", "permCode")).stream()
                .map(p -> SysPermissionDTO.builder()
                        .id(p.getId())
                        .permCode(p.getPermCode())
                        .permName(p.getPermName())
                        .groupCode(p.getGroupCode())
                        .remark(p.getRemark())
                        .build())
                .collect(Collectors.toList());
    }

    private void bindPerms(String roleId, List<String> permCodes) {
        rolePermissionRepository.deleteByRoleId(roleId);
        if (permCodes == null || permCodes.isEmpty()) {
            return;
        }
        Set<String> valid = permissionRepository.findAll().stream()
                .map(SysPermissionDO::getPermCode)
                .collect(Collectors.toSet());
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (String code : permCodes) {
            if (StrUtil.isBlank(code)) {
                continue;
            }
            String c = code.trim();
            if (!valid.contains(c)) {
                throw new FlowException("RBAC_PERM_NOT_FOUND", "权限不存在: " + c);
            }
            uniq.add(c);
        }
        for (String c : uniq) {
            rolePermissionRepository.save(new SysRolePermissionDO(roleId, c));
        }
    }

    private SysRoleDTO toRoleDto(SysRoleDO r) {
        List<String> perms = rolePermissionRepository.findByRoleId(r.getId()).stream()
                .map(SysRolePermissionDO::getPermCode)
                .sorted()
                .collect(Collectors.toList());
        return SysRoleDTO.builder()
                .id(r.getId())
                .roleCode(r.getRoleCode())
                .roleName(r.getRoleName())
                .status(r.getStatus())
                .isBuiltin(r.getIsBuiltin())
                .remark(r.getRemark())
                .permCodes(perms)
                .permCount(perms.size())
                .createTime(r.getCreateTime() == null ? null : FMT.format(r.getCreateTime()))
                .updateTime(r.getUpdateTime() == null ? null : FMT.format(r.getUpdateTime()))
                .build();
    }

    private void bindRoles(String userId, List<String> roleCodes) {
        userRoleRepository.deleteByUserId(userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        for (String code : roleCodes) {
            if (StrUtil.isBlank(code)) {
                continue;
            }
            SysRoleDO role = roleRepository.findByRoleCode(code.trim())
                    .orElseThrow(() -> new FlowException("RBAC_ROLE_NOT_FOUND", "角色不存在: " + code));
            userRoleRepository.save(new SysUserRoleDO(userId, role.getId()));
        }
    }

    private SysUserDTO toDto(SysUserDO u) {
        SysUserDTO dto = new SysUserDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setDisplayName(u.getDisplayName());
        dto.setStatus(u.getStatus());
        dto.setIsBuiltin(u.getIsBuiltin());
        dto.setRemark(u.getRemark());
        dto.setRoleCodes(resolveRoleCodes(u.getId()));
        dto.setCreateTime(u.getCreateTime() == null ? null : FMT.format(u.getCreateTime()));
        dto.setUpdateTime(u.getUpdateTime() == null ? null : FMT.format(u.getUpdateTime()));
        return dto;
    }
}
