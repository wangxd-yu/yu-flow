package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;
import org.yu.flow.exception.FlowException;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.ApiInterceptMode;

/**
 * 系统保留宿主目录接口：禁止删、改 path、改目录；允许改实现与发布。
 */
public final class HostCatalogLocks {

    private HostCatalogLocks() {
    }

    public static void assertNotDeleted(String id) {
        if (HostCatalogReserved.isReservedId(id)) {
            throw new FlowException("HOST_CATALOG_API_LOCKED",
                    "系统保留接口不可删除，请从「平台设置 → 宿主机配置」维护");
        }
    }

    public static void assertNotDeleted(Iterable<String> ids) {
        if (HostCatalogReserved.containsReserved(ids)) {
            throw new FlowException("HOST_CATALOG_API_LOCKED",
                    "系统保留接口不可删除，请从「平台设置 → 宿主机配置」维护");
        }
    }

    public static void assertNotMoved(Iterable<String> ids) {
        if (HostCatalogReserved.containsReserved(ids)) {
            throw new FlowException("HOST_CATALOG_API_LOCKED",
                    "系统保留接口不可移动目录");
        }
    }

    public static void assertCreateAllowed(FlowApiDO api) {
        if (api == null) {
            return;
        }
        if (HostCatalogReserved.isReservedId(api.getId())) {
            throw new FlowException("HOST_CATALOG_API_LOCKED",
                    "系统保留接口标识不可新建，请从「宿主机配置」进入编排");
        }
        if (HostCatalogReserved.isReservedUrl(api.getUrl())) {
            throw new FlowException("HOST_CATALOG_API_LOCKED",
                    "路径 " + api.getUrl() + " 为系统保留，不可占用");
        }
    }

    public static void assertUpdateAllowed(FlowApiDO incoming, FlowApiDO existing) {
        if (existing == null || !HostCatalogReserved.isReservedId(existing.getId())) {
            if (incoming != null && HostCatalogReserved.isReservedUrl(incoming.getUrl())
                    && !HostCatalogReserved.isReservedId(incoming.getId())) {
                throw new FlowException("HOST_CATALOG_API_LOCKED",
                        "路径 " + incoming.getUrl() + " 为系统保留，不可占用");
            }
            return;
        }
        HostCatalogReserved.Spec spec = HostCatalogReserved.specOfId(existing.getId());
        if (spec == null) {
            return;
        }
        String url = incoming.getUrl() == null ? "" : incoming.getUrl().trim();
        if (!spec.url().equals(url)) {
            incoming.setUrl(spec.url());
        }
        if (StrUtil.isNotBlank(incoming.getMethod()) && !"GET".equalsIgnoreCase(incoming.getMethod())) {
            throw new FlowException("HOST_CATALOG_API_LOCKED", "系统保留接口 Method 必须为 GET");
        }
        incoming.setMethod("GET");
        incoming.setDirectoryId(null);
        String serviceType = StrUtil.blankToDefault(
                        StrUtil.blankToDefault(incoming.getServiceType(), existing.getServiceType()), "")
                .trim().toUpperCase();
        String mode = ApiInterceptMode.normalize(incoming.getInterceptMode());
        if (ApiInterceptMode.WRAP.equals(mode) || ApiInterceptMode.SERVICE_TYPE_HOST.equals(
                serviceType)) {
            throw new FlowException("HOST_CATALOG_API_LOCKED",
                    "宿主保留接口只支持 FLOW / DB / JSON / STRING，不能使用 WRAP/HOST");
        }
        if (!"FLOW".equals(serviceType) && !"DB".equals(serviceType)
                && !"JSON".equals(serviceType) && !"STRING".equals(serviceType)) {
            throw new FlowException("HOST_CATALOG_API_LOCKED",
                    "宿主保留接口只支持 FLOW / DB / JSON / STRING");
        }
        String responseType = StrUtil.blankToDefault(
                        StrUtil.blankToDefault(incoming.getResponseType(), existing.getResponseType()), "")
                .trim().toUpperCase();
        if ("DB".equals(serviceType)
                && !"LIST".equals(responseType)
                && !"PAGE".equals(responseType)) {
            throw new FlowException("HOST_CATALOG_API_READ_ONLY",
                    "宿主保留接口 DB 实现只允许 LIST / PAGE 查询，禁止 INSERT / UPDATE");
        }
    }
}
