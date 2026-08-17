package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 主体解析测试结果：原始返回 + 映射后的主体，便于对字段名。
 */
@Data
@Accessors(chain = true)
public class HostPrincipalTestResultDTO {

    private boolean resolved;
    private String mode;
    private boolean apiPublished;
    /** 解析接口返回的首行原始数据（HEADER 模式为空） */
    private Map<String, Object> raw;
    /** 按字段映射得到的主体 */
    private Map<String, Object> principal;
    private String message;
}
