package org.yu.flow.module.transfer.support;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.mq.repository.MqConnectionRepository;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;

import java.util.Collection;

/**
 * 导出时为外部依赖补充创建占位所需的非敏感属性（地址、账号、密钥一律不带）。
 */
@Component
public class RequirementAttributes {

    @Resource
    private DynamicDataSourceService dynamicDataSourceService;
    @Resource
    private MqConnectionRepository mqConnectionRepository;

    public void fill(Collection<TransferRequirementDTO> requirements) {
        for (TransferRequirementDTO req : requirements) {
            switch (req.getKind()) {
                case TransferRequirementDTO.KIND_DATASOURCE -> {
                    String dbType = dynamicDataSourceService.findDbTypeByCode(req.getKey());
                    if (dbType != null) {
                        req.getAttributes().put("dbType", dbType);
                    }
                }
                case TransferRequirementDTO.KIND_MQ -> mqConnectionRepository.findByCode(req.getKey())
                        .ifPresent(c -> req.getAttributes().put("mqType", c.getMqType()));
                default -> {
                }
            }
        }
    }
}
