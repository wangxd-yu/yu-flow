package org.yu.flow.module.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewExportConfigDTO {
    @Builder.Default
    private Boolean enabled = true;
    @Builder.Default
    private String sheetName = "数据";
    /** 默认 50000 */
    private Integer maxExportRows;
    /** FLOW 用，P0 可空 */
    private String itemsPath;
    /**
     * DYNAMIC | TEMPLATE；TEMPLATE 需已上传模板，否则导出回退 DYNAMIC
     */
    @Builder.Default
    private String exportMode = "DYNAMIC";
    /** 模板文件 ID（flow_api_excel_template.id），可空 */
    private String templateFileId;
    /** 模板 Sheet 下标，默认 0 */
    private Integer templateSheetNo;
    @Builder.Default
    private List<ViewExportColumnDTO> columns = new ArrayList<>();
}
