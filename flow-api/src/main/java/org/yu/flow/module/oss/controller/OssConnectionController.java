package org.yu.flow.module.oss.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.dto.OssConnectionDTO;
import org.yu.flow.module.oss.dto.OssConnectionTestResultDTO;
import org.yu.flow.module.oss.query.OssConnectionQueryDTO;
import org.yu.flow.module.oss.service.OssConnectionService;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.util.List;

@Slf4j
@YuFlowApi
@RestController
@RequestMapping("/flow-api/oss/connections")
@RequirePerm({"flow:oss:view", "flow:oss:write"})
public class OssConnectionController {

    @Resource
    private OssConnectionService ossConnectionService;

    @PostMapping
    public R<OssConnectionDTO> create(@RequestBody OssConnectionDO connectionDO) {
        return R.ok(OssConnectionDTO.fromDO(ossConnectionService.save(connectionDO)));
    }

    @PutMapping("/{id}")
    public R<OssConnectionDTO> update(@PathVariable String id, @RequestBody OssConnectionDO connectionDO) {
        connectionDO.setId(id);
        return R.ok(OssConnectionDTO.fromDO(ossConnectionService.update(connectionDO)));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        ossConnectionService.delete(id);
        return R.ok();
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        ossConnectionService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<OssConnectionDTO> getById(@PathVariable String id) {
        return R.ok(OssConnectionDTO.fromDO(ossConnectionService.findById(id)));
    }

    @GetMapping("/page")
    public R<PageBean<OssConnectionDTO>> getPage(OssConnectionQueryDTO queryDTO) {
        return R.ok(ossConnectionService.findPage(queryDTO));
    }

    @GetMapping("/options")
    public R<List<OssConnectionDTO>> options() {
        return R.ok(ossConnectionService.listEnabled());
    }

    @PutMapping("/{id}/enable")
    public R<OssConnectionDTO> enable(@PathVariable String id) {
        return R.ok(OssConnectionDTO.fromDO(ossConnectionService.enable(id)));
    }

    @PutMapping("/{id}/disable")
    public R<OssConnectionDTO> disable(@PathVariable String id) {
        return R.ok(OssConnectionDTO.fromDO(ossConnectionService.disable(id)));
    }

    @PostMapping("/test")
    public R<OssConnectionTestResultDTO> testConnection(@RequestBody OssConnectionDO probe) {
        try {
            return R.ok(ossConnectionService.testConnection(probe), "连接测试成功");
        } catch (Exception e) {
            return R.fail("连接测试失败：" + e.getMessage());
        }
    }

    @PostMapping("/code/{code}/test")
    public R<OssConnectionTestResultDTO> testConnectionByCode(@PathVariable String code) {
        OssConnectionDO stored = ossConnectionService.findByCode(code);
        if (stored == null) {
            return R.fail("OSS 连接不存在: " + code);
        }
        return testConnectionById(stored.getId());
    }

    @PostMapping("/{id}/test")
    public R<OssConnectionTestResultDTO> testConnectionById(@PathVariable String id) {
        OssConnectionDO stored = ossConnectionService.findById(id);
        if (stored == null) {
            return R.fail("OSS 连接不存在: " + id);
        }
        try {
            OssConnectionDO probe = OssConnectionDO.builder()
                    .id(stored.getId())
                    .code(stored.getCode())
                    .endpoint(stored.getEndpoint())
                    .accessKey(stored.getAccessKey())
                    .publicBucket(stored.getPublicBucket())
                    .privateBucket(stored.getPrivateBucket())
                    .region(stored.getRegion())
                    .pathStyle(stored.getPathStyle())
                    .build();
            return R.ok(ossConnectionService.testConnection(probe), "连接测试成功");
        } catch (Exception e) {
            return R.fail("连接测试失败：" + e.getMessage());
        }
    }
}
