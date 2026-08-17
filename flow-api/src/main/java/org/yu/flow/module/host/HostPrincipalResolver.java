package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.yu.flow.module.host.dto.HostPrincipalTestResultDTO;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 按「宿主机配置」把当前请求解析成宿主主体，供缺省 Provider 与管理端测试共用。
 */
@Component
public class HostPrincipalResolver {

    @Resource
    private HostPrincipalApiExecutor apiExecutor;

    /**
     * @param draft 仅管理端测试可跑草稿；运行时必须已发布
     */
    public FlowHostPrincipal resolve(HttpServletRequest request, HostPrincipalSettings settings, boolean draft) {
        if (settings == null || request == null) {
            return null;
        }
        if (settings.isHeaderMode()) {
            return settings.isTrustProxyHeaders() ? fromHeaders(request, settings) : null;
        }
        Map<String, Object> row = apiExecutor.resolveRow(request, settings, draft);
        return HostPrincipalResultMapper.toPrincipal(row, settings, HostPrincipalSettings.CHANNEL_API);
    }

    /** 管理端「测试解析」：返回原始行与映射结果，便于对字段名。 */
    public HostPrincipalTestResultDTO test(HttpServletRequest request, HostPrincipalSettings settings, boolean draft) {
        HostPrincipalTestResultDTO dto = new HostPrincipalTestResultDTO()
                .setMode(settings.resolvedMode())
                .setApiPublished(apiExecutor.isPublished());
        if (settings.isHeaderMode()) {
            if (!settings.isTrustProxyHeaders()) {
                return dto.setMessage("请求头模式需先确认「Flow 不直接暴露公网」，否则任何人都能伪造身份头。");
            }
            FlowHostPrincipal principal = fromHeaders(request, settings);
            return fill(dto, principal, null,
                    principal == null ? "未读到 " + settings.resolvedHeaderName("userId") + " 请求头" : null);
        }
        Map<String, Object> row = apiExecutor.resolveRow(request, settings, draft);
        if (row == null) {
            return dto.setMessage(dto.isApiPublished() || draft
                    ? "解析接口未返回数据，请检查编排是否正确转发了凭证。"
                    : "解析接口尚未发布，运行时会回退内置 JWT。");
        }
        FlowHostPrincipal principal = HostPrincipalResultMapper.toPrincipal(
                row, settings, HostPrincipalSettings.CHANNEL_API);
        return fill(dto, principal, row,
                principal == null ? "返回值里取不到 " + settings.resolvedField("userId") + " 字段" : null);
    }

    private static HostPrincipalTestResultDTO fill(HostPrincipalTestResultDTO dto, FlowHostPrincipal principal,
                                                   Map<String, Object> raw, String message) {
        dto.setRaw(raw).setMessage(message).setResolved(principal != null);
        if (principal != null) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("userId", principal.getUserId());
            mapped.put("username", principal.getUsername());
            mapped.put("userType", principal.getUserType());
            mapped.put("deptId", principal.getDeptId());
            mapped.put("deptIds", principal.getDeptIds());
            mapped.put("roles", principal.getRoles());
            mapped.put("permissions", principal.getPermissions());
            dto.setPrincipal(mapped);
        }
        return dto;
    }

    static FlowHostPrincipal fromHeaders(HttpServletRequest request, HostPrincipalSettings settings) {
        String userId = header(request, settings.resolvedHeaderName("userId"));
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        String username = header(request, settings.resolvedHeaderName("username"));
        return FlowHostPrincipal.builder()
                .userId(userId)
                .username(StrUtil.blankToDefault(username, userId))
                .userType(header(request, settings.resolvedHeaderName("userType")))
                .deptId(header(request, settings.resolvedHeaderName("deptId")))
                .deptIds(csv(header(request, settings.resolvedHeaderName("deptIds"))))
                .roles(csv(header(request, settings.resolvedHeaderName("roles"))))
                .permissions(csv(header(request, settings.resolvedHeaderName("permissions"))))
                .authChannel(HostPrincipalSettings.CHANNEL_HEADER)
                .attributes(Map.of())
                .build();
    }

    private static String header(HttpServletRequest request, String name) {
        if (request == null || StrUtil.isBlank(name)) {
            return null;
        }
        return StrUtil.trimToNull(request.getHeader(name));
    }

    private static Set<String> csv(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (StrUtil.isBlank(raw)) {
            return out;
        }
        for (String part : StrUtil.split(raw, ',')) {
            if (StrUtil.isNotBlank(part)) {
                out.add(part.trim());
            }
        }
        return out;
    }
}
