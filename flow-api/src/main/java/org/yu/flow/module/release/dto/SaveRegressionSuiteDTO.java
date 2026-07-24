package org.yu.flow.module.release.dto;

import lombok.Data;

@Data
public class SaveRegressionSuiteDTO {
    private String name;
    private String assetType;
    private String assetId;
    private Integer enabled;
}
