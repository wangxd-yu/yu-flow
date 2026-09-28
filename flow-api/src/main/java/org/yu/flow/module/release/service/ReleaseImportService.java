package org.yu.flow.module.release.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.release.dto.ReleaseImportLogDTO;
import org.yu.flow.module.release.dto.ReleaseInspectResultDTO;
import org.yu.flow.module.release.support.ReleaseDiffCalculator;

import java.util.List;
import java.util.Map;

/**
 * 目标环境导入发布包：预检 → 备份 → 整批写入并发布 → 记录；支持回滚到导入前。
 */
public interface ReleaseImportService {

    /** 只算不写 */
    ReleaseInspectResultDTO inspect(byte[] packageContent);

    /**
     * 导入即发布，整批一个事务，任一步失败全部回滚。
     *
     * @param confirmCode         运维手工输入的版本号，须与包内一致（防误操作）
     * @param privilegedConfirmed 运维已逐项核对预检列出的高权限变更（全局宏、开放平台授权）；包含这类变更时必须为 true
     */
    ReleaseImportLogDTO execute(byte[] packageContent, String confirmCode, boolean privilegedConfirmed);

    default ReleaseImportLogDTO execute(byte[] packageContent, String confirmCode) {
        return execute(packageContent, confirmCode, false);
    }

    PageBean<ReleaseImportLogDTO> page(String keyword, String status, int page, int size);

    ReleaseImportLogDTO get(String id);

    /** 回滚到导入前；只允许最近一次成功导入 */
    ReleaseImportLogDTO rollback(String id);

    /** 为缺失的外部依赖创建停用状态的占位，返回是否实际创建 */
    boolean createPlaceholder(String kind, String key, Map<String, String> attributes, String remark);

    /** 预检过的发布包中某个资产与本环境当前内容的逐字段差异 */
    List<ReleaseDiffCalculator.FieldDiff> diff(String digest, String assetType, String key);
}
