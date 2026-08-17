package org.yu.flow.module.transfer.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量导出请求。
 */
@Data
public class AssetExportRequestDTO {

    public static final String SOURCE_PUBLISHED_FIRST = "PUBLISHED_FIRST";
    public static final String SOURCE_DRAFT = "DRAFT";

    private List<String> apiIds;

    private List<String> serviceIds;

    private List<String> taskIds;

    /** 是否把 DSL 中引用到的其它接口 / 内部服务一并导出，默认 true */
    private Boolean includeDependencies;

    /** 是否带上回归套件与用例，默认 true */
    private Boolean includeRegression;

    /** {@link #SOURCE_PUBLISHED_FIRST}（默认）或 {@link #SOURCE_DRAFT} */
    private String contentSource;

    /** 来源环境备注，仅写进包头供人识别 */
    private String sourceEnv;
}
