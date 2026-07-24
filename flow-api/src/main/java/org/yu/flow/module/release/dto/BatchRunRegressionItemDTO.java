package org.yu.flow.module.release.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRunRegressionItemDTO {
    private String assetId;
    private String assetName;
    private String suiteId;
    private String runId;
    /** PASSED | FAILED | SKIPPED | ERROR */
    private String status;
    private String message;
}
