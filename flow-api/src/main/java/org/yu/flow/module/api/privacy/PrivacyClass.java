package org.yu.flow.module.api.privacy;

/**
 * 当前调用方对隐私字段的处理档。角色名来自宿主机配置，代码不写死业务码。
 */
public enum PrivacyClass {
    /** 解密后按类型脱敏 */
    MASK,
    /** 解密后明文（JSON 接口再套传输 SM4） */
    REVEAL
}
