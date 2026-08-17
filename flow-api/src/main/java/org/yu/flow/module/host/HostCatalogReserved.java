package org.yu.flow.module.host;

import cn.hutool.core.util.StrUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宿主对接的系统保留接口（固定 id / path，不在接口管理列表出现）：
 * 5 条身份目录 + 1 条当前用户解析。
 */
public final class HostCatalogReserved {

    public static final String SETTINGS_KEY = "HOST_IDENTITY_CATALOG";

    /** 固定数字主键：{@code flow_api_info.id} 在瀚高/PG 为 bigint，不能用字符串码。 */
    public static final String DIR_USER_TYPE = "88060813001";
    public static final String DIR_ROLE = "88060813002";
    public static final String DIR_PERM = "88060813003";
    public static final String DIR_DEPT = "88060813004";
    public static final String DIR_USER = "88060813005";
    public static final String DIR_PRINCIPAL = "88060813006";

    /**
     * 当前用户解析接口：入参为转发的请求头/凭证，出参为一行主体字段。
     * 不属于 {@link FlowHostCatalogDimension}——目录回答「能勾哪些码」，它回答「当前是谁」。
     */
    public static final Spec PRINCIPAL = new Spec(
            DIR_PRINCIPAL, "/__sys/host-principal/resolve", "宿主主体·当前用户",
            "[{\"userId\":\"END_USER:1001\",\"username\":\"张三\",\"userType\":\"END_USER\","
                    + "\"deptId\":\"rd-fe\",\"roles\":[\"vip\"],\"permissions\":[\"order:read\"]}]");

    private static final Map<FlowHostCatalogDimension, Spec> SPECS = new LinkedHashMap<>();
    private static final List<Spec> ALL_SPECS;
    private static final List<String> IDS;
    private static final Set<String> ID_SET;

    static {
        SPECS.put(FlowHostCatalogDimension.USER_TYPE, new Spec(
                DIR_USER_TYPE, "/__sys/host-catalog/user-type", "宿主目录·用户类型",
                "[{\"value\":\"ADMIN\",\"label\":\"管理员/运营\"},{\"value\":\"END_USER\",\"label\":\"普通用户\"},{\"value\":\"OPEN_APP\",\"label\":\"开放应用\"}]"));
        SPECS.put(FlowHostCatalogDimension.ROLE, new Spec(
                DIR_ROLE, "/__sys/host-catalog/role", "宿主目录·角色",
                "[{\"value\":\"vip\",\"label\":\"会员\"},{\"value\":\"operator\",\"label\":\"运营\"},{\"value\":\"merchant\",\"label\":\"商户\"}]"));
        SPECS.put(FlowHostCatalogDimension.PERMISSION, new Spec(
                DIR_PERM, "/__sys/host-catalog/permission", "宿主目录·权限",
                "[{\"value\":\"*\",\"label\":\"超管\"},{\"value\":\"order:read\",\"label\":\"订单只读\"},{\"value\":\"order:write\",\"label\":\"订单编排\"},{\"value\":\"oss:avatar:upload\",\"label\":\"头像上传\"}]"));
        SPECS.put(FlowHostCatalogDimension.DEPT, new Spec(
                DIR_DEPT, "/__sys/host-catalog/dept", "宿主目录·部门",
                "[{\"value\":\"hq\",\"label\":\"总部\"},{\"value\":\"rd\",\"label\":\"研发中心\",\"parentId\":\"hq\"},{\"value\":\"rd-fe\",\"label\":\"前端组\",\"parentId\":\"rd\"},{\"value\":\"sales\",\"label\":\"销售中心\",\"parentId\":\"hq\"},{\"value\":\"sales-east\",\"label\":\"华东\",\"parentId\":\"sales\"}]"));
        SPECS.put(FlowHostCatalogDimension.USER, new Spec(
                DIR_USER, "/__sys/host-catalog/user", "宿主目录·用户",
                "[{\"value\":\"u-100\",\"label\":\"张三\"},{\"value\":\"u-200\",\"label\":\"李四\"},{\"value\":\"u-300\",\"label\":\"王五\"}]"));
        List<Spec> all = new java.util.ArrayList<>(SPECS.values());
        all.add(PRINCIPAL);
        ALL_SPECS = List.copyOf(all);
        IDS = List.copyOf(ALL_SPECS.stream().map(Spec::id).toList());
        ID_SET = Set.copyOf(IDS);
    }

    private HostCatalogReserved() {
    }

    public static Map<FlowHostCatalogDimension, Spec> specs() {
        return Collections.unmodifiableMap(SPECS);
    }

    public static Spec spec(FlowHostCatalogDimension dimension) {
        return SPECS.get(dimension);
    }

    /** 全部保留接口（含当前用户解析），用于建表引导与锁定校验。 */
    public static List<Spec> allSpecs() {
        return ALL_SPECS;
    }

    public static Spec specOfId(String id) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        String target = id.trim();
        for (Spec spec : ALL_SPECS) {
            if (spec.id().equals(target)) {
                return spec;
            }
        }
        return null;
    }

    public static List<String> ids() {
        return IDS;
    }

    public static Set<String> idSet() {
        return ID_SET;
    }

    public static boolean isReservedId(String id) {
        return StrUtil.isNotBlank(id) && ID_SET.contains(id.trim());
    }

    public static boolean isReservedUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        String u = url.trim();
        return ALL_SPECS.stream().anyMatch(s -> s.url().equals(u));
    }

    public static boolean containsReserved(Iterable<String> ids) {
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            if (isReservedId(id)) {
                return true;
            }
        }
        return false;
    }

    public static FlowHostCatalogDimension dimensionOfId(String id) {
        for (Map.Entry<FlowHostCatalogDimension, Spec> e : SPECS.entrySet()) {
            if (e.getValue().id().equals(id)) {
                return e.getKey();
            }
        }
        return null;
    }

    public record Spec(String id, String url, String name, String defaultJson) {
    }
}
