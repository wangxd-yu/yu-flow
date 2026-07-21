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
