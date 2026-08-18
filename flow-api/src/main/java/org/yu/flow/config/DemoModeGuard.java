package org.yu.flow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.yu.flow.exception.FlowException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示模式守卫。
 *
 * <p>当 {@code yu.flow.demo-mode=true} 时，本 Bean 在启动时将数据库中现存的所有
 * 资产 ID（API、模型、目录、数据源）加载到内存保护名单中。</p>
 *
 * <p>后续任何对保护名单内 ID 的修改或删除请求都将被立即拦截，并返回友好提示，
 * 而用户新建的资产（启动后创建，ID 不在名单中）则不受任何限制。</p>
 *
 * <p>本组件无论 demoMode 是否开启都会被注入；所有守卫方法会在 demoMode 关闭时
 * 直接放行，因此对非演示环境的性能影响为零。</p>
 *
 * @author yu-flow
 */
@Component
public class DemoModeGuard {

    private static final Logger log = LoggerFactory.getLogger(DemoModeGuard.class);

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 启动时加载的系统预置资产 ID 保护名单（key=id, value=true） */
    private final Set<String> protectedIds = ConcurrentHashMap.newKeySet();

    // ====================================================================
    //  初始化
    // ====================================================================

    @PostConstruct
    public void init() {
        if (!yuFlowProperties.isDemoMode()) {
            log.debug("[DemoModeGuard] 演示模式未开启，守卫空载运行。");
            return;
        }

        log.warn("[DemoModeGuard] *** 演示模式已开启 *** 正在锁定当前所有系统预置资产...");
        loadProtectedIds("flow_api_info", "API 接口");
        loadProtectedIds("flow_model_info", "数据模型");
        loadProtectedIds("flow_db_connection", "数据源");
        loadProtectedIds("flow_directory", "目录");
        loadProtectedIds("flow_sys_macro", "系统宏定义");
        loadProtectedIds("flow_page_info", "页面设计");
        loadProtectedIds("flow_sys_config", "系统参数");
        loadProtectedIds("flow_task_info", "定时任务");
        loadProtectedIds("flow_service_info", "服务编排");
        loadProtectedIds("flow_open_platform", "开放平台");
        loadProtectedIds("flow_mq_connection", "MQ 连接");
        loadProtectedIds("flow_mq_task_info", "MQ 任务");
        loadProtectedIds("flow_oss_connection", "OSS 连接");
        loadProtectedIds("flow_oss_upload_profile", "OSS 上传场景");
        log.warn("[DemoModeGuard] 演示模式资产锁定完成，共保护 {} 个资产 ID。", protectedIds.size());
    }

    private void loadProtectedIds(String tableName, String description) {
        try {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM " + tableName + " LIMIT 5000", String.class);
            protectedIds.addAll(ids);
            log.info("[DemoModeGuard] 已锁定{}（{}）的 {} 条记录。", description, tableName, ids.size());
        } catch (Exception e) {
            log.error("[DemoModeGuard] 加载保护名单失败，表名={}，原因={}", tableName, e.getMessage(), e);
        }
    }

    // ====================================================================
    //  守卫校验方法
    // ====================================================================

    /**
     * 校验某资产 ID 是否在演示保护名单中（用于修改/删除操作前的检查）。
     *
     * @param id         被操作的资产 ID
     * @param targetName 资产名称（用于错误提示，如"API 接口"）
     * @throws FlowException 若当前为演示模式且 ID 在保护名单内
     */
    public void checkModifyOrDelete(String id, String targetName) {
        if (!yuFlowProperties.isDemoMode()) {
            return;
        }
        if (id != null && protectedIds.contains(id)) {
            log.warn("[DemoModeGuard] 拒绝修改/删除操作，ID={} 为演示预置资产（{}）。", id, targetName);
            throw new FlowException(
                    "DEMO_RESTRICTED",
                    "演示模式限制：系统预置的【" + targetName + "】不允许被修改或删除，您可以创建新的资产进行体验！"
            );
        }
    }

    /**
     * 校验当前是否为演示模式，若是则拒绝所有 SQL 写操作（INSERT / UPDATE / DELETE）。
     *
     * @param operationType 操作类型描述（如"INSERT"/"UPDATE"/"DELETE"）
     * @throws FlowException 若当前为演示模式
     */
    public void checkSqlWrite(String operationType) {
        if (yuFlowProperties.isDemoMode()) {
            log.warn("[DemoModeGuard] 拒绝 SQL 写操作：{}，当前为演示模式。", operationType);
            throw new FlowException(
                    "DEMO_RESTRICTED",
                    "演示模式限制：禁止执行数据库写入/修改/删除操作（" + operationType + "）。"
                            + "如需体验增删改功能，请联系管理员获取完整版！"
            );
        }
    }

    /**
     * 校验 API 的响应类型，演示模式下禁止新建/更新为写入类 API（INSERT/UPDATE）。
     *
     * @param responseType API 的 responseType 字段值
     * @throws FlowException 若当前为演示模式且 responseType 为写入类型
     */
    public void checkApiResponseType(String responseType) {
        if (!yuFlowProperties.isDemoMode()) {
            return;
        }
        if ("INSERT".equalsIgnoreCase(responseType) || "UPDATE".equalsIgnoreCase(responseType)) {
            log.warn("[DemoModeGuard] 拒绝创建/更新写入类 API，responseType={}。", responseType);
            throw new FlowException(
                    "DEMO_RESTRICTED",
                    "演示模式限制：禁止创建或修改数据库写入类型接口（" + responseType + "）。"
                            + "演示环境仅支持 SELECT / PAGE / LIST / OBJECT 类查询接口！"
            );
        }
    }

    /**
     * 校验 ForStep 循环的输入数组大小，演示模式下禁止超大数组。
     *
     * @param arraySize   输入数组元素数
     * @param forStepId   ForStep 节点 ID（用于日志）
     * @throws FlowException 若当前为演示模式且数组大小超出限制
     */
    public void checkForLoopSize(int arraySize, String forStepId) {
        if (!yuFlowProperties.isDemoMode()) {
            return;
        }
        int limit = yuFlowProperties.getDemo().getMaxForLoopItems();
        if (limit > 0 && arraySize > limit) {
            log.warn("[DemoModeGuard] 拒绝 ForStep [{}] 执行，数组大小 {} 超出限制 {}。",
                    forStepId, arraySize, limit);
            throw new FlowException(
                    "DEMO_RESTRICTED",
                    "演示模式限制：For 循环节点 [" + forStepId + "] 的输入数组大小（"
                            + arraySize + "）超出上限（" + limit + "），已被安全机制拦截。"
            );
        }
    }

    /**
     * 校验 Delay 节点等待时长，演示模式下禁止过长 sleep。
     */
    public void checkDelayMs(long delayMs, String delayStepId) {
        if (!yuFlowProperties.isDemoMode()) {
            return;
        }
        long limit = yuFlowProperties.getDemo().getMaxDelayMs();
        if (limit > 0 && delayMs > limit) {
            log.warn("[DemoModeGuard] 拒绝 DelayStep [{}] 执行，delayMs {} 超出限制 {}。",
                    delayStepId, delayMs, limit);
            throw new FlowException(
                    "DEMO_RESTRICTED",
                    "演示模式限制：Delay 节点 [" + delayStepId + "] 的等待时间（"
                            + delayMs + "ms）超出上限（" + limit + "ms），已被安全机制拦截。"
            );
        }
    }

    /**
     * 校验 MQ 消息发送，演示模式下禁止向外部消息队列投递。
     *
     * @param topic 目标 topic（用于日志）
     * @throws FlowException 若当前为演示模式
     */
    public void checkMqSend(String topic) {
        if (yuFlowProperties.isDemoMode()) {
            log.warn("[DemoModeGuard] 拒绝 MQ 消息发送，topic={}，当前为演示模式。", topic);
            throw new FlowException(
                    "DEMO_RESTRICTED",
                    "演示模式限制：禁止向外部消息队列发送消息。如需体验 MQ 节点，请联系管理员获取完整版！"
            );
        }
    }

    /**
     * 校验 OSS 写入（上传 / 删除对象），演示模式下禁止向真实桶写入。
     *
     * @param target 桶名或目标描述（用于日志）
     */
    public void checkOssWrite(String target) {
        if (yuFlowProperties.isDemoMode()) {
            log.warn("[DemoModeGuard] 拒绝 OSS 写入，target={}，当前为演示模式。", target);
            throw new FlowException(
                    "DEMO_RESTRICTED",
                    "演示模式限制：禁止向对象存储写入或删除文件。如需体验 OSS 上传，请联系管理员获取完整版！"
            );
        }
    }

    /**
     * 获取演示模式下的最大步骤数限制。
     * @return 最大步骤数，若非演示模式返回 0（不限制）
     */
    public int getMaxSteps() {
        if (!yuFlowProperties.isDemoMode()) {
            return 0;
        }
        return yuFlowProperties.getDemo().getMaxSteps();
    }

    /**
     * @return 当前是否处于演示模式
     */
    public boolean isDemoMode() {
        return yuFlowProperties.isDemoMode();
    }
}
