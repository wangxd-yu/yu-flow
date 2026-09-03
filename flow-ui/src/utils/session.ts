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

/** 运营中心宿主 JWT（与门户 ssp.token.user 同源隔离） */
export const HOST_TOKEN_LS = 'ssp.token.opcenter';
const HOST_TOKEN_SESSION_KEY = 'ssp_host_token';

function rememberHostToken(token: string): string {
  try {
    sessionStorage.setItem(HOST_TOKEN_SESSION_KEY, token);
  } catch {
    /* ignore */
  }
  return token;
}

function readLocalStorage(key: string): string | null {
  try {
    const value = localStorage.getItem(key);
    return value && value.trim() ? value.trim() : null;
  } catch {
    return null;
  }
}

/**
 * 嵌入运营中心时的宿主 Token：session 缓存、URL ?token=、localStorage {@code ssp.token.opcenter}。
 * 用于 Authorization（OPCENTER）；与 Flow 自身 YU_FLOW_TOKEN Cookie 分离。
 */
export function resolveHostToken(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    const cached = sessionStorage.getItem(HOST_TOKEN_SESSION_KEY);
    if (cached) return cached;
  } catch {
    /* ignore */
  }
  try {
    const q = new URLSearchParams(window.location.search).get('token');
    if (q && q.trim()) {
      return rememberHostToken(q.trim());
    }
  } catch {
    /* ignore */
  }
  const fromLs = readLocalStorage(HOST_TOKEN_LS);
  return fromLs ? rememberHostToken(fromLs) : null;
}

/** 管理 API 附加宿主 Authorization（Flow JWT 仍走 Cookie） */
export function hostAuthHeaders(): Record<string, string> {
  const token = resolveHostToken();
  if (!token) return {};
  const value = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  return { Authorization: value };
}
