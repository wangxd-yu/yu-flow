package org.yu.flow.module.release.dto;

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
public class BatchRunRegressionResultDTO {
    private String assetType;
    private String envCode;
    private int total;
    private int passed;
    private int failed;
    private int skipped;
    private int error;
    @Builder.Default
    private List<BatchRunRegressionItemDTO> items = new ArrayList<>();
}
