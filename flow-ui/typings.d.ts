import '@umijs/max/typings';

declare namespace PageManage {
  interface PageConfig {
    id: string | number;
    name: string;
    routePath: string;
    json: string | object;
    status: boolean;
    /** 所属目录 ID，用于左侧目录树过滤 */
    directoryId?: string;
    /** 所属目录名称 */
    directoryName?: string;
    createTime: string;
  }
}
