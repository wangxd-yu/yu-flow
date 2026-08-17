package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.api.privacy.PrivacyMaskRule;

import java.util.ArrayList;
import java.util.List;

/**
 * 宿主机隐私方案对外视图：不回传解密密钥。
 */
@Data
@Accessors(chain = true)
public class HostPrivacyProfileViewDTO {

    private String id;
    private String name;
    private String decryptAlg;
    private String decryptMode;
    private String decryptEncoding;
    private String decryptIvMode;
    private String decryptIvFixed;
    private boolean decryptKeySet;
    private String fieldSuffix;
    private List<String> extraFields = new ArrayList<>();
    private Boolean stripSuffix;
    private List<PrivacyMaskRule> rules = new ArrayList<>();
}
