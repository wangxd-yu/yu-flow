/**
 * 构建接口调试用的 curl 命令（基于当前草稿 Method / URL）。
 */
export function buildApiCurl(method?: string, url?: string, origin?: string): string {
  const m = (method || 'GET').toUpperCase();
  const path = (url || '').trim() || '/';
  const base = (origin || (typeof window !== 'undefined' ? window.location.origin : '')).replace(/\/$/, '');
  const full = path.startsWith('http') ? path : `${base}${path.startsWith('/') ? path : `/${path}`}`;
  const safeUrl = full.replace(/'/g, `'\\''`);
  if (m === 'GET' || m === 'DELETE') {
    return `curl -X ${m} '${safeUrl}'`;
  }
  return `curl -X ${m} '${safeUrl}' -H 'Content-Type: application/json' -d '{}'`;
}

export async function copyText(text: string, successMsg = '已复制'): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const input = document.createElement('textarea');
      input.value = text;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
    }
    return true;
  } catch {
    return false;
  }
}

/** API 文档中心地址（OpenAPI 导出，无内置 Swagger UI） */
export function buildApiDocsCenterUrl(origin?: string): string {
  const base = (origin || (typeof window !== 'undefined' ? window.location.origin : '')).replace(/\/$/, '');
  return `${base}/flow-ui/api-docs`;
}

/**
 * 打开 API 文档中心（复制/下载 OpenAPI JSON）。
 * - 未发布：返回 false（调用方提示）
 * - 已发布：同窗口跳转或新窗口打开文档中心
 */
export function openPublishedApiDocCenter(opts: {
  apiId?: string;
  publishStatus?: number | null;
  newWindow?: boolean;
}): { ok: boolean; reason?: 'missing_id' | 'unpublished' } {
  const { apiId, publishStatus, newWindow = true } = opts;
  if (!apiId) return { ok: false, reason: 'missing_id' };
  if (publishStatus !== 1) return { ok: false, reason: 'unpublished' };
  const url = buildApiDocsCenterUrl();
  if (newWindow) {
    window.open(url, '_blank', 'noopener,noreferrer');
  } else if (typeof window !== 'undefined') {
    window.location.href = url;
  }
  return { ok: true };
}

/** @deprecated 使用 {@link openPublishedApiDocCenter}；保留别名避免外部调用断裂 */
export const openPublishedApiSwaggerDoc = openPublishedApiDocCenter;
