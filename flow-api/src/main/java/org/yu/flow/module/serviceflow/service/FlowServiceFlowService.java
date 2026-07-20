package org.yu.flow.module.serviceflow.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.dto.FlowServiceFlowDTO;
import org.yu.flow.module.serviceflow.query.FlowServiceFlowQueryDTO;

import java.util.List;

public interface FlowServiceFlowService {

    FlowServiceFlowDO save(FlowServiceFlowDO entity);

    FlowServiceFlowDO update(FlowServiceFlowDO entity);

    void delete(String id);

    void batchDelete(List<String> ids);

    FlowServiceFlowDO findById(String id);

    PageBean<FlowServiceFlowDTO> findPage(FlowServiceFlowQueryDTO queryDTO);

    FlowServiceFlowDO enable(String id);

    FlowServiceFlowDO disable(String id);

    FlowServiceFlowDO updateLogEnabled(String id, boolean logEnabled);

    /** 发布：冻结 dslContent + contract 为快照 */
    FlowServiceFlowDO publish(String id);

    /** 下线：清除快照，CALL 不可再调用 */
    FlowServiceFlowDO unpublish(String id);

    /** 回滚草稿到已发布快照 */
    FlowServiceFlowDO rollbackToPublished(String id);

    /** 重新发布（等同 publish） */
    FlowServiceFlowDO republish(String id);
}
