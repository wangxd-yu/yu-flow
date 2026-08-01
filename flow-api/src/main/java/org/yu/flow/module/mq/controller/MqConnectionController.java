package org.yu.flow.module.mq.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.mq.domain.MqConnectionDO;
import org.yu.flow.module.mq.dto.MqConnectionDTO;
import org.yu.flow.module.mq.query.MqConnectionQueryDTO;
import org.yu.flow.module.mq.service.MqConnectionService;
import org.yu.flow.module.rbac.support.RequirePerm;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * MQ 连接配置管理 REST 控制器
 *
 * @author yu-flow
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("/flow-api/mq-connection")
@RequirePerm({"flow:mq:view", "flow:mq:write"})
public class MqConnectionController {

    @Resource
    private MqConnectionService mqConnectionService;

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    public R<MqConnectionDTO> create(@RequestBody MqConnectionDO connectionDO) {
        return R.ok(MqConnectionDTO.fromDO(mqConnectionService.save(connectionDO)));
    }

    @PutMapping("/{id}")
    public R<MqConnectionDTO> update(@PathVariable String id, @RequestBody MqConnectionDO connectionDO) {
        connectionDO.setId(id);
        return R.ok(MqConnectionDTO.fromDO(mqConnectionService.update(connectionDO)));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        mqConnectionService.delete(id);
        return R.ok();
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        mqConnectionService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<MqConnectionDTO> getById(@PathVariable String id) {
        return R.ok(MqConnectionDTO.fromDO(mqConnectionService.findById(id)));
    }

    @GetMapping("/page")
    public R<PageBean<MqConnectionDTO>> getPage(MqConnectionQueryDTO queryDTO) {
        return R.ok(mqConnectionService.findPage(queryDTO));
    }

    /** 全部启用的连接（流程节点 / MQ 任务下拉选项） */
    @GetMapping("/options")
    public R<List<MqConnectionDTO>> options() {
        return R.ok(mqConnectionService.listEnabled());
    }

    /**
     * topic 候选（MQ 任务 / 发送节点的 Topic 输入提示）。
     *
     * <p>拉取失败不阻断表单填写：降级为空列表，前端保持手工输入。</p>
     */
    @GetMapping("/{code}/topics")
    public R<List<String>> topics(@PathVariable String code,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false, defaultValue = "50") Integer limit) {
        try {
            return R.ok(mqConnectionService.listTopics(code, keyword, limit == null ? 50 : limit));
        } catch (Exception e) {
            log.warn("[MQ] 获取 topic 候选失败 code={}: {}", code, e.getMessage());
            return R.ok(List.of());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 启用 / 停用 / 测试连接
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/enable")
    public R<MqConnectionDTO> enable(@PathVariable String id) {
        return R.ok(MqConnectionDTO.fromDO(mqConnectionService.enable(id)));
    }

    @PutMapping("/{id}/disable")
    public R<MqConnectionDTO> disable(@PathVariable String id) {
        return R.ok(MqConnectionDTO.fromDO(mqConnectionService.disable(id)));
    }

    /** 按表单参数测试连接（密码留空且 id 有值时回填库中密码） */
    @PostMapping("/test")
    public R<Boolean> testConnection(@RequestBody MqConnectionDO probe) {
        try {
            mqConnectionService.testConnection(probe);
            return R.ok(true, "连接测试成功");
        } catch (Exception e) {
            return R.fail("连接测试失败：" + e.getMessage());
        }
    }

    /** 按 ID 测试已保存的连接 */
    @PostMapping("/{id}/test")
    public R<Boolean> testConnectionById(@PathVariable String id) {
        MqConnectionDO stored = mqConnectionService.findById(id);
        if (stored == null) {
            return R.fail("MQ 连接不存在: " + id);
        }
        try {
            // 密码传空走库中密码回填
            MqConnectionDO probe = MqConnectionDO.builder()
                    .id(stored.getId())
                    .code(stored.getCode())
                    .mqType(stored.getMqType())
                    .servers(stored.getServers())
                    .virtualHost(stored.getVirtualHost())
                    .username(stored.getUsername())
                    .build();
            mqConnectionService.testConnection(probe);
            return R.ok(true, "连接测试成功");
        } catch (Exception e) {
            return R.fail("连接测试失败：" + e.getMessage());
        }
    }
}
