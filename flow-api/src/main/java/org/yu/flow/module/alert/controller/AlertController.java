package org.yu.flow.module.alert.controller;

import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.alert.domain.AlertChannelDO;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.alert.dto.*;
import org.yu.flow.module.alert.query.AlertChannelQueryDTO;
import org.yu.flow.module.alert.query.AlertEventQueryDTO;
import org.yu.flow.module.alert.query.AlertRuleQueryDTO;
import org.yu.flow.module.alert.service.AlertManageService;
import org.yu.flow.module.rbac.support.RequirePerm;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/alerts")
public class AlertController {

    @Resource
    private AlertManageService alertManageService;

    // ── channels ──

    @GetMapping("/channels/page")
    @RequirePerm("flow:alert:view")
    public R<PageBean<AlertChannelDTO>> pageChannels(AlertChannelQueryDTO query) {
        return R.ok(alertManageService.pageChannels(query));
    }

    @GetMapping("/channels/list")
    @RequirePerm("flow:alert:view")
    public R<List<AlertChannelDTO>> listChannels() {
        return R.ok(alertManageService.listChannels());
    }

    @PostMapping("/channels")
    @RequirePerm("flow:alert:edit")
    public R<AlertChannelDO> createChannel(@RequestBody SaveAlertChannelDTO dto) {
        return R.ok(alertManageService.createChannel(dto));
    }

    @PutMapping("/channels/{id}")
    @RequirePerm("flow:alert:edit")
    public R<AlertChannelDO> updateChannel(@PathVariable String id, @RequestBody SaveAlertChannelDTO dto) {
        return R.ok(alertManageService.updateChannel(id, dto));
    }

    @DeleteMapping("/channels/{id}")
    @RequirePerm("flow:alert:edit")
    public R<Void> deleteChannel(@PathVariable String id) {
        alertManageService.deleteChannel(id);
        return R.ok();
    }

    @PostMapping("/channels/{id}/test")
    @RequirePerm("flow:alert:edit")
    public R<Map<String, Object>> testChannel(@PathVariable String id) {
        boolean ok = alertManageService.testChannel(id);
        return R.ok(Map.of("success", ok));
    }

    // ── rules ──

    @GetMapping("/rules/page")
    @RequirePerm("flow:alert:view")
    public R<PageBean<AlertRuleDTO>> pageRules(AlertRuleQueryDTO query) {
        return R.ok(alertManageService.pageRules(query));
    }

    @PostMapping("/rules")
    @RequirePerm("flow:alert:edit")
    public R<AlertRuleDO> createRule(@RequestBody SaveAlertRuleDTO dto) {
        return R.ok(alertManageService.createRule(dto));
    }

    @PutMapping("/rules/{id}")
    @RequirePerm("flow:alert:edit")
    public R<AlertRuleDO> updateRule(@PathVariable String id, @RequestBody SaveAlertRuleDTO dto) {
        return R.ok(alertManageService.updateRule(id, dto));
    }

    @DeleteMapping("/rules/{id}")
    @RequirePerm("flow:alert:edit")
    public R<Void> deleteRule(@PathVariable String id) {
        alertManageService.deleteRule(id);
        return R.ok();
    }

    @PostMapping("/rules/{id}/run-once")
    @RequirePerm("flow:alert:edit")
    public R<Void> runRuleOnce(@PathVariable String id) {
        alertManageService.runRuleOnce(id);
        return R.ok();
    }

    // ── events ──

    @GetMapping("/events/page")
    @RequirePerm("flow:alert:view")
    public R<PageBean<AlertEventDTO>> pageEvents(AlertEventQueryDTO query) {
        return R.ok(alertManageService.pageEvents(query));
    }
}
