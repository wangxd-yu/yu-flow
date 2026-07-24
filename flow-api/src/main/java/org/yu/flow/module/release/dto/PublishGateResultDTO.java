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
public class PublishGateResultDTO {
    private String assetType;
    private String assetId;
    private String envCode;
    private boolean passed;
    private String message;
    @Builder.Default
    private List<PublishGateCheckItemDTO> checks = new ArrayList<>();
}
