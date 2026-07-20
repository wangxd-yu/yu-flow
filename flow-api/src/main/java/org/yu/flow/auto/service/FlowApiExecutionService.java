package org.yu.flow.auto.service;

import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowDbDebugRequestDTO;
import org.springframework.data.domain.Pageable;

import jakarta.servlet.http.HttpServletResponse;
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

    /**
     * 数据库模式调试运行：不落执行日志，返回与逻辑编排调试一致的 FlowTrace。
     */
    FlowTrace debugRunDb(FlowDbDebugRequestDTO request);
}
