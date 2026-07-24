export interface AuthMe {
  userId?: string;
  username: string;
  displayName?: string;
  roles: string[];
  permissions: string[];
  legacyAdmin?: boolean;
}

/**
 * 拉取当前登录用户权限。
 * 注意：getInitialState 阶段不要用 umi request（易循环依赖/阻塞白屏），故用原生 fetch。
 */
export async function fetchAuthMe(): Promise<AuthMe | null> {
  const token = localStorage.getItem('flow_token');
  if (!token) return null;

  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 8000);
  try {
    const res = await fetch('/flow-api/auth/me', {
      method: 'GET',
      headers: {
        'Flow-Authorization': token.startsWith('Bearer ') ? token : `Bearer ${token}`,
      },
      credentials: 'include',
      signal: controller.signal,
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
