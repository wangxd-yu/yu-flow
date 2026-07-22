package org.yu.flow.module.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetHealthDTO {
    private String assetType;
    private String assetId;
    /** ok | warn | error | empty */
    private String health;
    private Double successRate;
    private long consecutiveFail;
    private long totalCalls;
    private String window;
}
