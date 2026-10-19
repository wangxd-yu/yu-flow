package org.yu.flow.util.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个请求一个 ID：沿用合法的 {@code X-Request-Id}，否则生成；写入 MDC 并回写到响应头，
 * 前端报错时带上它，运维按 ID 在日志行和执行日志表里查。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = RequestIds.accept(request.getHeader(RequestIds.HEADER));
        if (id == null) {
            id = RequestIds.newId();
        }
        String previous = MDC.get(RequestIds.MDC_KEY);
        MDC.put(RequestIds.MDC_KEY, id);
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(RequestIds.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(RequestIds.MDC_KEY);
            } else {
                MDC.put(RequestIds.MDC_KEY, previous);
            }
        }
    }
}
