package org.yu.flow.module.alert.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.alert.domain.AlertChannelDO;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.dto.*;
import org.yu.flow.module.alert.query.AlertChannelQueryDTO;
import org.yu.flow.module.alert.query.AlertEventQueryDTO;
import org.yu.flow.module.alert.query.AlertRuleQueryDTO;

import java.util.List;

public interface AlertManageService {

    PageBean<AlertChannelDTO> pageChannels(AlertChannelQueryDTO query);

    List<AlertChannelDTO> listChannels();

    AlertChannelDO createChannel(SaveAlertChannelDTO dto);

    AlertChannelDO updateChannel(String id, SaveAlertChannelDTO dto);

    void deleteChannel(String id);

    /** 测试推送；返回是否成功 */
    boolean testChannel(String id);

    PageBean<AlertRuleDTO> pageRules(AlertRuleQueryDTO query);

    AlertRuleDO createRule(SaveAlertRuleDTO dto);

    AlertRuleDO updateRule(String id, SaveAlertRuleDTO dto);

    void deleteRule(String id);

    /** 手动执行一次规则（跳过 interval 节流） */
    void runRuleOnce(String id);

    PageBean<AlertEventDTO> pageEvents(AlertEventQueryDTO query);

    List<AlertRuleDO> listEnabledRules();
}
