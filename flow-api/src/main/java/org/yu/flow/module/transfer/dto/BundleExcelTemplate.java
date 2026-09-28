package org.yu.flow.module.transfer.dto;

import lombok.Data;

/**
 * 接口附带的 Excel 导出模板（二进制以 Base64 随包携带）。
 */
@Data
public class BundleExcelTemplate {

    private String apiId;

    private String fileName;

    private String contentType;

    private Integer fileSize;

    private String contentBase64;
}
