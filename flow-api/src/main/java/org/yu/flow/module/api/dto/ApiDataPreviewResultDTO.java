package org.yu.flow.module.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiDataPreviewResultDTO {
    private String apiId;
    private String apiName;
    private String serviceType;
    private String responseType;
    private boolean useDraft;
    @Builder.Default
    private List<ViewExportColumnDTO> columns = new ArrayList<>();
    /** PAGE/LIST：行列表；OBJECT：单元素列表 */
    @Builder.Default
    private List<Map<String, Object>> rows = new ArrayList<>();
    /** OBJECT 时的原始对象 */
    private Map<String, Object> object;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer pages;
    /** 出站隐私是否对本接口生效 */
    private Boolean privacyEnabled;
    /** MASK / REVEAL；未启用时为 null */
    private String privacyClass;
}
