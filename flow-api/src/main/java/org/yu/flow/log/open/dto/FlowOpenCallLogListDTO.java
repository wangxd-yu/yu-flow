package org.yu.flow.log.open.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowOpenCallLogListDTO {
    private String id;
    private String platformId;
    private String appKey;
    private String apiId;
    private String method;
    private String path;
    private Integer status;
    private Long costMs;
    private String errorCode;
    private String requestId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
