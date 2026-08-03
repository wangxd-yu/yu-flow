package org.yu.flow.module.oss.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class OssIntegrationStatusDTO {

    private String principalProvider;
    private String dataScopeProvider;
    private List<String> supportedScopes;
    private List<String> hints;
}
