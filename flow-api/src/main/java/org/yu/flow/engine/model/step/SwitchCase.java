package org.yu.flow.engine.model.step;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Switch / Condition 单条分支。
 * <ul>
 *   <li>{@code id}：稳定端口后缀，出口为 {@code case_<id>}</li>
 *   <li>{@code name}：画布展示名（可编辑）</li>
 *   <li>{@code value}：与表达式求值结果匹配的值</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwitchCase {
    private String id;
    private String name;
    private String value;

    public static SwitchCase of(String id, String name, String value) {
        SwitchCase c = new SwitchCase();
        c.setId(id);
        c.setName(name);
        c.setValue(value);
        return c;
    }
}
