package org.yu.flow.module.envvar.controller;

import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.envvar.dto.EnvVariableDictVO;
import org.yu.flow.module.envvar.dto.SaveSysEnvVariableDTO;
import org.yu.flow.module.envvar.dto.SysEnvVariableDTO;
import org.yu.flow.module.envvar.service.EnvVarExtractionService;
import org.yu.flow.module.envvar.service.SysEnvVariableService;
import org.yu.flow.module.rbac.support.RequirePerm;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 环境变量管理。
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/sys-env-variables")
@RequirePerm({"sys:env:view", "sys:env:write"})
public class SysEnvVariableController {

    @Resource
    private SysEnvVariableService sysEnvVariableService;

    @Resource
    private EnvVarExtractionService envVarExtractionService;

    @GetMapping
    public R<List<SysEnvVariableDTO>> list(@RequestParam(required = false) String keyword) {
        return R.ok(sysEnvVariableService.list(keyword));
    }

    /** 变量名字典（不含值）；编排编辑者需要知道有哪些变量，因此只要求登录 */
    @GetMapping("/dictionary")
    @RequirePerm({})
    public R<List<EnvVariableDictVO>> dictionary() {
        return R.ok(sysEnvVariableService.dictionary());
    }

    @PostMapping
    @RequirePerm("sys:env:write")
    public R<SysEnvVariableDTO> create(@RequestBody SaveSysEnvVariableDTO dto) {
        return R.ok(sysEnvVariableService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePerm("sys:env:write")
    public R<SysEnvVariableDTO> update(@PathVariable String id, @RequestBody SaveSysEnvVariableDTO dto) {
        return R.ok(sysEnvVariableService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("sys:env:write")
    public R<Void> delete(@PathVariable String id) {
        sysEnvVariableService.delete(id);
        return R.ok();
    }

    /** 扫描编排里写死的第三方地址，按主机归组给出建议变量名 */
    @GetMapping("/extract-candidates")
    @RequirePerm("sys:env:write")
    public R<List<EnvVarExtractionService.Candidate>> extractCandidates() {
        return R.ok(envVarExtractionService.candidates());
    }

    /** 创建变量并把相关 httpRequest 节点的 URL 前缀替换为 ${env.CODE}（只改草稿） */
    @PostMapping("/extract")
    @RequirePerm("sys:env:write")
    public R<EnvVarExtractionService.ApplyResult> extract(@RequestBody ExtractRequest body) {
        return R.ok(envVarExtractionService.apply(body.baseUrl(), body.code(), body.remark()));
    }

    public record ExtractRequest(String baseUrl, String code, String remark) {
    }
}
