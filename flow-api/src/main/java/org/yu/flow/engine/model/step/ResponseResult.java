package org.yu.flow.engine.model.step;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 响应结果封装类，用于传递 HTTP 终态节点的最终输出结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseResult {
    private int status;
    private Map<String, String> headers;
    private Object body;
}
