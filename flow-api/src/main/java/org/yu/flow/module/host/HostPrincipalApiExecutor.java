package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.yu.flow.auto.service.FlowApiExecutionService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.repository.FlowApiRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程内执行「当前用户解析」保留接口。
 *
 * <p>入参：{@code headers}（按白名单转发的请求头）、{@code token}（去掉 Bearer 前缀的凭证）、
 * {@code clientIp}。编排里可用 {@code $.request.headers.xxx} 或 {@code ${token}} 取用。</p>
 */
@Slf4j
@Component
public class HostPrincipalApiExecutor {

    /** 解析接口自身若再触发主体解析会无限递归，用线程标记短路。 */
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Resource
    private FlowApiRepository flowApiRepository;
    @Resource
    private FlowApiExecutionService flowApiExecutionService;

    public boolean isPublished() {
        return flowApiRepository.findById(HostCatalogReserved.DIR_PRINCIPAL)
                .map(api -> api.getPublishStatus() != null && api.getPublishStatus() == 1)
                .orElse(false);
    }

    /**
     * @param draft 管理端测试时允许跑草稿；运行时只认已发布，避免半成品编排决定身份
     * @return 解析结果首行，接口缺失/未发布/执行失败均返回 null
     */
    public Map<String, Object> resolveRow(HttpServletRequest request, HostPrincipalSettings settings, boolean draft) {
        if (Boolean.TRUE.equals(RESOLVING.get())) {
            return null;
        }
        FlowApiDO api = flowApiRepository.findById(HostCatalogReserved.DIR_PRINCIPAL).orElse(null);
        if (api == null) {
            return null;
        }
        boolean published = api.getPublishStatus() != null && api.getPublishStatus() == 1;
        if (!published && !draft) {
            return null;
        }
        FlowApiDO exec;
        try {
            exec = published && !draft
                    ? HostCatalogApiExecutor.publishedClone(api)
                    : HostCatalogApiExecutor.draftClone(api);
            assertReadOnly(exec);
        } catch (Exception e) {
            log.warn("[HostPrincipal] 物化解析接口失败: {}", e.getMessage());
            return null;
        }
        RESOLVING.set(Boolean.TRUE);
        try {
            Object raw = flowApiExecutionService.executeApi(
                    exec, buildParams(request, settings), PageRequest.of(0, 1), null);
            return HostPrincipalResultMapper.firstRow(raw);
        } catch (Exception e) {
            log.warn("[HostPrincipal] 执行解析接口失败: {}", e.getMessage());
            return null;
        } finally {
            RESOLVING.remove();
        }
    }

    private static Map<String, Object> buildParams(HttpServletRequest request, HostPrincipalSettings settings) {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        if (request != null) {
            for (String name : settings.resolvedForwardHeaders()) {
                String value = request.getHeader(name);
                if (StrUtil.isNotBlank(value)) {
                    headers.put(name, value);
                }
            }
            params.put("clientIp", StrUtil.trimToEmpty(request.getRemoteAddr()));
            params.put("token", stripBearer(request.getHeader("Authorization")));
        }
        params.put("headers", headers);
        return params;
    }

    private static String stripBearer(String authorization) {
        String v = StrUtil.trimToEmpty(authorization);
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return v;
    }

    private static void assertReadOnly(FlowApiDO api) {
        if (!"DB".equalsIgnoreCase(api.getServiceType())) {
            return;
        }
        String responseType = StrUtil.blankToDefault(api.getResponseType(), "").trim();
        if (!List.of("LIST", "PAGE").contains(responseType.toUpperCase())) {
            throw new IllegalStateException("DB 主体解析只允许 LIST / PAGE");
        }
    }
}
