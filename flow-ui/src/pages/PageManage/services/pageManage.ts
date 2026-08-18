import { request } from '@umijs/max';

// ================================================================
// 页面管理 API
// ================================================================

/** 分页查询页面列表 */
export async function queryPageList(params: {
  directoryId?: string;
  name?: string;
  routePath?: string;
  page?: number;
  size?: number;
}) {
  return request('/flow-api/pages/page', {
    method: 'GET',
    params,
  });
}

/** 获取页面详情（含 schema） */
export async function getPageDetail(id: string) {
  return request(`/flow-api/pages/${id}`, {
    method: 'GET',
  });
}

/** 新建页面 */
export async function addPage(data: {
  name: string;
  routePath: string;
  directoryId?: string;
}) {
  return request('/flow-api/pages', {
    method: 'POST',
    data,
  });
}

/** 更新页面基础信息 */
export async function updatePage(id: string, data: {
  name?: string;
  routePath?: string;
  directoryId?: string;
}) {
  return request(`/flow-api/pages/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 保存设计器 JSON Schema */
export async function updatePageJson(id: string, json: string) {
  return request(`/flow-api/pages/${id}/json`, {
    method: 'PUT',
    data: { json },
  });
}

/** 切换发布状态 */
export async function updatePageStatus(id: string, status: number) {
  return request(`/flow-api/pages/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/** 克隆页面 */
export async function clonePage(id: string) {
  return request(`/flow-api/pages/${id}/clone`, {
    method: 'POST',
  });
}

/** 删除页面 */
export async function deletePage(id: string) {
  return request(`/flow-api/pages/${id}`, {
    method: 'DELETE',
  });
}

/** 批量移动页面 */
export async function batchMovePage(ids: string[], targetDirectoryId?: string) {
  return request('/flow-api/pages/batch/moveToDir', {
    method: 'PUT',
    data: { ids, targetDirectoryId },
  });
}
