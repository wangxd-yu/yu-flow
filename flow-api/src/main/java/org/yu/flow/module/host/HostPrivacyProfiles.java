package org.yu.flow.module.host;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 宿主机隐私方案列表（系统配置 {@code HOST_PRIVACY_PROFILES}）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostPrivacyProfiles {

    public static final String SETTINGS_KEY = "HOST_PRIVACY_PROFILES";

    private List<HostPrivacyProfile> profiles = new ArrayList<>();
}
