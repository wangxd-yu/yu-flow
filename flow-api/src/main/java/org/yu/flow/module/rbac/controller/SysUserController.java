package org.yu.flow.module.rbac.controller;

import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.rbac.dto.SaveSysUserDTO;
import org.yu.flow.module.rbac.dto.SysRoleDTO;
import org.yu.flow.module.rbac.dto.SysUserDTO;
import org.yu.flow.module.rbac.dto.SysUserQueryDTO;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/sys-users")
public class SysUserController {

    @Resource
    private RbacService rbacService;

    @GetMapping("/page")
    @RequirePerm("sys:user:view")
    public R<PageBean<SysUserDTO>> page(SysUserQueryDTO query) {
        return R.ok(rbacService.pageUsers(query));
    }

    @GetMapping("/{id}")
    @RequirePerm("sys:user:view")
    public R<SysUserDTO> detail(@PathVariable String id) {
        return R.ok(rbacService.getUser(id));
    }

    @GetMapping("/roles")
    @RequirePerm({"sys:user:view", "sys:user:write"})
    public R<List<SysRoleDTO>> roles() {
        return R.ok(rbacService.listRoles());
    }

    @PostMapping
    @RequirePerm("sys:user:write")
    public R<SysUserDTO> create(@RequestBody SaveSysUserDTO dto) {
        return R.ok(rbacService.createUser(dto));
    }

    @PutMapping("/{id}")
    @RequirePerm("sys:user:write")
    public R<SysUserDTO> update(@PathVariable String id, @RequestBody SaveSysUserDTO dto) {
        return R.ok(rbacService.updateUser(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("sys:user:write")
    public R<Void> delete(@PathVariable String id) {
        rbacService.deleteUser(id);
        return R.ok();
    }
}
