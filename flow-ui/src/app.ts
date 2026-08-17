// 运行时配置
import { requestConfig } from '@/utils/request';
// @ts-ignore
import { history } from 'umi';
import logo from '@/assets/logo1.svg';
import React from 'react';

import { fetchAuthMe, mayBeLoggedIn } from '@/services/auth';
import { clearAuthHint } from '@/utils/session';
import UserHeaderActions from '@/components/UserHeaderActions';

// ============ Favicon 动态与多环境适配 ============
(function () {
  if (typeof document !== 'undefined') {
    const publicPath =
      (window as any).publicPath ||
      (process.env.NODE_ENV === 'production' ? '/flow-ui/' : '/');
    let link = document.querySelector("link[rel*='icon']") as HTMLLinkElement;
    if (!link) {
      link = document.createElement('link');
      link.rel = 'shortcut icon';
      document.getElementsByTagName('head')[0].appendChild(link);
    }
    link.href = `${publicPath}logo1.svg`;
  }
})();

declare global {
  interface Window {
    __CONTEXT_PATH__?: string;
  }
}

const LOGIN_WHITE_LIST = ['/login'];

function isWhiteListed(pathname: string): boolean {
  return LOGIN_WHITE_LIST.some(
    (path) => pathname === path || pathname.endsWith(path),
  );
}

function resolveRoutePath(pathname: string): string {
  const base = '/flow-ui/';
  const contextPath = window.__CONTEXT_PATH__ || '';
  const fullBase = contextPath ? contextPath + base : base;
  const bases = [fullBase, base, fullBase.replace(/\/$/, ''), base.replace(/\/$/, '')].filter(
    (v, i, arr) => !!v && arr.indexOf(v) === i,
  );
  for (const b of bases) {
    if (pathname === b || pathname === b.replace(/\/$/, '')) {
      return '/';
    }
    if (pathname.startsWith(b.endsWith('/') ? b : b + '/')) {
      const prefix = b.endsWith('/') ? b : b + '/';
      return '/' + pathname.substring(prefix.length);
    }
  }
  return pathname;
}

export function modifyContextOpts(memo: any) {
  const contextPath = window.__CONTEXT_PATH__ || '';
  if (contextPath) {
    memo.basename = contextPath + '/flow-ui/';
  }
  return memo;
}

export function modifyClientRenderOpts(memo: any) {
  const contextPath = window.__CONTEXT_PATH__ || '';
  if (contextPath) {
    memo.basename = contextPath + '/flow-ui/';
  }
  return memo;
}

/**
 * Cookie 会话：无法读 HttpOnly JWT，用 session hint 做 UX 门禁；真正鉴权靠 /auth/me。
 */
export function render(oldRender: () => void) {
  const routePath = resolveRoutePath(window.location.pathname);

  if (!mayBeLoggedIn() && !isWhiteListed(routePath)) {
    history.push('/login');
    oldRender();
    return;
  }

  oldRender();
}

export async function getInitialState(): Promise<{
  name: string;
  displayName?: string;
  isLogin: boolean;
  userId?: string;
  roles?: string[];
  permissions?: string[];
  legacyAdmin?: boolean;
  ossEnabled?: boolean;
}> {
  if (!mayBeLoggedIn()) {
    return { name: '', isLogin: false, roles: [], permissions: [] };
  }
  const me = await fetchAuthMe();
  if (!me) {
    clearAuthHint();
    return { name: '', isLogin: false, roles: [], permissions: [] };
  }
  return {
    name: me.username,
    displayName: me.displayName || me.username,
    isLogin: true,
    userId: me.userId,
    roles: me.roles,
    permissions: me.permissions,
    legacyAdmin: me.legacyAdmin,
    ossEnabled: me.ossEnabled !== false,
  };
}

export const layout = ({
  initialState,
}: {
  initialState: {
    isLogin?: boolean;
    displayName?: string;
    name?: string;
    legacyAdmin?: boolean;
  } | null;
}) => {
  const isLogin = initialState?.isLogin ?? false;
  const displayName = initialState?.displayName || initialState?.name || '';
  const legacyAdmin = !!initialState?.legacyAdmin;

  return {
    title: 'YU Flow',
    logo: logo,
    menu: {
      locale: false,
    },
    actionsRender: isLogin
      ? () => [
          React.createElement(UserHeaderActions, {
            key: 'user-actions',
            displayName,
            legacyAdmin,
          }),
        ]
      : undefined,
    menuRender: isLogin ? undefined : false,
    headerRender: isLogin ? undefined : false,
  };
};

export const request = {
  ...requestConfig,
};

export function onRouteChange({ location }: { location: Location }) {
  const isWhitePath = isWhiteListed(location.pathname);
  if (!isWhitePath && !mayBeLoggedIn()) {
    if (location.pathname.endsWith('/login')) {
      return;
    }
    history.push('/login');
  }
}
