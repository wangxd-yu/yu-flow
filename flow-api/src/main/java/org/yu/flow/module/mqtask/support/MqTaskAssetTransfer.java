package org.yu.flow.module.mqtask.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.mqtask.repository.FlowMqTaskRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.dto.TransferItemDTO;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;
import org.yu.flow.module.transfer.support.BundleRequirementScanner;
import org.yu.flow.module.transfer.support.FlowAssetTransfer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MqTaskAssetTransfer extends FlowAssetTransfer<FlowMqTaskDO> {

    private final FlowMqTaskRepository repository;

    public MqTaskAssetTransfer(FlowMqTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return "MQ_TASK";
    }

    @Override
    protected String exportLabel() {
        return "MQ 任务";
    }

    @Override
    protected String importLabel() {
        return "MQ 任务";
    }

    @Override
    protected String updateNote() {
        return "仅覆盖草稿，线上订阅按已发布快照运行，需重新发布才生效";
    }

    @Override
    protected Class<FlowMqTaskDO> entityClass() {
        return FlowMqTaskDO.class;
    }

    @Override
    public String writePerm() {
        return "flow:mq:write";
    }

    @Override
    public List<FlowMqTaskDO> items(AssetBundle bundle) {
        return bundle.getMqTasks();
    }

    @Override
    public void setItems(AssetBundle bundle, List<FlowMqTaskDO> items) {
        bundle.setMqTasks(items);
    }

    @Override
    public Optional<FlowMqTaskDO> findById(String id) {
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
    protected void save(FlowMqTaskDO row) {
        repository.save(row);
    }

    @Override
    public String id(FlowMqTaskDO row) {
        return row.getId();
    }

    @Override
    protected String name(FlowMqTaskDO row) {
        return row.getName();
    }

    @Override
    public String directoryId(FlowMqTaskDO row) {
        return row.getDirectoryId();
    }

    @Override
    public String dslContent(FlowMqTaskDO row) {
        return row.getDslContent();
    }

    @Override
    protected Integer publishStatus(FlowMqTaskDO row) {
        return row.getPublishStatus();
    }

    @Override
    protected String publishedSnapshot(FlowMqTaskDO row) {
        return row.getPublishedSnapshot();
    }

    @Override
    protected void clearForExport(FlowMqTaskDO copy) {
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
    }

    @Override
    protected TransferItemDTO precheck(FlowMqTaskDO incoming) {
        if (StrUtil.isBlank(incoming.getConnectionCode()) || StrUtil.isBlank(incoming.getTopic())) {
            return item(incoming, TransferItemDTO.ACTION_CONFLICT, "缺少连接编码或 topic");
        }
        return null;
    }

    @Override
    protected void addRequirements(FlowMqTaskDO task, String name, Map<String, TransferRequirementDTO> acc) {
        BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_MQ, task.getConnectionCode(), name);
    }

    /** 启停是目标环境的运维状态，仅新建时取包内值 */
    @Override
    protected FlowMqTaskDO newDraft(String id, FlowMqTaskDO incoming, LocalDateTime now) {
        FlowMqTaskDO target = new FlowMqTaskDO();
        target.setId(id);
        target.setPublishStatus(0);
        target.setDeleted(0);
        target.setCreateTime(now);
        target.setEnabled(incoming.getEnabled() != null ? incoming.getEnabled() : Boolean.TRUE);
        return target;
    }

    @Override
    protected void copyDraft(FlowMqTaskDO incoming, FlowMqTaskDO target, String directoryId, LocalDateTime now) {
        target.setName(incoming.getName());
        target.setDirectoryId(directoryId);
        target.setConnectionCode(incoming.getConnectionCode());
        target.setTopic(incoming.getTopic());
        target.setConsumerGroup(incoming.getConsumerGroup());
        target.setConcurrency(incoming.getConcurrency());
        target.setRetryMax(incoming.getRetryMax());
        target.setRetryBackoffMs(incoming.getRetryBackoffMs());
        target.setDeadLetterTopic(incoming.getDeadLetterTopic());
        target.setDslContent(incoming.getDslContent());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setLogPayloadMode(incoming.getLogPayloadMode());
        target.setLogRetentionDays(incoming.getLogRetentionDays());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
    }
}
