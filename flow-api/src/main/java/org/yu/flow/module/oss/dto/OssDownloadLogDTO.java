package org.yu.flow.module.oss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.oss.domain.OssDownloadLogDO;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class OssDownloadLogDTO {

    private String id;
    private String objectId;
    private String downloadedBy;
    private String downloadedByName;
    private String clientIp;
    private String userAgent;
    private String result;
    private String denyReason;
    private Long timeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static OssDownloadLogDTO fromDO(OssDownloadLogDO entity) {
        if (entity == null) {
            return null;
        }
        return new OssDownloadLogDTO()
                .setId(entity.getId())
                .setObjectId(entity.getObjectId())
                .setDownloadedBy(entity.getDownloadedBy())
                .setDownloadedByName(entity.getDownloadedByName())
                .setClientIp(entity.getClientIp())
                .setUserAgent(entity.getUserAgent())
                .setResult(entity.getResult())
                .setDenyReason(entity.getDenyReason())
                .setTimeMs(entity.getTimeMs())
                .setCreateTime(entity.getCreateTime());
    }
}
