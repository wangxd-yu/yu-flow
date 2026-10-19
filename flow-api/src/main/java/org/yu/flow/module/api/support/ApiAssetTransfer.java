package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.host.HostCatalogReserved;
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
public class ApiAssetTransfer extends FlowAssetTransfer<FlowApiDO> {

    private final FlowApiRepository repository;

    public ApiAssetTransfer(FlowApiRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return "API";
    }

    @Override
    protected String exportLabel() {
        return "接口";
    }

    @Override
    protected String importLabel() {
        return "接口";
    }

    @Override
    protected String updateNote() {
        return "仅覆盖草稿，线上快照不变，需重新发布才生效";
    }

    @Override
    protected Class<FlowApiDO> entityClass() {
        return FlowApiDO.class;
    }

    @Override
    public String writePerm() {
        return "flow:api:write";
    }

    @Override
    public List<FlowApiDO> items(AssetBundle bundle) {
        return bundle.getApis();
    }

    @Override
    public void setItems(AssetBundle bundle, List<FlowApiDO> items) {
        bundle.setApis(items);
    }

    @Override
    public Optional<FlowApiDO> findById(String id) {
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
    protected void save(FlowApiDO row) {
        repository.save(row);
    }

    @Override
    public String id(FlowApiDO row) {
        return row.getId();
    }

    @Override
    protected String name(FlowApiDO row) {
        return row.getName();
    }

    @Override
    public String directoryId(FlowApiDO row) {
        return row.getDirectoryId();
    }

    @Override
    public String dslContent(FlowApiDO row) {
        return row.getDslContent();
    }

    @Override
    protected Integer publishStatus(FlowApiDO row) {
        return row.getPublishStatus();
    }

    @Override
    protected String publishedSnapshot(FlowApiDO row) {
        return row.getPublishedSnapshot();
    }

    @Override
    protected void clearForExport(FlowApiDO copy) {
        copy.setPublishedSnapshot(null);
        copy.setPublishStatus(0);
        copy.setPublishTime(null);
        copy.setDeleted(null);
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
    }

    @Override
    protected String exportSkipReason(FlowApiDO row) {
        return HostCatalogReserved.isReservedId(row.getId())
                ? "系统保留接口不参与迁移，已跳过: " + StrUtil.nullToEmpty(row.getName()) : null;
    }

    @Override
    protected TransferItemDTO precheck(FlowApiDO incoming) {
        if (HostCatalogReserved.isReservedId(incoming.getId())) {
            return item(incoming, TransferItemDTO.ACTION_SKIP, "系统保留接口不参与迁移");
        }
        if (StrUtil.isBlank(incoming.getUrl()) || StrUtil.isBlank(incoming.getMethod())) {
            return item(incoming, TransferItemDTO.ACTION_CONFLICT, "缺少 path 或 method");
        }
        // 同 path+method 被目标环境「其它接口」占用时无法自动决断，必须人工处理
        if (repository.existsDraftByUrlAndMethod(incoming.getUrl(), incoming.getMethod(), incoming.getId())) {
            return item(incoming, TransferItemDTO.ACTION_CONFLICT,
                    "目标环境已有其它接口占用 " + incoming.getMethod().toUpperCase() + " " + incoming.getUrl());
        }
        return null;
    }

    @Override
    protected void addRequirements(FlowApiDO api, String name, Map<String, TransferRequirementDTO> acc) {
        BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_DATASOURCE, api.getDatasource(), name);
        BundleRequirementScanner.add(acc, TransferRequirementDTO.KIND_TEMPLATE, api.getTemplateId(), name);
    }

    @Override
    protected FlowApiDO newDraft(String id, FlowApiDO incoming, LocalDateTime now) {
        FlowApiDO api = new FlowApiDO();
        api.setId(id);
        api.setPublishStatus(0);
        api.setDeleted(0);
        api.setCreateTime(now);
        return api;
    }

    @Override
    protected void copyDraft(FlowApiDO incoming, FlowApiDO target, String directoryId, LocalDateTime now) {
        target.setName(incoming.getName());
        target.setUrl(incoming.getUrl());
        target.setMethod(incoming.getMethod());
        target.setDirectoryId(directoryId);
        target.setResponseType(incoming.getResponseType());
        target.setVersion(incoming.getVersion());
        target.setServiceType(incoming.getServiceType());
        target.setInterceptMode(incoming.getInterceptMode());
        target.setHostBinding(incoming.getHostBinding());
        target.setDslContent(incoming.getDslContent());
        target.setSqlContent(incoming.getSqlContent());
        target.setJsonContent(incoming.getJsonContent());
        target.setTextContent(incoming.getTextContent());
        target.setDatasource(incoming.getDatasource());
        target.setContract(incoming.getContract());
        target.setCacheConfig(incoming.getCacheConfig());
        target.setSecurityConfig(incoming.getSecurityConfig());
        target.setViewExportConfig(incoming.getViewExportConfig());
        target.setTemplateId(incoming.getTemplateId());
        target.setCustomSuccessWrapper(incoming.getCustomSuccessWrapper());
        target.setCustomPageWrapper(incoming.getCustomPageWrapper());
        target.setCustomFailWrapper(incoming.getCustomFailWrapper());
        target.setLevel(incoming.getLevel());
        target.setLogEnabled(incoming.getLogEnabled() != null ? incoming.getLogEnabled() : Boolean.FALSE);
        target.setLogMode(incoming.getLogMode());
        target.setLogRetentionDays(incoming.getLogRetentionDays());
        target.setInfo(incoming.getInfo());
        target.setTags(incoming.getTags());
        target.setUpdateTime(now);
    }
}
