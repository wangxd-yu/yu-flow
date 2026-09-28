package org.yu.flow.module.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;

import java.util.ArrayList;
import java.util.List;

/**
 * 开放平台及其接口授权。按平台 code 匹配目标环境；AppKey / Secret 不随包迁移，由目标环境自行签发。
 */
@Data
public class BundleOpenPlatform {

    private FlowOpenPlatformDO platform;

    private List<Grant> grants = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Grant {
        private String apiId;
        /** 空表示跟随接口 method */
        private String allowMethods;
    }
}
