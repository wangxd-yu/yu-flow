import { request } from '@umijs/max';
import { csrfHeaders } from '@/utils/session';

export type OssObjectStatus = 'ACTIVE' | 'DELETED';

export interface OssObject {
  id: string;
  profileCode?: string;
  connectionCode?: string;
  bucket?: string;
  objectKey?: string;
  visibility?: string;
  publicPath?: string;
  originalName?: string;
  contentType?: string;
  extension?: string;
  sizeBytes?: number;
  checksumSha256?: string;
  bizMeta?: string;
  uploadedBy?: string;
  uploadedByUserType?: string;
  uploadedByName?: string;
  deptId?: string;
  status?: OssObjectStatus;
  thumbStatus?: string;
  thumbPublicPath?: string;
  hasThumbnail?: boolean;
  expiresAt?: string;
  createTime?: string;
  updateTime?: string;
}

export interface OssPresignUrlResult {
  url: string;
  expireSeconds: number;
}

function apiContextPath(): string {
  return process.env.NODE_ENV === 'production' ? (window as any).__CONTEXT_PATH__ || '' : '';
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}

function filenameFromContentDisposition(cd: string | null, fallback: string) {
  const m = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(cd || '');
  if (m) {
    return decodeURIComponent(m[1] || m[2]);
  }
  return fallback;
}

// ── 查询 / 删除 / 下载 ──

export async function queryOssObjectPage(params: any) {
  return request<API.PageInfo<OssObject>>('/flow-api/oss/objects/page', {
    method: 'GET',
    params,
  });
}

export async function deleteOssObject(id: string) {
  return request(`/flow-api/oss/objects/${id}`, { method: 'DELETE' });
}

/** 私有文件内容下载（携带 Cookie + CSRF） */
export async function downloadOssObjectContent(id: string, fallbackName = 'download') {
  const url = `${apiContextPath()}/flow-api/oss/objects/${encodeURIComponent(id)}/content?stream=true&_t=${Date.now()}`;
  const res = await fetch(url, {
    method: 'GET',
    headers: csrfHeaders(),
    credentials: 'include',
  });
  if (res.status === 302 || res.redirected) {
    window.location.href = res.url;
    return;
  }
  if (!res.ok) {
    let msg = `下载失败 (${res.status})`;
    try {
      const j = await res.json();
      msg = j?.msg || j?.message || msg;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  triggerBlobDownload(
    blob,
    filenameFromContentDisposition(res.headers.get('Content-Disposition'), fallbackName),
  );
}

/** 获取预签名下载 URL（私有文件） */
export async function presignOssObject(id: string): Promise<OssPresignUrlResult> {
  const result = await request<OssPresignUrlResult>(`/flow-api/oss/objects/${id}/presign`, {
    method: 'GET',
  });
  return (result as any)?.data ?? result;
}

/** 批量打包下载（zip） */
export async function packDownloadOssObjects(ids: string[]) {
  const url = `${apiContextPath()}/flow-api/oss/objects/pack`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      ...csrfHeaders(),
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({ ids }),
  });
  if (!res.ok) {
    let msg = `打包下载失败 (${res.status})`;
    try {
      const j = await res.json();
      msg = j?.msg || j?.message || msg;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  triggerBlobDownload(blob, 'files.zip');
}

/** 获取缩略图 Blob URL（需手动 revokeObjectURL） */
export async function fetchOssObjectThumbnailBlobUrl(id: string): Promise<string> {
  const url = `${apiContextPath()}/flow-api/oss/objects/${encodeURIComponent(id)}/thumbnail?_t=${Date.now()}`;
  const res = await fetch(url, {
    method: 'GET',
    headers: csrfHeaders(),
    credentials: 'include',
  });
  if (!res.ok) {
    let msg = `缩略图不可用 (${res.status})`;
    try {
      const j = await res.json();
      msg = j?.msg || j?.message || msg;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

/** 获取原图文件内容 Blob URL（需手动 revokeObjectURL） */
export async function fetchOssObjectOriginalBlobUrl(id: string): Promise<string> {
  const url = `${apiContextPath()}/flow-api/oss/objects/${encodeURIComponent(id)}/content?stream=true&_t=${Date.now()}`;
  const res = await fetch(url, {
    method: 'GET',
    headers: csrfHeaders(),
    credentials: 'include',
  });
  if (!res.ok) {
    let msg = `原图不可用 (${res.status})`;
    try {
      const j = await res.json();
      msg = j?.msg || j?.message || msg;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

export async function rebuildOssObjectThumbnail(id: string) {
  return request(`/flow-api/oss/objects/${id}/thumbnail/rebuild`, { method: 'POST' });
}

export interface OssUploadResult {
  id: string;
  visibility?: string;
  originalName?: string;
  sizeBytes?: number;
  contentType?: string;
  publicUrl?: string;
  publicPath?: string;
  thumbStatus?: string;
  thumbPublicPath?: string;
  hasThumbnail?: boolean;
  expiresAt?: string;
}

/** 管理端试上传：POST /flow-api/oss/upload?profile=xxx */
export async function uploadOssByProfile(
  profileCode: string,
  files: File | File[],
): Promise<OssUploadResult[]> {
  const form = new FormData();
  const fileArray = Array.isArray(files) ? files : [files];
  fileArray.forEach((f) => form.append('file', f));
  const result = await request<OssUploadResult[]>(
    `/flow-api/oss/upload?profile=${encodeURIComponent(profileCode)}`,
    {
      method: 'POST',
      data: form,
      headers: csrfHeaders(),
    },
  );
  return (result as any)?.data ?? result;
}

// ── 预签名直传（单 PUT）──

export interface OssPresignInitResult {
  /** PENDING 台账 ID，confirm / abort 都用它 */
  objectId: string;
  /** 客户端直传地址（含签名参数） */
  uploadUrl: string;
  /** 固定 PUT */
  method: string;
  bucket: string;
  objectKey: string;
  expireSeconds?: number;
  urlExpiresAt?: string;
}

/** 开票：POST /flow-api/oss/presign/init?profile=xxx */
export async function initOssPresignUpload(
  profileCode: string,
  file: File,
  bizFields?: Record<string, string>,
): Promise<OssPresignInitResult> {
  const params = new URLSearchParams({
    profile: profileCode,
    originalName: file.name,
    sizeBytes: String(file.size),
  });
  if (file.type) {
    params.set('contentType', file.type);
  }
  const result = await request<OssPresignInitResult>(
    `/flow-api/oss/presign/init?${params.toString()}`,
    {
      method: 'POST',
      data: bizFields ?? {},
      headers: csrfHeaders(),
    },
  );
  return (result as any)?.data ?? result;
}

/**
 * 直传：PUT 文件体到 uploadUrl。
 * 不带任何业务凭证（签名已在 URL 上），也不能走 request()，否则会附加 baseURL 与 CSRF 头导致签名不匹配。
 * 需要在桶上放通跨域 PUT 与 Content-Type 请求头。
 */
export async function putOssPresignedFile(
  uploadUrl: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', uploadUrl, true);
    if (file.type) {
      xhr.setRequestHeader('Content-Type', file.type);
    }
    if (onProgress) {
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          onProgress(Math.round((e.loaded / e.total) * 100));
        }
      };
    }
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve()
        : reject(new Error(`直传失败 (${xhr.status})：${xhr.responseText || '请检查签名有效期与桶 CORS 配置'}`));
    xhr.onerror = () => reject(new Error('直传失败：网络异常或桶未放通跨域 PUT'));
    xhr.send(file);
  });
}

/** 复核转正：POST /flow-api/oss/presign/{objectId}/confirm */
export async function confirmOssPresignUpload(objectId: string): Promise<OssUploadResult> {
  const result = await request<OssUploadResult>(
    `/flow-api/oss/presign/${encodeURIComponent(objectId)}/confirm`,
    { method: 'POST', headers: csrfHeaders() },
  );
  return (result as any)?.data ?? result;
}

/** 放弃：DELETE /flow-api/oss/presign/{objectId} */
export async function abortOssPresignUpload(objectId: string) {
  return request(`/flow-api/oss/presign/${encodeURIComponent(objectId)}`, {
    method: 'DELETE',
    headers: csrfHeaders(),
  });
}

/** 端到端直传：init → PUT → confirm；PUT 失败自动 abort 回收 PENDING 台账 */
export async function uploadOssByPresign(
  profileCode: string,
  file: File,
  options?: { bizFields?: Record<string, string>; onProgress?: (percent: number) => void },
): Promise<OssUploadResult> {
  const ticket = await initOssPresignUpload(profileCode, file, options?.bizFields);
  try {
    await putOssPresignedFile(ticket.uploadUrl, file, options?.onProgress);
  } catch (e) {
    await abortOssPresignUpload(ticket.objectId).catch(() => undefined);
    throw e;
  }
  return confirmOssPresignUpload(ticket.objectId);
}

