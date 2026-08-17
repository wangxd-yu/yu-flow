package org.yu.flow.module.oss.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.host.FlowHostAuthSupport;
import org.yu.flow.module.host.FlowHostScopeType;
import org.yu.flow.module.host.HostPrincipalSettingsStore;
import org.yu.flow.module.oss.dto.OssIntegrationStatusDTO;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.util.Arrays;
import java.util.List;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@YuFlowApi
@ConditionalOnOssEnabled
@RestController
@RequestMapping("/flow-api/oss")
@RequirePerm("flow:oss:view")
public class OssIntegrationStatusController {

    @Resource
    private FlowHostAuthSupport flowHostAuthSupport;
    @Resource
    private HostPrincipalSettingsStore hostPrincipalSettingsStore;

    @GetMapping("/integration-status")
    public R<OssIntegrationStatusDTO> integrationStatus() {
        boolean builtinPrincipal = FlowHostAuthSupport.isBuiltin(flowHostAuthSupport.getPrincipalProvider());
        boolean builtinScope = FlowHostAuthSupport.isBuiltin(flowHostAuthSupport.getDataScopeProvider());
        boolean configured = hostPrincipalSettingsStore.load().isEnabled();

        OssIntegrationStatusDTO dto = new OssIntegrationStatusDTO()
                .setPrincipalProvider(principalMode(builtinPrincipal, configured))
                .setDataScopeProvider(principalMode(builtinScope, configured));

        if (builtinScope) {
            dto.setSupportedScopes(Arrays.asList(
                    FlowHostScopeType.ALL.name(),
                    FlowHostScopeType.SELF.name()
            ));
            dto.setHints(List.of(configured
                    ? "当前由「平台设置 → 宿主机配置 → 当前用户解析」提供宿主主体，支持全部/本人两档数据范围。需要部门或用户列表范围时，仍需实现 <a href=\"https://doc.yu-flow.com/guide/integration/host-auth-spi\" target=\"_blank\">FlowHostDataScopeProvider</a>。"
                    : "当前使用内置用户上下文。嵌入宿主机时请在「宿主机配置」里开启当前用户解析，或实现 <a href=\"https://doc.yu-flow.com/guide/integration/host-auth-spi\" target=\"_blank\">FlowHostPrincipalProvider / FlowHostDataScopeProvider</a>。"
            ));
        } else {
            dto.setSupportedScopes(Arrays.stream(FlowHostScopeType.values())
                    .map(Enum::name)
                    .toList());
            dto.setHints(List.of("已检测到宿主自定义 FlowHost* SPI 实现。"));
        }
        return R.ok(dto);
    }

    private static String principalMode(boolean builtin, boolean configured) {
        if (!builtin) {
            return "HOST_CUSTOM";
        }
        return configured ? "HOST_CONFIGURED" : "BUILTIN_JWT";
    }
}
