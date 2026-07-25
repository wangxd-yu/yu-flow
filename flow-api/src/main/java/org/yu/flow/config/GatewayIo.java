package org.yu.flow.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关 I/O 协作件：JSON 响应写出与请求参数 / 分页提取。
 * <p>从 {@link FlowApiGatewayFilter} 拆出，行为保持一致。</p>
 */
class GatewayIo {

    static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final ObjectMapper objectMapper;

    GatewayIo(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void writeJsonResponse(HttpServletResponse response, int status, Object data) throws IOException {
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status);
        objectMapper.writeValue(response.getWriter(), data);
    }

    void writeResponseEntity(HttpServletResponse response, ResponseEntity<?> responseEntity) throws IOException {
        response.setStatus(responseEntity.getStatusCodeValue());
        responseEntity.getHeaders().forEach((name, values) -> {
            for (String value : values) {
                response.setHeader(name, value);
            }
        });

        Object body = responseEntity.getBody();
        if (body == null) {
            return;
        }
        if (body instanceof String) {
            if (!responseEntity.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE)) {
                response.setContentType("text/plain;charset=UTF-8");
            }
            response.getWriter().write((String) body);
        } else {
            objectMapper.writeValue(response.getWriter(), body);
        }
    }

    Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            queryParams.put(paramName, request.getParameter(paramName));
        }
        return queryParams;
    }

    Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    Map<String, Object> toObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map) {
            ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    Map<String, Object> extractBodyParams(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null) {
            return new HashMap<>();
        }

        String body = request.getReader().lines().collect(java.util.stream.Collectors.joining());
        if (body == null || body.trim().isEmpty()) {
            return new HashMap<>();
        }

        if (contentType.contains("application/x-www-form-urlencoded")) {
            Map<String, Object> params = new HashMap<>();
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                }
            }
            return params;
        } else if (contentType.contains("application/json")) {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        }

        return new HashMap<>();
    }

    Pageable extractPageable(HttpServletRequest request) {
        int page = 0;
        int size = 10;
        Sort sort = Sort.unsorted();

        String pageStr = request.getParameter("page");
        String sizeStr = request.getParameter("size");
        String sortStr = request.getParameter("sort");

        try {
            if (pageStr != null) {
                page = Integer.parseInt(pageStr);
            }
            if (sizeStr != null) {
                size = Integer.parseInt(sizeStr);
            }
            if (sortStr != null) {
                sort = parseSortParameter(sortStr);
            }
        } catch (NumberFormatException e) {
        }
        return PageRequest.of(page, size, sort);
    }

    private Sort parseSortParameter(String sortStr) {
        List<Sort.Order> orders = new ArrayList<>();
        for (String param : sortStr.split(",")) {
            String[] parts = param.split(":");
            if (parts.length == 2) {
                orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]));
            }
        }
        return Sort.by(orders);
    }
}
