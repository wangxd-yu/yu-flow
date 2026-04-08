import { request } from '@umijs/max';

export interface FlowController {
  id: string;
  name: string;
  description?: string;
  url: string;
  datasource?: string;
  directoryId?: string;
  directoryName?: string;
  module?: string;
  version?: string;
  /** @deprecated 使用 dslContent/sqlContent/jsonContent/textContent 替代 */
  config?: string;
  dslContent?: string;
  sqlContent?: string;
  jsonContent?: string;
  textContent?: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  publishStatus?: 0 | 1;
  level?: number;
  rule?: string;
  tags?: string[]; // 标签字段，支持数组格式
  createTime?: string;
  updateTime?: string;
}

export async function queryAutoApiConfigDetail(id: string): Promise<FlowController> {
  return request(`/flow-api/api/${id}`, {
    method: 'GET',
  });
}
export async function queryAutoApiConfigList(params: any) {
  return request<API.PageInfo<FlowController>>('/flow-api/api/page', {
    method: 'GET',
    params,
  });
}

export async function addAutoApiConfig(data: Partial<FlowController>) {
  return request<FlowController>('/flow-api/api', {
    method: 'POST',
    data,
  });
}

export async function updateAutoApiConfig(id: string, data: Partial<FlowController>) {
  return request<FlowController>(`/flow-api/api/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteAutoApiConfig(id: string) {
  return request(`/flow-api/api/${id}`, {
    method: 'DELETE',
  });
}

export async function batchDeleteAutoApiConfig(ids: string[]) {
  return request('/flow-api/api/batch/delete', {
    method: 'PUT',
    data: ids,
  });
}

export async function batchMoveAutoApiConfig(ids: string[], targetDirectoryId?: string) {
  return request('/flow-api/api/batch/moveToDir', {
    method: 'PUT',
    data: { ids, targetDirectoryId },
  });
}