package org.yu.flow.config.properties;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本管理与跨环境发布配置。
 *
 * <p>对应 YAML 路径：{@code yu.flow.release.*}</p>
 * <pre>
 * yu:
 *   flow:
 *     release:
 *       current-env: PROD
 * </pre>
 */
public class ReleaseProperties {

    /**
     * 本实例所属环境，取值为 {@code flow_env.code}（如 DEV / PROD）。
     * <p>配置后发布门禁、回归运行一律按本实例环境判定，忽略请求里的 envCode，
     * 并写入导出的发布包头；留空则沿用按请求选择逻辑环境的旧行为（默认 DEV）。</p>
     */
    private String currentEnv;

    /**
     * 发布包 HMAC-SHA256 签名密钥，两套环境配置同一个值（建议通过环境变量注入）。
     * <p>配置后导出的包会带签名；导入时要求签名存在且校验通过，未签名或被篡改的包一律拒收。</p>
     */
    private String signingKey;

    /**
     * 密钥轮换期间仍认可的旧签名密钥，只用于校验、不用于签名。
     * <p>轮换步骤：两套环境先把旧密钥移到这里、配置新 {@link #signingKey}，在途的旧包导入完后再删掉旧密钥。</p>
     */
    private List<String> previousSigningKeys = new ArrayList<>();

    /**
     * 导入是否必须带有效签名。留空时自动判断：本实例环境为 PROD 或开启了 {@link #lockAssetEditing} 时必须签名。
     * <p>未签名时包内摘要只能发现损坏，发现不了有意篡改（任何人都能重算摘要）。</p>
     */
    private Boolean requireSignature;

    /**
     * 保留导入前备份的成功导入记录条数（按导入时间倒序），更早记录的备份会被清空，记录本身保留。
     * <p>只有最近一次导入可以回滚，更早的备份只用于留档；≤0 表示不清理。</p>
     */
    private int backupRetention = 10;

    /**
     * 锁定资产编辑：开启后本实例不允许直接新增 / 修改 / 删除 / 发布编排资产，
     * 所有变更只能通过发布包导入（生产环境建议开启）。
     */
    private boolean lockAssetEditing = false;

    public String getCurrentEnv() {
        return currentEnv;
    }

    public void setCurrentEnv(String currentEnv) {
        this.currentEnv = currentEnv;
    }

    public String getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    public List<String> getPreviousSigningKeys() {
        return previousSigningKeys;
    }

    public void setPreviousSigningKeys(List<String> previousSigningKeys) {
        this.previousSigningKeys = previousSigningKeys;
    }

    public Boolean getRequireSignature() {
        return requireSignature;
    }

    public void setRequireSignature(Boolean requireSignature) {
        this.requireSignature = requireSignature;
    }

    public int getBackupRetention() {
        return backupRetention;
    }

    public void setBackupRetention(int backupRetention) {
        this.backupRetention = backupRetention;
    }

    public boolean isLockAssetEditing() {
        return lockAssetEditing;
    }

    public void setLockAssetEditing(boolean lockAssetEditing) {
        this.lockAssetEditing = lockAssetEditing;
    }
}
