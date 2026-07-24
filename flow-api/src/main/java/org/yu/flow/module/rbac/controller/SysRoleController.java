package org.yu.flow.module.rbac.controller;

import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.rbac.dto.SaveSysRoleDTO;
import org.yu.flow.module.rbac.dto.SysPermissionDTO;
import org.yu.flow.module.rbac.dto.SysRoleDTO;
import org.yu.flow.module.rbac.service.RbacService;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/sys-roles")
public class SysRoleController {

    @Resource
    private RbacService rbacService;

    @GetMapping
    @RequirePerm("sys:role:view")
    public R<List<SysRoleDTO>> list() {
        return R.ok(rbacService.listRolesDetail());
    }

    @GetMapping("/permissions")
    @RequirePerm({"sys:role:view", "sys:role:write"})
    public R<List<SysPermissionDTO>> permissions() {
        return R.ok(rbacService.listPermissions());
    }

    @GetMapping("/{id}")
    @RequirePerm("sys:role:view")
    public R<SysRoleDTO> detail(@PathVariable String id) {
        return R.ok(rbacService.getRole(id));
    }

    @PostMapping
    @RequirePerm("sys:role:write")
    public R<SysRoleDTO> create(@RequestBody SaveSysRoleDTO dto) {
        return R.ok(rbacService.createRole(dto));
    }

    @PutMapping("/{id}")
    @RequirePerm("sys:role:write")
    public R<SysRoleDTO> update(@PathVariable String id, @RequestBody SaveSysRoleDTO dto) {
        return R.ok(rbacService.updateRole(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("sys:role:write")
    public R<Void> delete(@PathVariable String id) {
        rbacService.deleteRole(id);
        return R.ok();
    }
}
