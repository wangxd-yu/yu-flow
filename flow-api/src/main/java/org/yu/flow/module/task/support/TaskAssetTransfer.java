package org.yu.flow.module.task.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.yu.flow.module.task.domain.FlowTaskDO;
import org.yu.flow.module.task.repository.FlowTaskRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.TransferItemDTO;
import org.yu.flow.module.transfer.support.FlowAssetTransfer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class TaskAssetTransfer extends FlowAssetTransfer<FlowTaskDO> {

    private final FlowTaskRepository repository;

    public TaskAssetTransfer(FlowTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return "TASK";
    }

    @Override
    protected String exportLabel() {
        return "定时任务";
    }

    @Override
    protected String importLabel() {
        return "任务";
    }

    @Override
    protected String updateNote() {
        return "仅覆盖草稿，线上调度按已发布快照运行，需重新发布才生效";
    }

    @Override
    protected Class<FlowTaskDO> entityClass() {
        return FlowTaskDO.class;
    }

    @Override
    public String writePerm() {
        return "flow:task:write";
    }

    @Override
    public List<FlowTaskDO> items(AssetBundle bundle) {
        return bundle.getTasks();
    }

    @Override
    public void setItems(AssetBundle bundle, List<FlowTaskDO> items) {
        bundle.setTasks(items);
    }

    @Override
    public Optional<FlowTaskDO> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    @Override
    protected long countAnyById(String id) {
        return repository.countAnyById(id);
    }

    @Override
    protected void restoreDeletedById(String id) {
        repository.restoreDeletedById(id);
    }

    @Override
    protected void save(FlowTaskDO row) {
        repository.save(row);
    }

    @Override
    public String id(FlowTaskDO row) {
        return row.getId();
    }

    @Override
    protected String name(FlowTaskDO row) {
        return row.getName();
    }

    @Override
    public String directoryId(FlowTaskDO row) {
        return row.getDirectoryId();
    }

    @Override
    public String dslContent(FlowTaskDO row) {
        return row.getDslContent();
    }

    @Override
    protected Integer publishStatus(FlowTaskDO row) {
        return row.getPublishStatus();
    }

    @Override
    protected String publishedSnapshot(FlowTaskDO row) {
        return row.getPublishedSnapshot();
    }

    @Override
    protected void clearForExport(FlowTaskDO copy) {
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
    }

    @Override
    protected TransferItemDTO precheck(FlowTaskDO incoming) {
        if (StrUtil.isBlank(incoming.getCron()) || !CronExpression.isValidExpression(incoming.getCron().trim())) {
            return item(incoming, TransferItemDTO.ACTION_CONFLICT,
                    "Cron 表达式不合法: " + StrUtil.nullToEmpty(incoming.getCron()));
        }
        return null;
    }

    /** 新建即为未发布草稿，不会被调度器注册 */
    @Override
    protected FlowTaskDO newDraft(String id, FlowTaskDO incoming, LocalDateTime now) {
        FlowTaskDO target = new FlowTaskDO();
        target.setId(id);
        target.setPublishStatus(0);
        target.setDeleted(0);
        target.setCreateTime(now);
        target.setEnabled(incoming.getEnabled() != null ? incoming.getEnabled() : Boolean.TRUE);
        return target;
    }

    @Override
    protected void copyDraft(FlowTaskDO incoming, FlowTaskDO target, String directoryId, LocalDateTime now) {
        target.setName(incoming.getName());
        target.setDirectoryId(directoryId);
        target.setCron(incoming.getCron().trim());
        target.setDslContent(incoming.getDslContent());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setLogRetentionDays(incoming.getLogRetentionDays());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
    }
}
