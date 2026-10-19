package org.yu.flow.module.api.runtime;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowDbDebugRequestDTO;
import org.yu.flow.module.api.service.FlowApiCrudServiceImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态 API 运行时入口。
 *
 * <p>参数绑定与分发见 {@link ApiDispatcher}，各 serviceType 的执行见 {@link ApiTypeExecutor} 的实现，
 * 执行日志与计量见 {@link ApiExecutionRecorder}，SQL 执行见 {@link ApiSqlExecutor}。
 * 管理端 CRUD 在 {@link FlowApiCrudServiceImpl}。
 */
@Service
public class FlowApiServiceImpl implements FlowApiExecutionService {

    private final ApiExecutionRecorder recorder;
    private final ApiDispatcher dispatcher;
    private final DbApiDebugRunner dbDebugRunner;

    public FlowApiServiceImpl(ApiExecutionRecorder recorder, ApiDispatcher dispatcher, DbApiDebugRunner dbDebugRunner) {
        this.recorder = recorder;
        this.dispatcher = dispatcher;
        this.dbDebugRunner = dbDebugRunner;
    }

    @Override
    public Object executeApi(FlowApiDO flowApiDO, Map<String, String> queryParams, Map<String, Object> bodyParams,
                             Map<String, Object> mergeParamsMap, Pageable pageable, HttpServletResponse response) throws Exception {
        Map<String, Object> requestParams = new HashMap<>();
        if (queryParams != null) {
            requestParams.put("queryParams", queryParams);
        }
        if (bodyParams != null) {
            requestParams.put("bodyParams", bodyParams);
        }
        return recorder.record(flowApiDO, requestParams, logContext -> dispatcher.execute(
                flowApiDO, queryParams, bodyParams, mergeParamsMap, pageable, response, logContext));
    }

    @Override
    public Object executeApi(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable,
                             HttpServletResponse response) throws Exception {
        return recorder.record(flowApiDO, params, logContext -> dispatcher.execute(
                flowApiDO, params, pageable, response, logContext));
    }

    @Override
    public FlowTrace debugRunDb(FlowDbDebugRequestDTO request) {
        return dbDebugRunner.run(request);
    }
}
