package org.yu.flow.module.oss.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostAuthSupport;
import org.yu.flow.module.host.FlowHostScopeType;
import org.yu.flow.module.oss.dto.OssIntegrationStatusDTO;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.util.Arrays;
import java.util.List;

@YuFlowApi
@RestController
@RequestMapping("/flow-api/oss")
@RequirePerm("flow:oss:view")
public class OssIntegrationStatusController {

    @Resource
    private FlowHostAuthSupport flowHostAuthSupport;

    @GetMapping("/integration-status")
    public R<OssIntegrationStatusDTO> integrationStatus() {
        boolean builtinPrincipal = FlowHostAuthSupport.isBuiltin(flowHostAuthSupport.getPrincipalProvider());
        boolean builtinScope = FlowHostAuthSupport.isBuiltin(flowHostAuthSupport.getDataScopeProvider());

        OssIntegrationStatusDTO dto = new OssIntegrationStatusDTO()
                .setPrincipalProvider(builtinPrincipal ? "BUILTIN_JWT" : "HOST_CUSTOM")
                .setDataScopeProvider(builtinScope ? "BUILTIN_JWT" : "HOST_CUSTOM");

        if (builtinScope) {
            dto.setSupportedScopes(Arrays.asList(
                    FlowHostScopeType.ALL.name(),
                    FlowHostScopeType.SELF.name()
            ));
            dto.setHints(List.of(
                    "当前使用内置用户上下文。嵌入宿主机时请实现 <a href=\"https://doc.yu-flow.com/guide/integration/host-auth-spi\" target=\"_blank\">FlowHostPrincipalProvider / FlowHostDataScopeProvider</a>，以启用部门范围与用户列表授权。"
            ));
        } else {
            dto.setSupportedScopes(Arrays.stream(FlowHostScopeType.values())
                    .map(Enum::name)
                    .toList());
            dto.setHints(List.of("已检测到宿主自定义 FlowHost* SPI 实现。"));
        }
        return R.ok(dto);
    }
}
