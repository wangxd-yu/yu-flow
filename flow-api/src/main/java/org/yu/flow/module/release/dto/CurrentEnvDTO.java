package org.yu.flow.module.release.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本实例环境，供前端顶栏标识与发布弹窗锁定环境。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentEnvDTO {

    private String code;

    private String name;

    /** true：部署时已配置 current-env，发布环境不可选 */
    private boolean locked;

    /** true：本实例锁定资产编辑，变更只能通过发布包导入 */
    private boolean editLocked;

    /** true：本实例配置了发布包签名密钥 */
    private boolean signingEnabled;

    /** true：本实例导入发布包必须带有效签名 */
    private boolean signatureRequired;

    public CurrentEnvDTO(String code, String name, boolean locked) {
        this(code, name, locked, false, false, false);
    }
}
