package org.yu.flow.module.model.controller;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.BatchMoveDTO;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.dto.FlowModelInfoDTO;
import org.yu.flow.module.model.dto.SaveModelDTO;
import org.yu.flow.module.model.dto.FieldMetaSchema;
import org.yu.flow.module.model.dto.DdlImportDTO;
import org.yu.flow.module.model.service.FlowModelInfoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import javax.annotation.Resource;

/**
 * 数据模型信息管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/flow-api/models")
public class FlowModelInfoController {

    @Resource
    private FlowModelInfoService flowModelInfoService;

    /**
     * 分页查询模型列表
     * 支持根据 directoryId, name, tableName 模糊搜索
     *
     * @param directoryId 目录ID（可选）
     * @param name        模型名称（模糊，可选）
     * @param tableName   物理表名（模糊，可选）
     * @param page        页码
     * @param size        每页条数
     */
    @GetMapping("/page")
    public R<PageBean<FlowModelInfoDTO>> getPage(
            @RequestParam(required = false) String directoryId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(flowModelInfoService.findPage(directoryId, name, tableName, page, size));
    }

    /**
     * 获取模型详情
     * 需完整返回 fields_schema JSON 用于前端表格回显
     *
     * @param id 模型主键
     */
    @GetMapping("/{id}")
    public R<FlowModelInfoDTO> getById(@PathVariable String id) {
        return R.ok(flowModelInfoService.findById(id));
    }

    /**
     * 新建模型基础信息
     */
    @PostMapping
    public R<FlowModelInfoDO> create(@RequestBody SaveModelDTO saveModelDTO) {
        return R.ok(flowModelInfoService.create(saveModelDTO));
    }

    /**
     * 批量移动
     */
    @PutMapping("/batch/moveToDir")
    public R<Void> batchMove(@RequestBody BatchMoveDTO batchMoveDTO) {
        flowModelInfoService.batchMove(batchMoveDTO.getIds(), batchMoveDTO.getTargetDirectoryId());
        return R.ok();
    }

    /**
     * 更新模型
     * 需支持同时接收和更新 fields_schema 字段配置
     */
    @PutMapping("/{id}")
    public R<FlowModelInfoDO> update(@PathVariable String id, @RequestBody SaveModelDTO saveModelDTO) {
        return R.ok(flowModelInfoService.update(id, saveModelDTO));
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        flowModelInfoService.delete(id);
        return R.ok();
    }

    /**
     * 从现存库表导入元数据
     * 根据 datasourceCode 获取数据库连接读取指定表的字段信息
     */
    @GetMapping("/import/from-db")
    public R<List<FieldMetaSchema>> importFromDb(@RequestParam(required = false) String datasourceCode,
                                                 @RequestParam String tableName) {
        if ("[DEFAULT]".equals(datasourceCode)) {
            datasourceCode = null;
        }
        return R.ok(flowModelInfoService.importFromDb(datasourceCode, tableName));
    }

    /**
     * 从 DDL 建表语句解析元数据
     */
    @PostMapping("/import/from-ddl")
    public R<List<FieldMetaSchema>> importFromDdl(@RequestBody DdlImportDTO ddlImportDTO) {
        return R.ok(flowModelInfoService.importFromDdl(ddlImportDTO.getDdl(), ddlImportDTO.getDbType()));
    }


}
