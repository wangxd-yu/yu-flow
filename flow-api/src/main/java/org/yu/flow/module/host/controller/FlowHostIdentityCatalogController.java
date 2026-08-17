package org.yu.flow.module.host.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostCatalogDimension;
import org.yu.flow.module.host.FlowHostIdentityCatalogService;
import org.yu.flow.module.host.dto.HostIdentityCatalogDTO;
import org.yu.flow.module.host.dto.HostIdentityCatalogItemDTO;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.util.List;

/**
 * 管理端调用方策略下拉：读可选的宿主身份目录 SPI。
 */
@YuFlowApi
@RestController
@RequestMapping("/flow-api/host/identity-catalog")
@RequirePerm({
        "flow:api:view", "flow:api:write",
        "flow:oss:view", "flow:oss:write",
        "sys:host:view", "sys:host:write"
})
public class FlowHostIdentityCatalogController {

    @Resource
    private FlowHostIdentityCatalogService flowHostIdentityCatalogService;

    @GetMapping
    public R<HostIdentityCatalogDTO> snapshot() {
        return R.ok(flowHostIdentityCatalogService.snapshot());
    }

    @GetMapping("/items")
    public R<List<HostIdentityCatalogItemDTO>> items(
            @RequestParam("dimension") String dimension,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return R.ok(flowHostIdentityCatalogService.search(
                FlowHostCatalogDimension.parse(dimension), keyword, limit));
    }
}
