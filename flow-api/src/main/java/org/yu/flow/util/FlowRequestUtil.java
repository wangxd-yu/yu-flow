package org.yu.flow.util;

import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.cache.FlowRedisUtil;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * @author yu-flow
 * @date 2025-11-17 下午7:28
 */
public class FlowRequestUtil {

    /**
     * 判断 请求配置中是否有对应 标签，用于上层应用做 权限管理使用
     * request 需要传入，因为上层可能在filter中判断，filter中无法通过 RequestContextHolder.getRequestAttributes() 获取
     *
     * @return
     */
    public static boolean hasTag(String tag, HttpServletRequest request) {
        String requestMethod = request.getMethod();
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        // 不包含 context-requestPath
        urlPathHelper.setAlwaysUseFullPath(false);
        // 是否解码 URL（默认 true）
        urlPathHelper.setUrlDecode(false);
        String requestPath = urlPathHelper.getPathWithinApplication(request);

        boolean b = FlowRedisUtil.hhasKey("flow::api::map", requestMethod + "-" + requestPath.substring(1));
        if (b) {
            FlowApiDO flowApiDO = FlowRedisUtil.hget("flow::api::map", requestMethod + "-" + requestPath.substring(1), FlowApiDO.class);
            return !Objects.isNull(flowApiDO) && flowApiDO.getTags() != null && flowApiDO.getTags().contains(tag);
        }
        return false;
    }
}
