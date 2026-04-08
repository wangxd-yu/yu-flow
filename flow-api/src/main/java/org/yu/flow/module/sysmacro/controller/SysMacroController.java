package org.yu.flow.module.sysmacro.controller;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.sysmacro.cache.SysMacroCacheManager;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.sysmacro.dto.SysMacroDTO;
import org.yu.flow.module.sysmacro.dto.SaveSysMacroDTO;
import org.yu.flow.module.sysmacro.query.SysMacroQueryDTO;
import org.yu.flow.module.sysmacro.service.SysMacroService;
import org.yu.flow.module.sysmacro.vo.SysMacroDictVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统全局宏定义管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/flow-api/sys-macros")
public class SysMacroController {

    @Resource
    private SysMacroService flowSysMacroService;

    @Resource
    private SysMacroCacheManager sysMacroCacheManager;

    // ========================= 公共数据接口 =========================

    /**
     * 获取宏字典列表（前端代码提示专用）
     *
     * <p>直接读取本地内存缓存，零 DB 查询、零序列化开销，
     * 仅返回前端所需的最小字段集（macroCode / macroName / macroType / returnType / macroParams）。</p>
     *
     * @return 所有启用状态的宏字典 VO 列表
     */
    @GetMapping("/dictionary")
    public R<List<SysMacroDictVO>> getDictionary() {
        List<SysMacroDictVO> dictList = sysMacroCacheManager.getAllCachedMacros()
                .values()
                .stream()
                .map(cached -> {
                    SysMacroDO macro = cached.getSysMacro();
                    return new SysMacroDictVO(
                            macro.getMacroCode(),
                            macro.getMacroName(),
                            macro.getMacroType(),
                            macro.getReturnType(),
                            macro.getMacroParams()
                    );
                })
                .collect(Collectors.toList());
        return R.ok(dictList);
    }

    // ========================= CRUD 管理接口 =========================

    /**
     * 分页查询宏定义列表
     * 支持按 macroCode、macroName、macroType、scope、status 过滤
     */
    @GetMapping("/page")
    public R<PageBean<SysMacroDTO>> getPage(SysMacroQueryDTO queryDTO) {
        return R.ok(flowSysMacroService.findPage(queryDTO));
    }

    /**
     * 获取宏定义详情
     *
     * @param id 宏定义主键
     */
    @GetMapping("/{id}")
    public R<SysMacroDTO> getById(@PathVariable String id) {
        return R.ok(flowSysMacroService.findById(id));
    }

    /**
     * 新建宏定义
     */
    @PostMapping
    public R<SysMacroDO> create(@Valid @RequestBody SaveSysMacroDTO dto) {
        return R.ok(flowSysMacroService.create(dto));
    }

    /**
     * 更新宏定义
     *
     * @param id  宏定义主键
     * @param dto 更新内容
     */
    @PutMapping("/{id}")
    public R<SysMacroDO> update(@PathVariable String id, @RequestBody SaveSysMacroDTO dto) {
        return R.ok(flowSysMacroService.update(id, dto));
    }

    /**
     * 删除宏定义
     *
     * @param id 宏定义主键
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowSysMacroService.delete(id);
        return R.ok();
    }
}

