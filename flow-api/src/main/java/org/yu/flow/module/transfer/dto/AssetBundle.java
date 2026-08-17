package org.yu.flow.module.transfer.dto;

import lombok.Data;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.directory.domain.FlowDirectoryDO;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.task.domain.FlowTaskDO;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨环境资产迁移包。
 *
 * <p>资产一律保留源环境 ID：DSL 中的 {@code serviceId} 与 {@code directoryId} 都是 ID 引用，
 * 保留 ID 才能让引用在目标环境自动对上，并让同一个包可以反复导入做版本更新。</p>
 *
 * <p>包内不含任何连接串与密钥：数据源 / MQ / OSS 都是按 code 引用，目标环境需自行准备同 code 的资源，
 * 缺失项由导入预检列出（见 {@link #requirements}）。</p>
 */
@Data
public class AssetBundle {

    public static final String KIND = "yu-flow/asset-bundle";
    public static final int SCHEMA_VERSION = 1;

    /** 包格式版本，导入时校验 */
    private Integer schemaVersion;

    /** 固定为 {@link #KIND}，用于识别误传的文件 */
    private String kind;

    private String exportedAt;

    private String exportedBy;

    /** 导出方备注的来源环境，仅作展示 */
    private String sourceEnv;

    /** PUBLISHED_FIRST：已发布接口取线上快照内容；DRAFT：取草稿内容 */
    private String contentSource;

    private List<FlowDirectoryDO> directories = new ArrayList<>();

    private List<FlowApiDO> apis = new ArrayList<>();

    private List<FlowServiceFlowDO> services = new ArrayList<>();

    private List<FlowTaskDO> tasks = new ArrayList<>();

    private List<BundleRegressionSuite> regressionSuites = new ArrayList<>();

    /** 目标环境必须已存在的外部资源（数据源 / MQ / OSS / 响应模板） */
    private List<TransferRequirementDTO> requirements = new ArrayList<>();

    /** 导出期发现的问题，例如引用了已被删除的资产 */
    private List<String> warnings = new ArrayList<>();
}
