package org.yu.flow.module.transfer.support;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.TransferItemDTO;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 迁移包里一类可发布编排资产（接口 / 服务 / 定时任务 / MQ 任务）。
 *
 * <p>导出取线上内容并清掉发布状态；导入按 ID 只覆盖草稿，目标环境的发布快照与发布状态不变，
 * 同 ID 的软删除行在覆盖时恢复。各类型只提供字段与校验上的差异。</p>
 *
 * @param <T> 资产实体
 */
public abstract class FlowAssetTransfer<T> {

    /** 迁移报告里的类型编码 */
    public abstract String type();

    /** 导出警告里的名称，例如「内部服务」 */
    protected abstract String exportLabel();

    /** 导入提示里的名称，例如「服务」 */
    protected abstract String importLabel();

    /** 覆盖已存在资产时的提示 */
    protected abstract String updateNote();

    protected abstract Class<T> entityClass();

    /** 导入此类资产需要的写权限 */
    public abstract String writePerm();

    public abstract List<T> items(AssetBundle bundle);

    public abstract void setItems(AssetBundle bundle, List<T> items);

    public abstract Optional<T> findById(String id);

    public abstract boolean existsById(String id);

    protected abstract long countAnyById(String id);

    protected abstract void restoreDeletedById(String id);

    protected abstract void save(T row);

    public abstract String id(T row);

    protected abstract String name(T row);

    public abstract String directoryId(T row);

    public abstract String dslContent(T row);

    protected abstract Integer publishStatus(T row);

    protected abstract String publishedSnapshot(T row);

    /** 导出拷贝上清掉发布状态与时间戳 */
    protected abstract void clearForExport(T copy);

    /** 新建时的初始草稿：未发布、未删除，启停等运维状态按需取包内值 */
    protected abstract T newDraft(String id, T incoming, LocalDateTime now);

    /** 把包内草稿内容写到目标行 */
    protected abstract void copyDraft(T incoming, T target, String directoryId, LocalDateTime now);

    /** 导出时不参与迁移的原因（记为警告）；参与返回 null */
    protected String exportSkipReason(T row) {
        return null;
    }

    /** 导入前按类型检查，返回跳过或冲突项；通过返回 null */
    protected TransferItemDTO precheck(T incoming) {
        return null;
    }

    /** 除 DSL 之外的外部依赖（数据源、MQ 连接等） */
    protected void addRequirements(T row, String name, Map<String, TransferRequirementDTO> acc) {
    }

    /**
     * 导出一条：已发布优先时把线上快照回填成草稿内容，导出的就是测试环境验证过的那一版。
     *
     * @return 不存在或不参与迁移时返回 null，并写入警告
     */
    public T exportCopy(String id, boolean publishedFirst, List<String> warnings) {
        T row = findById(id).orElse(null);
        if (row == null) {
            warnings.add(exportLabel() + "不存在或已删除，已跳过: " + id);
            return null;
        }
        String skip = exportSkipReason(row);
        if (skip != null) {
            warnings.add(skip);
            return null;
        }
        T copy = BundleEntityCopier.detachedCopy(row, entityClass());
        String snapshot = publishedSnapshot(row);
        Integer status = publishStatus(row);
        if (publishedFirst && status != null && status == 1 && StrUtil.isNotBlank(snapshot)) {
            BundleEntityCopier.applySnapshot(copy, snapshot);
        }
        clearForExport(copy);
        return copy;
    }

    public void collectRequirements(Collection<T> rows, Map<String, TransferRequirementDTO> acc) {
        for (T row : rows) {
            String name = StrUtil.nullToEmpty(name(row));
            addRequirements(row, name, acc);
            BundleRequirementScanner.scanDsl(dslContent(row), name, acc);
        }
    }

    /**
     * 预检与导入共用的判定；dryRun 时只写报告。
     *
     * @param resolveDirectory 目标环境没有的目录换成 null（落到根目录）
     */
    public void importItem(T incoming, TransferReportDTO report, boolean overwriteExisting, boolean dryRun,
                           LocalDateTime now, UnaryOperator<String> resolveDirectory) {
        String id = id(incoming);
        String name = name(incoming);
        if (StrUtil.isBlank(id)) {
            report.getItems().add(TransferItemDTO.of(type(), null, name,
                    TransferItemDTO.ACTION_CONFLICT, "缺少 ID，包体不完整"));
            return;
        }
        TransferItemDTO early = precheck(incoming);
        if (early != null) {
            report.getItems().add(early);
            return;
        }
        T existing = findById(id).orElse(null);
        boolean softDeleted = existing == null && countAnyById(id) > 0;
        boolean occupied = existing != null || softDeleted;
        if (occupied && !overwriteExisting) {
            report.getItems().add(TransferItemDTO.of(type(), id, name, TransferItemDTO.ACTION_SKIP,
                    softDeleted ? spaced("目标环境存在同 ID 的已删除", importLabel()) + "，未开启覆盖"
                            : "目标环境已存在，未开启覆盖"));
            return;
        }
        report.getItems().add(TransferItemDTO.of(type(), id, name,
                occupied ? TransferItemDTO.ACTION_UPDATE : TransferItemDTO.ACTION_CREATE,
                softDeleted ? "恢复目标环境已删除的同 ID " + importLabel() + "并覆盖草稿"
                        : (existing != null ? updateNote() : null)));
        if (dryRun) {
            return;
        }
        if (softDeleted) {
            restoreDeletedById(id);
            existing = findById(id).orElse(null);
        }
        T target = existing != null ? existing : newDraft(id, incoming, now);
        copyDraft(incoming, target, resolveDirectory.apply(directoryId(incoming)), now);
        save(target);
    }

    /** 冲突或跳过项 */
    protected TransferItemDTO item(T incoming, String action, String message) {
        return TransferItemDTO.of(type(), id(incoming), name(incoming), action, message);
    }

    /** 中文与拉丁字母相接处补一个空格，例如「已删除」+「MQ 任务」 */
    private static String spaced(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left + right;
        }
        boolean cjkThenLatin = Character.UnicodeScript.of(left.codePointBefore(left.length())) == Character.UnicodeScript.HAN
                && Character.isLetterOrDigit(right.charAt(0)) && right.charAt(0) < 0x80;
        return cjkThenLatin ? left + " " + right : left + right;
    }
}
