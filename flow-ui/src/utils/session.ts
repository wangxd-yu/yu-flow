/** Cookie 会话：HttpOnly JWT 由浏览器携带；CSRF 双提交用可读 Cookie */

export const AUTH_HINT_KEY = 'flow_auth_hint';
export const CSRF_COOKIE = 'YU_FLOW_CSRF';
export const CSRF_HEADER = 'X-Yu-CSRF';

export function setAuthHint() {
  try {
    sessionStorage.setItem(AUTH_HINT_KEY, '1');
  } catch {
    /* ignore */
  }
}

export function clearAuthHint() {
  try {
    sessionStorage.removeItem(AUTH_HINT_KEY);
  } catch {
    /* ignore */
  }
  // 清理历史 localStorage Token（迁移期）
  try {
    localStorage.removeItem('flow_token');
  } catch {
    /* ignore */
  }
}

export function hasAuthHint(): boolean {
  try {
    return sessionStorage.getItem(AUTH_HINT_KEY) === '1';
  } catch {
    return false;
  }
}

export function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const parts = document.cookie ? document.cookie.split(';') : [];
  for (const part of parts) {
    const idx = part.indexOf('=');
    if (idx <= 0) continue;
    const k = part.slice(0, idx).trim();
    if (k === name) {
      return decodeURIComponent(part.slice(idx + 1).trim());
    }
  }
  return null;
}

export function csrfHeaders(): Record<string, string> {
  const csrf = readCookie(CSRF_COOKIE);
  return csrf ? { [CSRF_HEADER]: csrf } : {};
}
