package org.yu.flow.module.oss.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OssPresignUrlDTO {

    private String url;
    private int expireSeconds;
}
