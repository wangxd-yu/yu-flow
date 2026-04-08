import { request } from '@umijs/max';

/** 健康度枚举值 */
export type HealthStatus = 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN';

export interface DataSourceDO {
  id: string;
  name: string;
  code: string; // 数据源全局唯一编码，用于跨环境关联
  dbType: string; // mysql/postgresql/highgo
  driverClassName: string;
  url: string;
  username: string;
  password: string;
  initialSize?: number;
  minIdle?: number;
  maxActive?: number;
  status?: number; // 0-停用,1-启用
  createTime?: string;
  updateTime?: string;
  // ---------- 健康度追踪字段 ----------
  healthStatus?: HealthStatus;
  errorCount?: number;
  lastErrorMsg?: string;
}

/** POST /flow-api/dataSource/test-connection 的请求参数 */
export interface TestConnectionParams {
  id?: string;
  driverClassName: string;
  url: string;
  username: string;
  password?: string;
}

/** POST /flow-api/dataSource/test-connection 的响应体 */
export interface TestConnectionResult {
  success: boolean;
  message: string;
}

export async function queryDataSourceList() {
  return request<Array<DataSourceDO>>('/flow-api/dataSource/list', {
    method: 'GET',
  });
}
export async function queryDataSourcePage(params: any) {
  return request<API.PageInfo<DataSourceDO>>('/flow-api/dataSource/page', {
    method: 'GET',
    params,
  });
}

export async function addDataSource(data: DataSourceDO) {
  return request<DataSourceDO>('/flow-api/dataSource', {
    method: 'POST',
    data,
  });
}

export async function updateDataSource(
  id: string,
  data: Partial<DataSourceDO>,
) {
  return request<DataSourceDO>(`/flow-api/dataSource/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteDataSource(id: string) {
  return request(`/flow-api/dataSource/${id}`, {
    method: 'DELETE',
  });
}

export async function batchDeleteDataSource(ids: string[]) {
  return request('/flow-api/dataSource/batch-delete', {
    method: 'POST',
    data: { ids },
  });
}

export async function enableDataSource(id: string) {
  return request<boolean>(`/flow-api/dataSource/${id}/enable`, {
    method: 'POST',
  });
}

export async function disableDataSource(id: string) {
  return request<boolean>(`/flow-api/dataSource/${id}/disable`, {
    method: 'POST',
  });
}

/** 用已保存数据源 ID 测试连接（GET，使用库中加密密码） */
export async function testDataSourceConnection(id: string) {
  return request<boolean>(`/flow-api/dataSource/${id}/test-connection`, {
    method: 'GET',
  });
}

/** 用表单参数独立测试连接（POST，不写库，5 秒超时） */
export async function testConnectionByParams(data: TestConnectionParams) {
  return request<TestConnectionResult>('/flow-api/dataSource/test-connection', {
    method: 'POST',
    data,
  });
}

