package org.yu.flow.module.release.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.yu.flow.log.audit.service.AuditLogService;
import org.yu.flow.module.api.repository.FlowApiRepository;
import org.yu.flow.module.release.domain.FlowEnvDO;
import org.yu.flow.module.release.domain.FlowRegressionRunDO;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;
import org.yu.flow.module.release.dto.PublishGateCheckItemDTO;
import org.yu.flow.module.release.dto.PublishGateResultDTO;
import org.yu.flow.module.release.repository.FlowEnvRepository;
import org.yu.flow.module.release.repository.FlowRegressionRunRepository;
import org.yu.flow.module.release.repository.FlowRegressionSuiteRepository;
import org.yu.flow.module.release.service.PublishGateService;
import org.yu.flow.module.release.support.PublishGateException;
import org.yu.flow.module.release.support.RegressionSecurity;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.task.repository.FlowTaskRepository;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PublishGateServiceImpl implements PublishGateService {

    @Resource
    private FlowEnvRepository flowEnvRepository;

    @Resource
    private FlowRegressionSuiteRepository suiteRepository;

    @Resource
    private FlowRegressionRunRepository runRepository;

    @Resource
    private FlowApiRepository flowApiRepository;

    @Resource
    private FlowTaskRepository flowTaskRepository;

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    private AuditLogService auditLogService;

    @Override
    public PublishGateResultDTO check(String assetType, String assetId, String envCode) {
        String type = RegressionSecurity.normalizeAssetType(assetType);
        String env = RegressionSecurity.normalizeEnvCode(envCode);
        if (StrUtil.isBlank(assetId)) {
            return PublishGateResultDTO.builder()
                    .assetType(type)
                    .assetId(assetId)
                    .envCode(env)
                    .passed(false)
                    .message("assetId 不能为空")
                    .build();
        }

        List<PublishGateCheckItemDTO> checks = new ArrayList<>();

        boolean assetOk = assetExists(type, assetId);
        checks.add(PublishGateCheckItemDTO.builder()
                .code("ASSET_EXISTS")
                .name("资产存在")
                .status(assetOk ? "PASS" : "FAIL")
                .message(assetOk ? "OK" : "资产不存在")
                .build());

        Optional<FlowEnvDO> envOpt = flowEnvRepository.findByCode(env);
        boolean envOk = envOpt.isPresent() && Integer.valueOf(1).equals(envOpt.get().getEnabled());
        checks.add(PublishGateCheckItemDTO.builder()
                .code("ENV_ENABLED")
                .name("目标环境可用")
                .status(envOk ? "PASS" : "FAIL")
                .message(envOk ? envOpt.get().getName() : "环境不存在或已停用: " + env)
                .build());

        FlowEnvDO envDO = envOpt.orElse(null);
        boolean requireSuite = envDO != null && Integer.valueOf(1).equals(envDO.getRequireSuitePass());
        if (!requireSuite) {
            checks.add(PublishGateCheckItemDTO.builder()
                    .code("REGRESSION_PASS")
                    .name("回归通过")
                    .status("SKIP")
                    .message("当前环境不强制回归")
                    .build());
        } else {
            List<FlowRegressionSuiteDO> suites =
                    suiteRepository.findByAssetTypeAndAssetIdAndEnabled(type, assetId, 1);
            if (suites.isEmpty()) {
                checks.add(PublishGateCheckItemDTO.builder()
                        .code("REGRESSION_PASS")
                        .name("回归通过")
                        .status("FAIL")
                        .message("环境 " + env + " 要求回归通过，但未配置启用中的套件")
                        .build());
            } else {
                int ttl = envDO.getPassTtlHours() == null || envDO.getPassTtlHours() <= 0
                        ? 24 : envDO.getPassTtlHours();
                LocalDateTime after = LocalDateTime.now().minusHours(ttl);
                Optional<FlowRegressionRunDO> lastPass = runRepository
                        .findFirstByAssetTypeAndAssetIdAndEnvCodeAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                                type, assetId, env, "PASSED", after);
                if (lastPass.isPresent()) {
                    checks.add(PublishGateCheckItemDTO.builder()
                            .code("REGRESSION_PASS")
                            .name("回归通过")
                            .status("PASS")
                            .message("最近通过: " + lastPass.get().getFinishedAt()
                                    + "（有效 " + ttl + "h）")
                            .build());
                } else {
                    checks.add(PublishGateCheckItemDTO.builder()
                            .code("REGRESSION_PASS")
                            .name("回归通过")
                            .status("FAIL")
                            .message("环境 " + env + " 要求 " + ttl
                                    + " 小时内有 PASSED 回归；请先运行套件")
                            .build());
                }
            }
        }

        boolean passed = checks.stream().noneMatch(c -> "FAIL".equals(c.getStatus()));
        String message = passed ? "门禁通过" : "门禁未通过，请处理失败项后再发布";
        return PublishGateResultDTO.builder()
                .assetType(type)
                .assetId(assetId)
                .envCode(env)
                .passed(passed)
                .message(message)
                .checks(checks)
                .build();
    }

    @Override
    public void assertCanPublish(String assetType, String assetId, String envCode) {
        PublishGateResultDTO result = check(assetType, assetId, envCode);
        if (!result.isPassed()) {
            try {
                auditLogService.record("PUBLISH_GATE_BLOCKED", assetType, assetId,
                        "{\"env\":\"" + result.getEnvCode() + "\",\"msg\":\""
                                + StrUtil.nullToEmpty(result.getMessage()).replace("\"", "'") + "\"}");
            } catch (Exception ignored) {
            }
            throw new PublishGateException(result);
        }
    }

    private boolean assetExists(String type, String assetId) {
        return switch (type) {
            case "API" -> flowApiRepository.existsById(assetId);
            case "TASK" -> flowTaskRepository.existsById(assetId);
            case "SERVICE" -> flowServiceFlowRepository.existsById(assetId);
            default -> false;
        };
    }
}
