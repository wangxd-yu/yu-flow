package org.yu.flow.module.api.runtime;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Pageable;
import org.yu.flow.module.api.domain.FlowApiDO;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 按 serviceType 执行接口内容。新增一种实现方式时注册一个 Bean，{@link ApiDispatcher} 按类型路由。
 */
public interface ApiTypeExecutor {

    /** 负责的 serviceType，同一类型只能由一个实现负责 */
    Set<String> serviceTypes();

    Object execute(Invocation invocation) throws Exception;

    /**
     * @param content    按发布快照或草稿解析出的脚本 / 配置
     * @param params     契约类型转换后的合并参数
     * @param flowInputs 流程模式需要的分区入参，按需构建
     */
    record Invocation(FlowApiDO api, String content, Map<String, Object> params, Pageable pageable,
                      HttpServletResponse response, Supplier<Map<String, Object>> flowInputs,
                      RuntimeLogContext logContext) {
    }
}
