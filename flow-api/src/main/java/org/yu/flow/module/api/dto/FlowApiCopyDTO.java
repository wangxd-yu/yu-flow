package org.yu.flow.module.api.dto;

import lombok.Data;

/**
 * 复制接口的目标信息。
 */
@Data
public class FlowApiCopyDTO {

    /** 新接口名称，为空时取「源名称_副本」 */
    private String name;

    /** 新接口完整 path，为空时沿用源接口 path */
    private String url;

    /** 目标目录，为空时沿用源接口目录 */
    private String directoryId;
}
