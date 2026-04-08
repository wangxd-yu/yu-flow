package org.yu.flow.module.model.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.model.dto.FlowModelInfoDTO;
import org.yu.flow.module.model.dto.SaveModelDTO;
import org.yu.flow.module.model.dto.FieldMetaSchema;

import java.util.List;

/**
 * 数据模型信息 Service 接口
 */
public interface FlowModelInfoService {

    /**
     * 分页查询模型列表（支持按 directoryId, name, tableName 过滤）
     */
    PageBean<FlowModelInfoDTO> findPage(String directoryId, String name, String tableName, int page, int size);

    /**
     * 获取模型详情（含 fieldsSchema）
     */
    FlowModelInfoDTO findById(String id);

    /**
     * 新建模型
     */
    FlowModelInfoDO create(SaveModelDTO saveModelDTO);

    /**
     * 更新模型（含 metadata 结构更新）
     */
    FlowModelInfoDO update(String id, SaveModelDTO saveModelDTO);

    /**
     * 删除模型
     */
    void delete(String id);

    /**
     * 批量移动到指定目录
     */
    void batchMove(java.util.List<String> ids, String targetDirectoryId);

    /**
     * 从数据库表导入字段元数据
     * @param datasourceCode 数据源 code
     * @param tableName 表名称
     * @return 转换后的标准前端组件模型 List
     */
    List<FieldMetaSchema> importFromDb(String datasourceCode, String tableName);

    /**
     * 从 DDL 解析字段元数据
     * @param ddl 建表语句
     * @param dbType 数据库类型（可选，默认按 mysql -> postgresql 尝试）
     * @return 转换后的标准前端组件模型 List
     */
    List<FieldMetaSchema> importFromDdl(String ddl, String dbType);
}
