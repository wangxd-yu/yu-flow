import { clearAuthHint, hasAuthHint, hostAuthHeaders } from '@/utils/session';

export interface AuthMe {
  userId?: string;
  username: string;
  displayName?: string;
  roles: string[];
  permissions: string[];
  legacyAdmin?: boolean;
  /** false：后端未装配 OSS（未引入 minio 或 yu.flow.oss.enabled=false）。缺省按 true，兼容旧后端。 */
  ossEnabled?: boolean;
}

/**
 * 拉取当前登录用户权限（依赖 Cookie 会话，credentials: include）。
 */
export async function fetchAuthMe(): Promise<AuthMe | null> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 8000);
  try {
    // 与 request 拦截器一致：生产带 context-path（如 /flow）时必须拼前缀
    const contextPath =
      (typeof window !== 'undefined' && (window as any).__CONTEXT_PATH__) || '';
    const meUrl = `${contextPath}/flow-api/auth/me`;
    const res = await fetch(meUrl, {
      method: 'GET',
      credentials: 'include',
      signal: controller.signal,
      headers: {
        ...hostAuthHeaders(),
      },
    });
    if (!res.ok) return null;
    const body: any = await res.json();
    const data = body?.data !== undefined ? body.data : body;
    if (!data?.username) return null;
    return {
      userId: data.userId,
      username: data.username,
      displayName: data.displayName || data.username,
      roles: Array.isArray(data.roles) ? data.roles : [],
      permissions: Array.isArray(data.permissions) ? data.permissions : [],
      legacyAdmin: !!data.legacyAdmin,
      ossEnabled: data.ossEnabled !== false,
    };
  } catch {
    return null;
  } finally {
    window.clearTimeout(timer);
  }
}

export function hasPerm(permissions: string[] | undefined, ...codes: string[]): boolean {
  if (!codes.length) return true;
  const set = new Set(permissions || []);
  if (set.has('*')) return true;
  return codes.some((c) => set.has(c));
}

/** 当前用户修改密码 */
export async function changePassword(body: {
  oldPassword: string;
  newPassword: string;
  confirmPassword?: string;
}) {
  const { request } = await import('@umijs/max');
  return request('/flow-api/auth/change-password', {
    method: 'POST',
    data: body,
  });
}

export async function logoutRemote() {
  try {
    const { request } = await import('@umijs/max');
    await request('/flow-api/auth/logout', { method: 'POST' });
  } catch {
    /* ignore */
  } finally {
    clearAuthHint();
  }
}

/** 路由门禁 UX 提示；真正鉴权在 Cookie + /auth/me */
export function mayBeLoggedIn(): boolean {
  return hasAuthHint();
}
