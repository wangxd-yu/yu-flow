package org.yu.flow.module.release.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishGateCheckItemDTO {
    private String code;
    private String name;
    /** PASS | FAIL | SKIP */
    private String status;
    private String message;
}
