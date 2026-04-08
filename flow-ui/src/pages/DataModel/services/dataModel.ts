import { request } from '@umijs/max';

// ==========================================
// 目录相关接口
// ==========================================
export async function getDirectoryTree() {
  return request('/flow-api/model-directories/tree', {
    method: 'GET',
  });
}

export async function addDirectory(data: { parentId?: string; name: string }) {
  return request('/flow-api/model-directories', {
    method: 'POST',
    data,
  });
}

export async function updateDirectory(id: string, data: { name: string }) {
  return request(`/flow-api/model-directories/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteDirectory(id: string) {
  return request(`/flow-api/model-directories/${id}`, {
    method: 'DELETE',
  });
}

// ==========================================
// 数据模型相关接口
// ==========================================
export async function queryModelList(params: any) {
  return request('/flow-api/models/page', {
    method: 'GET',
    params,
  });
}

export async function getModelDetail(id: string) {
  return request(`/flow-api/models/${id}`, {
    method: 'GET',
  });
}

export async function addModel(data: any) {
  return request('/flow-api/models', {
    method: 'POST',
    data,
  });
}

export async function updateModel(id: string, data: any) {
  return request(`/flow-api/models/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteModel(id: string) {
  return request(`/flow-api/models/${id}`, {
    method: 'DELETE',
  });
}

export async function batchMoveModel(ids: string[], targetDirectoryId?: string) {
  return request('/flow-api/models/batch/moveToDir', {
    method: 'PUT',
    data: { ids, targetDirectoryId },
  });
}
