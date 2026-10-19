package org.yu.flow.module.serviceflow.support;

import org.springframework.stereotype.Component;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.transfer.dto.AssetBundle;
import org.yu.flow.module.transfer.support.FlowAssetTransfer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ServiceAssetTransfer extends FlowAssetTransfer<FlowServiceFlowDO> {

    private final FlowServiceFlowRepository repository;

    public ServiceAssetTransfer(FlowServiceFlowRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return "SERVICE";
    }

    @Override
    protected String exportLabel() {
        return "内部服务";
    }

    @Override
    protected String importLabel() {
        return "服务";
    }

    @Override
    protected String updateNote() {
        return "仅覆盖草稿，线上快照不变，需重新发布才生效";
    }

    @Override
    protected Class<FlowServiceFlowDO> entityClass() {
        return FlowServiceFlowDO.class;
    }

    @Override
    public String writePerm() {
        return "flow:service:write";
    }

    @Override
    public List<FlowServiceFlowDO> items(AssetBundle bundle) {
        return bundle.getServices();
    }

    @Override
    public void setItems(AssetBundle bundle, List<FlowServiceFlowDO> items) {
        bundle.setServices(items);
    }

    @Override
    public Optional<FlowServiceFlowDO> findById(String id) {
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
    protected void save(FlowServiceFlowDO row) {
        repository.save(row);
    }

    @Override
    public String id(FlowServiceFlowDO row) {
        return row.getId();
    }

    @Override
    protected String name(FlowServiceFlowDO row) {
        return row.getName();
    }

    @Override
    public String directoryId(FlowServiceFlowDO row) {
        return row.getDirectoryId();
    }

    @Override
    public String dslContent(FlowServiceFlowDO row) {
        return row.getDslContent();
    }

    @Override
    protected Integer publishStatus(FlowServiceFlowDO row) {
        return row.getPublishStatus();
    }

    @Override
    protected String publishedSnapshot(FlowServiceFlowDO row) {
        return row.getPublishedSnapshot();
    }

    @Override
    protected void clearForExport(FlowServiceFlowDO copy) {
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
    }

    /** 启停是目标环境的运维状态，仅新建时取包内值 */
    @Override
    protected FlowServiceFlowDO newDraft(String id, FlowServiceFlowDO incoming, LocalDateTime now) {
        FlowServiceFlowDO target = new FlowServiceFlowDO();
        target.setId(id);
        target.setPublishStatus(0);
        target.setDeleted(0);
        target.setCreateTime(now);
        target.setEnabled(incoming.getEnabled() != null ? incoming.getEnabled() : Boolean.TRUE);
        return target;
    }

    @Override
    protected void copyDraft(FlowServiceFlowDO incoming, FlowServiceFlowDO target, String directoryId, LocalDateTime now) {
        target.setName(incoming.getName());
        target.setDirectoryId(directoryId);
        target.setDslContent(incoming.getDslContent());
        target.setContract(incoming.getContract());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
    }
}
