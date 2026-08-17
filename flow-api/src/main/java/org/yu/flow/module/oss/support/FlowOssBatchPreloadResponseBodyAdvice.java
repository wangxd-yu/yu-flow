package org.yu.flow.module.oss.support;

import jakarta.annotation.Resource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.yu.flow.module.oss.annotation.FlowOssFileResolve;
import org.yu.flow.module.oss.annotation.FlowOssFileUrl;
import org.yu.flow.module.oss.service.OssObjectService;

import java.lang.reflect.Field;
import java.util.*;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

/**
 * 全局 Response 切面：在 Controller 返回 JSON 响应之前，自动递归扫描 Response 对象中的
 * &#64;FlowOssFileUrl 与 &#64;FlowOssFileResolve 注解，
 * 集中收集所有的 fileId 并自动触发单条 SQL IN 批量预热填充 Caffeine 缓存。
 *
 * <p>使得宿主服务零代码侵入，无需手动调用预热接口，即可天然消除 N+1 数据库查询隐患！
 */
@ConditionalOnOssEnabled
@ControllerAdvice
public class FlowOssBatchPreloadResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Resource
    private OssObjectService ossObjectService;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return body;
        }

        try {
            Set<String> absoluteUrlsToPreload = new HashSet<>();
            Set<String> relativeUrlsToPreload = new HashSet<>();
            Set<String> absoluteDetailsToPreload = new HashSet<>();
            Set<String> relativeDetailsToPreload = new HashSet<>();

            collectFileIds(body, absoluteUrlsToPreload, relativeUrlsToPreload, absoluteDetailsToPreload, relativeDetailsToPreload, new HashSet<>());

            if (!absoluteUrlsToPreload.isEmpty()) {
                ossObjectService.batchPreloadUrls(absoluteUrlsToPreload, true);
            }
            if (!relativeUrlsToPreload.isEmpty()) {
                ossObjectService.batchPreloadUrls(relativeUrlsToPreload, false);
            }
            if (!absoluteDetailsToPreload.isEmpty()) {
                ossObjectService.batchPreloadDetails(absoluteDetailsToPreload, true);
            }
            if (!relativeDetailsToPreload.isEmpty()) {
                ossObjectService.batchPreloadDetails(relativeDetailsToPreload, false);
            }
        } catch (Exception e) {
            // 静默捕获异常，确保切面扫描不影响正常主业务响应
        }

        return body;
    }

    private void collectFileIds(Object target, Set<String> absUrls, Set<String> relUrls,
                                Set<String> absDetails, Set<String> relDetails, Set<Object> visited) {
        if (target == null) {
            return;
        }
        Class<?> clazz = target.getClass();
        if (isPrimitiveOrSimple(clazz)) {
            return;
        }
        if (visited.contains(target)) {
            return;
        }
        visited.add(target);

        if (target instanceof Iterable) {
            for (Object item : (Iterable<?>) target) {
                collectFileIds(item, absUrls, relUrls, absDetails, relDetails, visited);
            }
            return;
        }

        if (target instanceof Map) {
            for (Object val : ((Map<?, ?>) target).values()) {
                collectFileIds(val, absUrls, relUrls, absDetails, relDetails, visited);
            }
            return;
        }

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object val = field.get(target);
                if (val == null) {
                    continue;
                }

                FlowOssFileUrl urlAnn = field.getAnnotation(FlowOssFileUrl.class);
                if (urlAnn != null && val instanceof String) {
                    String fileId = (String) val;
                    if (!fileId.trim().isEmpty()) {
                        if (urlAnn.absolute()) {
                            absUrls.add(fileId.trim());
                        } else {
                            relUrls.add(fileId.trim());
                        }
                    }
                }

                FlowOssFileResolve resAnn = field.getAnnotation(FlowOssFileResolve.class);
                if (resAnn != null && val instanceof String) {
                    String fileId = (String) val;
                    if (!fileId.trim().isEmpty()) {
                        if (resAnn.absolute()) {
                            absDetails.add(fileId.trim());
                        } else {
                            relDetails.add(fileId.trim());
                        }
                    }
                }

                if (urlAnn == null && resAnn == null && !isPrimitiveOrSimple(val.getClass())) {
                    collectFileIds(val, absUrls, relUrls, absDetails, relDetails, visited);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isPrimitiveOrSimple(Class<?> clazz) {
        return clazz.isPrimitive()
                || String.class.equals(clazz)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.equals(clazz)
                || Character.class.equals(clazz)
                || Date.class.equals(clazz)
                || clazz.getName().startsWith("java.time.")
                || clazz.getName().startsWith("java.lang.");
    }
}
