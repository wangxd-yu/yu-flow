package org.yu.flow.module.release.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.yu.flow.module.release.domain.FlowReleaseDO;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReleaseDTO {

    private String id;
    private String code;
    private String name;
    private String status;
    private String remark;
    private String sourceEnv;
    private String frozenBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime frozenTime;

    private String exportedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime exportedTime;

    private String packageDigest;
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    private long itemCount;

    /** 仅详情接口返回 */
    private List<ReleaseItemDTO> items;

    public static ReleaseDTO fromDO(FlowReleaseDO r) {
        ReleaseDTO dto = new ReleaseDTO();
        dto.setId(r.getId());
        dto.setCode(r.getCode());
        dto.setName(r.getName());
        dto.setStatus(r.getStatus());
        dto.setRemark(r.getRemark());
        dto.setSourceEnv(r.getSourceEnv());
        dto.setFrozenBy(r.getFrozenBy());
        dto.setFrozenTime(r.getFrozenTime());
        dto.setExportedBy(r.getExportedBy());
        dto.setExportedTime(r.getExportedTime());
        dto.setPackageDigest(r.getPackageDigest());
        dto.setCreateBy(r.getCreateBy());
        dto.setCreateTime(r.getCreateTime());
        dto.setUpdateTime(r.getUpdateTime());
        return dto;
    }
}
