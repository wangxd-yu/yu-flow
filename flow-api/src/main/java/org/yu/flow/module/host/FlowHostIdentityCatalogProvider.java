package org.yu.flow.module.host;

import java.util.List;

/**
 * 可选：向管理端提供调用方策略的下拉清单（用户类型 / 角色 / 权限 / 部门 / 用户）。
 *
 * <p><b>没有本 Bean 时</b>走「宿主机配置」保留接口。
 * {@link #supports} 为 false 的维度会从 OSS / 接口访问规则表单隐藏；
 * 已保存的约束仍按原样匹配，避免停用维度后策略被悄悄放宽。</p>
 *
 * <p>内置 JWT 不提供本实现。嵌入宿主后声明一个 {@code @Component} 即可，例如：</p>
 * <pre>{@code
 * @Component
 * public class MyIdentityCatalog implements FlowHostIdentityCatalogProvider {
 *     @Override
 *     public List<FlowHostCatalogItem> list(FlowHostCatalogDimension dimension,
 *                                          String keyword, int limit) {
 *         return switch (dimension) {
 *             case USER_TYPE -> List.of(
 *                 FlowHostCatalogItem.builder().value("ADMIN").label("运营").build(),
 *                 FlowHostCatalogItem.builder().value("END_USER").label("C 端用户").build());
 *             case ROLE -> filterByKeyword(hostRoles(), keyword, limit);
 *             default -> List.of();
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p>部门、用户量通常较大：请保持 {@link #searchable} 为 true，按 keyword 查询，不要一次倒全表。
 * 引擎会钳制 {@code limit}（默认 50，最大 200）。</p>
 */
public interface FlowHostIdentityCatalogProvider {

    /**
     * 本宿主是否拥有该身份维度。{@code false} 时管理端表单隐藏该维；已保存约束仍匹配。
     */
    default boolean supports(FlowHostCatalogDimension dimension) {
        return true;
    }

    /**
     * 该维度是否远程搜索。默认部门、用户为 true（快照接口不预拉全量）。
     */
    default boolean searchable(FlowHostCatalogDimension dimension) {
        return dimension == FlowHostCatalogDimension.DEPT
                || dimension == FlowHostCatalogDimension.USER;
    }

    /**
     * @param keyword 可空；搜索时应做包含/前缀匹配（大小写由宿主自定）
     * @param limit   已由引擎钳制的上限，宿主仍应遵守
     *                部门请带上 {@code parentId}，策略表单按树勾选，运行时默认含下级
     */
    List<FlowHostCatalogItem> list(FlowHostCatalogDimension dimension, String keyword, int limit);
}
