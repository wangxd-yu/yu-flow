package org.yu.flow.module.oss.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class OssConnectionTestResultDTO {

    private boolean success;
    private String message;
    private Map<String, OssBucketStatusDTO> buckets = new LinkedHashMap<>();

    @Data
    @Accessors(chain = true)
    public static class OssBucketStatusDTO {
        private String bucket;
        private boolean exists;
        private String policySummary;
    }
}
