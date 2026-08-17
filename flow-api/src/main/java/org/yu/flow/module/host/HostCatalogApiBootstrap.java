package org.yu.flow.module.host;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.yu.flow.module.rbac.domain.SysPermissionDO;
import org.yu.flow.module.rbac.domain.SysRoleDO;
import org.yu.flow.module.rbac.domain.SysRolePermissionDO;
import org.yu.flow.module.rbac.repository.SysPermissionRepository;
import org.yu.flow.module.rbac.repository.SysRolePermissionRepository;
import org.yu.flow.module.rbac.repository.SysRoleRepository;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 启动时补齐保留接口（5 条身份目录 + 1 条当前用户解析）与宿主机配置权限（幂等）。
 * <p>保留接口用 JDBC 按 bigint 主键插入，避免 JPA {@code persist/merge} 预置 ID 失败。</p>
 */
@Slf4j
@Component
@Order(60)
public class HostCatalogApiBootstrap implements ApplicationRunner {

    public static final String PERM_VIEW = "sys:host:view";
    public static final String PERM_WRITE = "sys:host:write";
    static final String PERM_VIEW_ID = "p_host_v";
    static final String PERM_WRITE_ID = "p_host_w";

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SECURITY_JSON = "{\"authMode\":\"HOST\"}";
    private static final String INFO = "系统保留：宿主身份目录。请从「平台设置 → 宿主机配置」进入编排。";
    private static final String INFO_PRINCIPAL =
            "系统保留：宿主当前用户解析。入参为转发的请求头/凭证，返回一行主体字段。"
                    + "请从「平台设置 → 宿主机配置」进入编排。";
    private final AtomicBoolean reservedApisReady = new AtomicBoolean(false);

    @PersistenceContext
    private EntityManager entityManager;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private SysPermissionRepository sysPermissionRepository;
    @Resource
    private SysRoleRepository sysRoleRepository;
    @Resource
    private SysRolePermissionRepository sysRolePermissionRepository;
    @Resource
    private HostIdentityCatalogSettingsStore settingsStore;
    @Resource
    private HostPrincipalSettingsStore principalSettingsStore;

    @Override
    public void run(ApplicationArguments args) {
        runIsolated("权限", this::seedPermissions);
        if (runIsolated("保留接口", this::seedReservedApis)) {
            reservedApisReady.set(true);
        }
        runIsolated("目录绑定", () -> {
            if (!settingsStore.exists()) {
                settingsStore.save(new HostIdentityCatalogSettings());
            }
        });
        runIsolated("主体解析", () -> {
            if (!principalSettingsStore.exists()) {
                principalSettingsStore.save(new HostPrincipalSettings());
            }
        });
    }

    /** 打开详情 / 保存前补齐缺失的保留接口。 */
    public synchronized void ensureReservedApis() {
        if (reservedApisReady.get()) {
            return;
        }
        seedReservedApis();
        reservedApisReady.set(true);
    }

    public void ensureReservedApi(String id) {
        HostCatalogReserved.Spec spec = HostCatalogReserved.specOfId(id);
        if (spec != null) {
            insertIfAbsent(spec);
        }
    }

    private boolean runIsolated(String step, Runnable action) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> action.run());
            return true;
        } catch (Exception e) {
            log.warn("[HostCatalog] 启动引导「{}」失败（可稍后重试）: {}", step, e.getMessage());
            return false;
        }
    }

    private void seedPermissions() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        upsertPerm(PERM_VIEW_ID, PERM_VIEW, "宿主机配置查看", now);
        upsertPerm(PERM_WRITE_ID, PERM_WRITE, "宿主机配置管理", now);
        grantIfMissing("OPERATOR", PERM_VIEW);
        grantIfMissing("VIEWER", PERM_VIEW);
    }

    private void upsertPerm(String id, String code, String name, LocalDateTime now) {
        if (sysPermissionRepository.existsById(id)) {
            return;
        }
        boolean codeTaken = sysPermissionRepository.findAll().stream()
                .anyMatch(p -> code.equals(p.getPermCode()));
        if (codeTaken) {
            return;
        }
        entityManager.persist(SysPermissionDO.builder()
                .id(id)
                .permCode(code)
                .permName(name)
                .groupCode("sys")
                .remark("平台设置 · 宿主机配置")
                .createTime(now)
                .build());
        log.info("[HostCatalog] 已补齐权限 {}", code);
    }

    private void grantIfMissing(String roleCode, String permCode) {
        SysRoleDO role = sysRoleRepository.findByRoleCode(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        Set<String> existing = sysRolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(SysRolePermissionDO::getPermCode)
                .collect(Collectors.toSet());
        if (existing.contains(permCode) || existing.contains("*")) {
            return;
        }
        sysRolePermissionRepository.save(new SysRolePermissionDO(role.getId(), permCode));
    }

    private void seedReservedApis() {
        for (HostCatalogReserved.Spec spec : HostCatalogReserved.allSpecs()) {
            insertIfAbsent(spec);
        }
    }

    private void insertIfAbsent(HostCatalogReserved.Spec spec) {
        long id = Long.parseLong(spec.id());
        List<Integer> deletedValues = jdbcTemplate.query(
                "SELECT deleted FROM flow_api_info WHERE id = ?",
                (rs, rowNum) -> rs.getInt(1),
                id);
        if (!deletedValues.isEmpty()) {
            if (deletedValues.get(0) != null && deletedValues.get(0) != 0) {
                jdbcTemplate.update(
                        "UPDATE flow_api_info SET deleted = 0, update_time = CURRENT_TIMESTAMP WHERE id = ?",
                        id);
                log.info("[HostCatalog] 已恢复逻辑删除的保留接口 {}", spec.id());
            }
            return;
        }
        try {
            int rows = jdbcTemplate.update(
                    "INSERT INTO flow_api_info ("
                            + "id, name, url, method, service_type, intercept_mode, response_type, "
                            + "json_content, publish_status, log_enabled, log_mode, deleted, "
                            + "info, tags, security_config, create_time, update_time"
                            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    id,
                    spec.name(),
                    spec.url(),
                    "GET",
                    "JSON",
                    "REPLACE",
                    "LIST",
                    spec.defaultJson(),
                    0,
                    false,
                    "OFF",
                    0,
                    HostCatalogReserved.DIR_PRINCIPAL.equals(spec.id()) ? INFO_PRINCIPAL : INFO,
                    "system,host-catalog",
                    SECURITY_JSON);
            if (rows > 0) {
                log.info("[HostCatalog] 已创建保留接口 {} {}", spec.id(), spec.url());
            }
        } catch (DuplicateKeyException ignore) {
            // 并发启动
        } catch (Exception e) {
            log.warn("[HostCatalog] 插入保留接口 {} 失败: {}", spec.id(), e.getMessage());
            throw e;
        }
    }
}
