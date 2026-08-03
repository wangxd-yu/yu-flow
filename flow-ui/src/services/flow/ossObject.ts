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
  const url = `${apiContextPath()}/flow-api/oss/objects/${encodeURIComponent(id)}/content?_t=${Date.now()}`;
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

export async function rebuildOssObjectThumbnail(id: string) {
  return request(`/flow-api/oss/objects/${id}/thumbnail/rebuild`, { method: 'POST' });
}
