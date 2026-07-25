/**
 * 全局 API 类型声明
 * ─────────────────────────────────────────────────────────────
 * 历史上 `API.PageInfo` 寄生在脚手架 services/demo/typings.d.ts 中，
 * 脚手架清理后迁移至此，供 services/flow 各分页客户端使用。
 */
declare namespace API {
  interface PageInfo<T = Record<string, any>> {
    /** 当前页码 */
    current?: number;
    /** 每页大小 */
    size?: number;
    /** 数据总数 */
    total?: number;
    /** 总页数 */
    pages?: number;
    /** 数据列表 */
    items?: T[];
  }
}
