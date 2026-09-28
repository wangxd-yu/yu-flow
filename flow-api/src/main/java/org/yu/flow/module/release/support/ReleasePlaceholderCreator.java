package org.yu.flow.module.release.support;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.alert.domain.AlertChannelDO;
import org.yu.flow.module.alert.repository.AlertChannelRepository;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.envvar.dto.SaveSysEnvVariableDTO;
import org.yu.flow.module.envvar.repository.SysEnvVariableRepository;
import org.yu.flow.module.envvar.service.SysEnvVariableService;
import org.yu.flow.module.envvar.support.EnvVarRefs;
import org.yu.flow.module.mq.domain.MqConnectionDO;
import org.yu.flow.module.mq.repository.MqConnectionRepository;
import org.yu.flow.module.oss.domain.OssConnectionDO;
import org.yu.flow.module.oss.repository.OssConnectionRepository;
import org.yu.flow.module.transfer.dto.TransferRequirementDTO;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 为发布包依赖的外部资源创建「占位」：只有编码 / 名称 / 类型，状态为停用、地址与账号留空，
 * 由运维在对应管理页补填并启用后，导入预检才视为具备。
 */
@Component
public class ReleasePlaceholderCreator {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String TO_FILL = "待配置";
    /** 连接编码：来自发布包，按保守格式校验（与连接表 code 列宽一致） */
    private static final Pattern CONNECTION_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.\\-]{0,49}$");
    private static final Pattern CHANNEL_NAME = Pattern.compile("^[^\\p{Cntrl}]{1,64}$");

    @Resource
    private DynamicDataSourceService dynamicDataSourceService;
    @Resource
    private MqConnectionRepository mqConnectionRepository;
    @Resource
    private OssConnectionRepository ossConnectionRepository;
    @Resource
    private AlertChannelRepository alertChannelRepository;
    @Resource
    private SysEnvVariableRepository sysEnvVariableRepository;
    @Resource
    private SysEnvVariableService sysEnvVariableService;

    /**
     * @return 实际创建返回 true；已存在同名资源返回 false
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean create(String kind, String key, Map<String, String> attributes, String remark) {
        if (StrUtil.isBlank(key)) {
            throw new FlowException("RELEASE_INVALID", "依赖名称不能为空");
        }
        validateKey(StrUtil.nullToEmpty(kind), key);
        Map<String, String> attrs = attributes == null ? Map.of() : attributes;
        LocalDateTime now = LocalDateTime.now(ZONE);
        return switch (StrUtil.nullToEmpty(kind)) {
            case TransferRequirementDTO.KIND_DATASOURCE -> dynamicDataSourceService.createPlaceholder(key, attrs.get("dbType"));
            case TransferRequirementDTO.KIND_MQ -> {
                if (mqConnectionRepository.existsByCode(key)) {
                    yield false;
                }
                mqConnectionRepository.save(MqConnectionDO.builder()
                        .code(key).name(key + "（" + TO_FILL + "）")
                        .mqType(StrUtil.blankToDefault(attrs.get("mqType"), "RABBITMQ"))
                        .servers(TO_FILL).enabled(false).info("发布包导入时创建的占位，请补填地址与账号后启用")
                        .createTime(now).updateTime(now).build());
                yield true;
            }
            case TransferRequirementDTO.KIND_OSS -> {
                if (ossConnectionRepository.existsByCode(key)) {
                    yield false;
                }
                ossConnectionRepository.save(OssConnectionDO.builder()
                        .code(key).name(key + "（" + TO_FILL + "）").endpoint(TO_FILL).pathStyle(true).enabled(false)
                        .info("发布包导入时创建的占位，请补填地址与密钥后启用")
                        .createTime(now).updateTime(now).build());
                yield true;
            }
            case TransferRequirementDTO.KIND_ALERT_CHANNEL -> {
                boolean exists = alertChannelRepository.findAll().stream().anyMatch(c -> key.equals(c.getName()));
                if (exists) {
                    yield false;
                }
                alertChannelRepository.save(AlertChannelDO.builder()
                        .name(key).type(StrUtil.blankToDefault(attrs.get("type"), "WEBHOOK"))
                        .configJson("{}").enabled(0).createTime(now).updateTime(now).build());
                yield true;
            }
            case TransferRequirementDTO.KIND_ENV_VAR -> {
                if (sysEnvVariableRepository.existsByCode(key)) {
                    yield false;
                }
                SaveSysEnvVariableDTO dto = new SaveSysEnvVariableDTO();
                dto.setCode(key);
                dto.setValue("");
                dto.setSecret(false);
                dto.setRemark(StrUtil.maxLength(StrUtil.blankToDefault(remark, "发布包导入时创建的占位，请填写本环境的值"), 500));
                sysEnvVariableService.create(dto);
                yield true;
            }
            default -> throw new FlowException("RELEASE_INVALID", "该依赖不支持创建占位，请在对应管理页手工配置");
        };
    }

    /** 依赖名来自发布包，格式不对的直接拒绝，请运维到对应管理页手工创建 */
    private static void validateKey(String kind, String key) {
        boolean ok = switch (kind) {
            case TransferRequirementDTO.KIND_DATASOURCE, TransferRequirementDTO.KIND_MQ, TransferRequirementDTO.KIND_OSS ->
                    CONNECTION_CODE.matcher(key).matches();
            case TransferRequirementDTO.KIND_ALERT_CHANNEL -> CHANNEL_NAME.matcher(key).matches();
            case TransferRequirementDTO.KIND_ENV_VAR -> EnvVarRefs.isValidCode(key);
            default -> true;
        };
        if (!ok) {
            throw new FlowException("RELEASE_INVALID", "依赖名称格式不支持自动创建占位，请在对应管理页手工创建");
        }
    }
}
