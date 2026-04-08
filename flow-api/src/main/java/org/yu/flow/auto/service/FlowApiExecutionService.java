package org.yu.flow.auto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.springframework.data.domain.Pageable;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * FlowApi 执行服务接口 —— 负责动态 API 的运行时执行逻辑
 *
 * @author yu-flow
 */
public interface FlowApiExecutionService {

    /**
     * 执行动态 API（合并参数模式）
     */
    Object executeApi(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable, HttpServletResponse response) throws Exception;

    /**
     * 执行动态 API（分离参数模式）
     */
    Object executeApi(FlowApiDO flowApiDO, Map<String, String> queryParams, Map<String, Object> bodyParams, Map<String, Object> mergeParamsMap, Pageable pageable, HttpServletResponse response) throws Exception;
}
