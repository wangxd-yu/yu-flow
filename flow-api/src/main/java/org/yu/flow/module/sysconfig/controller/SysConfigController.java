package org.yu.flow.module.sysconfig.controller;

import org.yu.flow.annotation.YuFlowApi;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysconfig.dto.SaveSysConfigDTO;
import org.yu.flow.module.sysconfig.dto.SysConfigDTO;
import org.yu.flow.module.sysconfig.query.SysConfigQueryDTO;
import org.yu.flow.module.sysconfig.service.SysConfigService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 系统配置管理 Controller
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/sys-configs")
public class SysConfigController {

    @Resource
    private SysConfigService sysConfigService;

    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    @GetMapping("/key/{configKey}")
    public R<String> getConfigByKey(@PathVariable String configKey) {
        return R.ok(sysConfigCacheManager.getStringConfig(configKey, "/"));
    }

    @GetMapping("/page")
    @RequirePerm("sys:config:view")
    public R<PageBean<SysConfigDTO>> getPage(SysConfigQueryDTO queryDTO) {
        return R.ok(sysConfigService.findPage(queryDTO));
    }

    @GetMapping("/{id}")
    @RequirePerm("sys:config:view")
    public R<SysConfigDTO> getById(@PathVariable String id) {
        return R.ok(sysConfigService.findById(id));
    }

    @PostMapping
    @RequirePerm("sys:config:write")
    public R<SysConfigDO> create(@Valid @RequestBody SaveSysConfigDTO dto) {
        return R.ok(sysConfigService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePerm("sys:config:write")
    public R<SysConfigDO> update(@PathVariable String id, @RequestBody SaveSysConfigDTO dto) {
        return R.ok(sysConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("sys:config:write")
    public R<Void> delete(@PathVariable String id) {
        sysConfigService.delete(id);
        return R.ok();
    }
}
