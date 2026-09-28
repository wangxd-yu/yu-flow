package org.yu.flow.module.release.support;

import jakarta.annotation.Resource;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.exception.FlowException;

import java.util.Set;

/**
 * 锁定资产编辑（{@code yu.flow.release.lock-asset-editing=true}）：
 * 拦截 HTTP 请求里对编排资产的新增 / 修改 / 删除 / 发布 / 回滚，变更只能走发布包导入。
 *
 * <p>启停、日志开关、缓存配置等运维操作不拦截；与环境绑定的配置（连接、告警通道、开放平台密钥、系统配置、
 * 环境变量）不在锁定范围；应用启动等非请求上下文的内部调用不受影响。</p>
 *
 * <p>拦截范围内的接口新增方法时，{@code AssetEditLockAspectTest} 会要求在锁定 / 放行两类中归类。</p>
 */
@Aspect
@Component
public class AssetEditLockAspect {

    static final Set<String> LOCKED_METHODS = Set.of(
            "save", "batchSave", "create", "update", "updateJson", "updateStatus", "copy", "clonePage",
            "delete", "batchDelete", "batchMove", "batchApplyDirPrefix",
            "publish", "unpublish", "republish", "rollbackToPublished", "restoreVersion", "setDefault",
            "importBundle", "importFromDb", "importFromDdl",
            // 接口 Excel 模板
            "uploadExcelTemplate", "deleteExcelTemplate",
            // 开放平台授权（凭证签发 / 轮换是环境内操作，不锁）
            "replaceGrants", "purgeInvalidGrants",
            // 告警规则（告警通道是环境内连接，不锁）
            "createRule", "updateRule", "deleteRule",
            // 回归套件与用例
            "createSuite", "updateSuite", "deleteSuite", "createCase", "updateCase", "deleteCase");

    static final String POINTCUT = "execution(* org.yu.flow.module.api.service.FlowApiCrudService.*(..))"
            + " || execution(* org.yu.flow.module.api.service.ApiDataViewService.*(..))"
            + " || execution(* org.yu.flow.module.serviceflow.service.FlowServiceFlowService.*(..))"
            + " || execution(* org.yu.flow.module.task.service.FlowTaskService.*(..))"
            + " || execution(* org.yu.flow.module.mqtask.service.FlowMqTaskService.*(..))"
            + " || execution(* org.yu.flow.module.page.service.PageInfoService.*(..))"
            + " || execution(* org.yu.flow.module.model.service.FlowModelInfoService.*(..))"
            + " || execution(* org.yu.flow.module.responsetemplate.service.ResponseTemplateService.*(..))"
            + " || execution(* org.yu.flow.module.sysmacro.service.SysMacroService.*(..))"
            + " || execution(* org.yu.flow.module.open.service.FlowOpenPlatformService.*(..))"
            + " || execution(* org.yu.flow.module.alert.service.AlertManageService.*(..))"
            + " || execution(* org.yu.flow.module.directory.service.FlowDirectoryService.*(..))"
            + " || execution(* org.yu.flow.module.release.service.ReleaseManageService.*(..))"
            + " || execution(* org.yu.flow.module.transfer.service.AssetTransferService.importBundle(..))";

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Before(POINTCUT)
    public void check(JoinPoint jp) {
        if (!isLocked() || AssetEditLock.isUnlocked() || RequestContextHolder.getRequestAttributes() == null) {
            return;
        }
        if (LOCKED_METHODS.contains(jp.getSignature().getName())) {
            throw new FlowException("ASSET_EDIT_LOCKED",
                    "本环境已锁定资产编辑，所有变更请在开发测试环境完成后通过发布包导入");
        }
    }

    public boolean isLocked() {
        return yuFlowProperties.getRelease() != null && yuFlowProperties.getRelease().isLockAssetEditing();
    }
}
