package org.yu.flow.module.log.third.query;

import lombok.Data;

@Data
public class FlowThirdLogQueryDTO {
    private Integer page = 0;
    private Integer size = 10;
    /** 调用来源：API / TASK / DEBUG / OTHER */
    private String source;
    /** 来源名称模糊搜索 */
    private String sourceName;
    /** 来源关联 ID */
    private String sourceRef;
    /** 接口标识 */
    private String apiType;
    /** 是否成功：0 / 1 */
    private Integer isSuccess;
    /** 请求方法 */
    private String requestMethod;
    /** URL 模糊搜索 */
    private String requestUrl;
    /** 请求 ID 精确匹配（响应头 X-Request-Id） */
    private String requestId;
    private String startTime;
    private String endTime;
}
