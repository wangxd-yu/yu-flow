import { request } from '@umijs/max';

export type OssVisibility = 'PUBLIC' | 'PRIVATE';

export interface OssUploadProfile {
  id: string;
  name: string;
  code: string;
  connectionCode: string;
  visibility: OssVisibility;
  bucketOverride?: string;
  keyPattern?: string;
  allowedContentTypes?: string;
  allowedExtensions?: string;
  maxSizeBytes?: number;
  maxFilesPerRequest?: number;
  quotaMaxBytes?: number;
  quotaMaxFiles?: number;
  thumbnailEnabled?: boolean;
  /** 最长边像素，空/0=用全局 */
  thumbnailMaxEdge?: number;
  /** 源文件上限字节，空/0=用全局 */
  thumbnailMaxSourceBytes?: number;
  /** JPEG 质量 0~1，空=用全局 */
  thumbnailJpegQuality?: number;
  requireAuth?: boolean;
  /** 是否开放预签名直传（客户端 PUT 直达 OSS） */
  presignUploadEnabled?: boolean;
  bizFieldsSchema?: string;
  /** 上传权限码：哪些 RBAC 权限才能上传 */
  uploadPerm?: string;
  /** 下载权限码：DataScope 不通过时的 RBAC 兜底权限码 */
  downloadPerm?: string;
  /** 访问规则 JSON：{"rules":[...]} */
  callerPolicy?: string;
  enabled?: boolean;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

// ── CRUD ──

export async function queryOssUploadProfilePage(params: any) {
  return request<API.PageInfo<OssUploadProfile>>('/flow-api/oss/profiles/page', {
    method: 'GET',
    params,
  });
}

export async function getOssUploadProfile(id: string): Promise<OssUploadProfile> {
  const result = await request(`/flow-api/oss/profiles/${id}`, { method: 'GET' });
  return (result as any)?.data ?? result;
}

export async function createOssUploadProfile(data: Partial<OssUploadProfile>) {
  return request<OssUploadProfile>('/flow-api/oss/profiles', {
    method: 'POST',
    data,
  });
}

export async function updateOssUploadProfile(id: string, data: Partial<OssUploadProfile>) {
  return request<OssUploadProfile>(`/flow-api/oss/profiles/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteOssUploadProfile(id: string) {
  return request(`/flow-api/oss/profiles/${id}`, { method: 'DELETE' });
}

export async function batchDeleteOssUploadProfile(ids: string[]) {
  return request('/flow-api/oss/profiles/batch/delete', {
    method: 'PUT',
    data: ids,
  });
}

export async function queryOssUploadProfileOptions() {
  const result = await request<OssUploadProfile[]>('/flow-api/oss/profiles/options', {
    method: 'GET',
  });
  return (result as any)?.data ?? result;
}
