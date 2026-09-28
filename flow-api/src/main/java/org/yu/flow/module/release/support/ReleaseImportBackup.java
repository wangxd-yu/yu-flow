package org.yu.flow.module.release.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yu.flow.module.alert.domain.AlertRuleDO;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.domain.FlowApiExcelTemplateDO;
import org.yu.flow.module.model.domain.FlowModelInfoDO;
import org.yu.flow.module.mqtask.domain.FlowMqTaskDO;
import org.yu.flow.module.open.domain.FlowOpenApiGrantDO;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.page.domain.PageInfoDO;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;
import org.yu.flow.module.sysmacro.domain.SysMacroDO;
import org.yu.flow.module.release.domain.FlowRegressionCaseDO;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;
import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.task.domain.FlowTaskDO;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入前受影响资产的完整状态，回滚时据此恢复。
 *
 * <p>目录只会被导入新建、不会被修改，回滚时不处理（留下空目录无害）。</p>
 */
@Data
public class ReleaseImportBackup {

    /** 导入前正常存在：回滚时整行恢复 */
    public static final String ACTIVE = "ACTIVE";
    /** 导入前是已删除行（被导入恢复）：回滚时重新删除 */
    public static final String DELETED = "DELETED";
    /** 导入前不存在：回滚时删除 */
    public static final String ABSENT = "ABSENT";

    private List<Entry<FlowApiDO>> apis = new ArrayList<>();
    private List<Entry<FlowServiceFlowDO>> services = new ArrayList<>();
    private List<Entry<FlowTaskDO>> tasks = new ArrayList<>();
    private List<Entry<FlowMqTaskDO>> mqTasks = new ArrayList<>();
    private List<Entry<ResponseTemplateDO>> responseTemplates = new ArrayList<>();
    /** id 为接口 ID */
    private List<Entry<FlowApiExcelTemplateDO>> excelTemplates = new ArrayList<>();
    private List<Entry<SuiteState>> regressionSuites = new ArrayList<>();
    private List<Entry<PageInfoDO>> pages = new ArrayList<>();
    private List<Entry<FlowModelInfoDO>> models = new ArrayList<>();
    /** id 为宏编码 */
    private List<Entry<SysMacroDO>> sysMacros = new ArrayList<>();
    /** id 为配置键 */
    private List<Entry<SysConfigDO>> sysConfigs = new ArrayList<>();
    /** id 为平台编码 */
    private List<Entry<PlatformState>> openPlatforms = new ArrayList<>();
    private List<Entry<AlertRuleDO>> alertRules = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformState {
        private FlowOpenPlatformDO platform;
        private List<FlowOpenApiGrantDO> grants = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry<T> {
        private String id;
        private String state;
        private T row;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuiteState {
        private FlowRegressionSuiteDO suite;
        private List<FlowRegressionCaseDO> cases = new ArrayList<>();
    }
}
