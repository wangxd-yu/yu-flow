package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.yu.flow.module.host.HostPrincipalSettings;

import java.util.List;
import java.util.Map;

/**
 * 宿主主体解析总览：配置 + 保留接口状态 + 管理端提示所需的默认值。
 */
@Data
@Accessors(chain = true)
public class HostPrincipalOverviewDTO {

    /** 宿主已注册自定义 FlowHostPrincipalProvider，配置式解析不生效 */
    private boolean spiOverride;
    private HostPrincipalSettings settings;
    private HostCatalogApiMetaDTO api;
    /** 可映射的 Principal 字段顺序 */
    private List<String> fields;
    /** HEADER 模式的约定请求头名 */
    private Map<String, String> defaultHeaderNames;
}
