package org.yu.flow.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.yu.flow.engine.model.step.*;
import org.yu.flow.engine.model.step.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点基类（增强版）
 * 新增：端口定义、元数据、验证
 *
 * @author yu-flow
 * @date 2025-04-10 19:44 (updated 2026-01-31)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ServiceCallStep.class, name = "call"),
        @JsonSubTypes.Type(value = ServiceCallStep.class, name = "serviceCall"),
        @JsonSubTypes.Type(value = ConditionStep.class, name = "condition"),
        @JsonSubTypes.Type(value = IfStep.class, name = "if"),
        @JsonSubTypes.Type(value = SwitchStep.class, name = "switch"),
        @JsonSubTypes.Type(value = ReturnStep.class, name = "return"),
        @JsonSubTypes.Type(value = ParallelStep.class, name = "parallel"),
        @JsonSubTypes.Type(value = SetVarStep.class, name = "set"),
        @JsonSubTypes.Type(value = ApiServiceCallStep.class, name = "api"),
        @JsonSubTypes.Type(value = EvaluateStep.class, name = "evaluate"),
        @JsonSubTypes.Type(value = StartStep.class, name = "start"),
        @JsonSubTypes.Type(value = EndStep.class, name = "end"),
        @JsonSubTypes.Type(value = RequestStep.class, name = "request"),
        @JsonSubTypes.Type(value = HttpRequestStep.class, name = "httpRequest"),
        @JsonSubTypes.Type(value = ForStep.class, name = "for"),
        @JsonSubTypes.Type(value = CollectStep.class, name = "collect"),
        @JsonSubTypes.Type(value = TemplateStep.class, name = "template"),
        @JsonSubTypes.Type(value = ResponseStep.class, name = "response"),
        @JsonSubTypes.Type(value = RecordStep.class, name = "record"),
        @JsonSubTypes.Type(value = DatabaseStep.class, name = "database"),
        @JsonSubTypes.Type(value = SystemVarStep.class, name = "systemVar"),
        @JsonSubTypes.Type(value = SystemMethodStep.class, name = "systemMethod")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Step {
    // ========== 基础字段 ==========
    protected String id;
    protected String name;
    /**
     * 连接点映射 (Jointer)
     * Key: 端口名称 (如 out, then, else)
     * Value: 目标步骤ID (String) 或 目标步骤ID列表 (List<String>)
     */
    protected Map<String, Object> next = new HashMap<>();

    // ========== 新增字段 ==========
    protected String description;  // 节点描述
    protected String version;      // 节点版本
    private Map<String, Object> inputs; // 输入变量定义

    // ========== 抽象方法 ==========

    /**
     * 获取节点类型
     */
    public abstract String getType();

    /**
     * 获取输入端口定义
     * 子类可重写以定义自己的输入端口
     */
    @JsonIgnore
    public List<PortDefinition> getInputPorts() {
        // 默认没有输入端口
        return new ArrayList<>();
    }

    /**
     * 获取输出端口定义
     * 子类必须重写以定义自己的输出端口
     */
    @JsonIgnore
    public abstract List<PortDefinition> getOutputPorts();

    /**
     * 获取节点元数据
     */
    @JsonIgnore
    public StepMetadata getMetadata() {
        return StepMetadata.builder()
                .displayName(getType())
                .description(description != null ? description : "")
                .category("general")
                .version(version != null ? version : "1.0")
                .build();
    }

    /**
     * 验证节点配置
     * 子类可重写以添加自定义验证逻辑
     */
    @JsonIgnore
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        // 基础验证
        if (id == null || id.trim().isEmpty()) {
            errors.add("节点ID不能为空");
        }

        // 子类可添加更多验证
        List<String> customErrors = validateCustom();
        if (customErrors != null) {
            errors.addAll(customErrors);
        }

        if (errors.isEmpty()) {
            return ValidationResult.success();
        } else {
            return ValidationResult.failure(errors);
        }
    }

    /**
     * 自定义验证逻辑（子类重写）
     */
    protected List<String> validateCustom() {
        return null;
    }

    // ========== Getters and Setters ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getNext() {
        return next;
    }

    public void setNext(Map<String, Object> next) {
        this.next = next;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
}
