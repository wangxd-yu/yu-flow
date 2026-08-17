package org.yu.flow.module.oss.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传场景访问规则表（存于 {@code flow_oss_upload_profile.caller_policy}）。
 */
@Data
public class OssAccessRules {

    public static final int MAX_RULES = 8;

    private List<OssAccessRule> rules = new ArrayList<>();
}
