package org.yu.flow.engine.model;

import lombok.Data;

/**
 * 端口定义
 * 描述节点的输入/输出端口
 */
@Data
public class PortDefinition {
    private String name;        // 端口名称 (如 "out", "true", "false", "case_xxx")
    private String type;        // 数据类型 (如 "string", "number", "boolean", "object", "any")
    private boolean required;   // 是否必需
    private String description; // 端口描述

    public PortDefinition() {
    }

    public PortDefinition(String name, String type, boolean required) {
        this.name = name;
        this.type = type;
        this.required = required;
    }

    public PortDefinition(String name, String type, boolean required, String description) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
    }

    /**
     * 快速创建输出端口
     */
    public static PortDefinition output(String name) {
        return new PortDefinition(name, "any", false, null);
    }

    /**
     * 快速创建输入端口
     */
    public static PortDefinition input(String name, String type, boolean required) {
        return new PortDefinition(name, type, required, null);
    }
}
