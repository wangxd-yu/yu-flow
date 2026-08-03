package org.yu.flow.module.oss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.oss.domain.OssObjectRefDO;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OssObjectRefDTO {

    private String id;
    private String objectId;
    private String bizType;
    private String bizId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static OssObjectRefDTO fromDO(OssObjectRefDO entity) {
        if (entity == null) {
            return null;
        }
        return new OssObjectRefDTO()
                .setId(entity.getId())
                .setObjectId(entity.getObjectId())
                .setBizType(entity.getBizType())
                .setBizId(entity.getBizId())
                .setCreateTime(entity.getCreateTime());
    }
}
