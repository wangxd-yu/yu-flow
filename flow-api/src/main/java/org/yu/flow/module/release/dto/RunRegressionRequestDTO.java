package org.yu.flow.module.release.dto;

import lombok.Data;

@Data
public class RunRegressionRequestDTO {
    /** 目标逻辑环境，默认 DEV */
    private String envCode;
}
