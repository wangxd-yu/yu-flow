package org.yu.flow.module.release.dto;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import org.yu.flow.module.release.domain.FlowReleaseImportLogDO;
import org.yu.flow.module.transfer.dto.TransferReportDTO;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入记录（不含备份内容）。
 */
@Data
public class ReleaseImportLogDTO {

    private String id;
    private String releaseCode;
    private String releaseName;
    private String packageDigest;
    private String sourceEnv;
    private String targetEnv;
    private String status;
    private String summary;
    private String errorMessage;
    private String importedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime importedTime;

    private String rolledBackBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime rolledBackTime;

    /** 仅详情接口返回 */
    private TransferReportDTO report;

    /** 是否可回滚：成功且之后没有新的成功导入 */
    private boolean rollbackable;

    /** 导入提交后的运行时自检问题；为空表示自检通过 */
    private List<String> runtimeIssues = new ArrayList<>();

    public static ReleaseImportLogDTO fromDO(FlowReleaseImportLogDO d) {
        ReleaseImportLogDTO dto = new ReleaseImportLogDTO();
        dto.setId(d.getId());
        dto.setReleaseCode(d.getReleaseCode());
        dto.setReleaseName(d.getReleaseName());
        dto.setPackageDigest(d.getPackageDigest());
        dto.setSourceEnv(d.getSourceEnv());
        dto.setTargetEnv(d.getTargetEnv());
        dto.setStatus(d.getStatus());
        dto.setSummary(d.getSummary());
        dto.setErrorMessage(d.getErrorMessage());
        dto.setImportedBy(d.getImportedBy());
        dto.setImportedTime(d.getImportedTime());
        dto.setRolledBackBy(d.getRolledBackBy());
        dto.setRolledBackTime(d.getRolledBackTime());
        if (StrUtil.isNotBlank(d.getRuntimeIssues())) {
            try {
                dto.setRuntimeIssues(FlowObjectMapperUtil.flowObjectMapper()
                        .readValue(d.getRuntimeIssues(), new TypeReference<List<String>>() {
                        }));
            } catch (Exception ignored) {
                dto.getRuntimeIssues().add(d.getRuntimeIssues());
            }
        }
        return dto;
    }
}
