package org.yu.flow.module.api.host;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HostApiProbeResult {
    /** ok / fail / skip / unknown */
    private String status;
    private String apiId;
    private String path;
    private String method;
    private String message;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime checkedAt;
}
