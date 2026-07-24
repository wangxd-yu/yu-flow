// 运行时配置
import { requestConfig } from '@/utils/request';
import { history } from 'umi'; // 从 umi 导入 history
import logo from '@/assets/logo1.svg';
import React from 'react';

import { fetchAuthMe } from '@/services/auth';
import { extensionRegistry } from '@/utils/extensionRegistry';
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

// ============ ContextPath 动态适配（仿 Swagger UI） ============
// 后端 FlowUiController 在渲染 index.html 时会注入:
//   <script>window.__CONTEXT_PATH__='/flow';</script>
//   <script>window.publicPath='/flow/flow-ui/';</script>
// 此处读取 __CONTEXT_PATH__ 动态调整路由 basename，使前端路由感知宿主系统的 context-path
declare global {
  interface Window {
    __CONTEXT_PATH__?: string;
  }
}

// ============ 登录白名单（无需登录即可访问的路径） ============
const LOGIN_WHITE_LIST = ['/login'];

/**
 * 判断当前路径是否在白名单中
 */
function isWhiteListed(pathname: string): boolean {
  return LOGIN_WHITE_LIST.some(
    (path) => pathname === path || pathname.endsWith(path),
  );
}

/**
 * 校验 JWT Token 的格式和过期时间
 */
function isTokenValid(token: string | null): boolean {
  if (!token) return false;
  try {
    // 兼容 localStorage 存 "Bearer <jwt>" 或纯 jwt
    const jwt = token.startsWith('Bearer ') ? token.slice(7).trim() : token.trim();
    const parts = jwt.split('.');
    if (parts.length !== 3) return false;

    // 解析 Base64URL 编码的 Payload
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window.atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );

    const payload = JSON.parse(jsonPayload);
    // 判断是否已经过期
    if (payload.exp && Date.now() >= payload.exp * 1000) {
      return false;
    }
    return true;
  } catch (error) {
    return false;
  }
}

/**
 * 运行时修改渲染配置 —— 动态设置路由 basename
 *
 * 当宿主系统配置了 context-path（如 /flow）时，
 * 将 basename 从编译时的 '/flow-ui/' 调整为 '/flow/flow-ui/'，
 * 确保 history.push('/login') 正确跳转到 /flow/flow-ui/login
 */
/**
 * 对于 UmiJS 4，必须在 modifyContextOpts 中修改 basename 才能被 createHistory 采纳！
 * modifyClientRenderOpts 只能改渲染选项，来不及影响路由实例的创建。
 */
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
 * render —— 在整个应用渲染之前执行的钩子
 *
 * 安全修复：在应用渲染前检查登录态，未登录且不在白名单的路径：
 * 1. 立即跳转到登录页
 * 2. 【关键】不调用 oldRender()，完全中止应用渲染
 * 这样可以确保未登录用户看不到任何页面内容（包括侧边栏菜单/目录结构），
 * 从根本上杜绝未授权页面闪现漏洞。
 */
export function render(oldRender: () => void) {
  const token = localStorage.getItem('flow_token');
  const pathname = window.location.pathname;

  // 需要去掉可能的 base 前缀来获取路由路径
  const base = '/flow-ui/';
  const contextPath = window.__CONTEXT_PATH__ || '';
  const fullBase = contextPath ? contextPath + base : base;

  // 从完整路径中提取路由路径（兼容 /flow-ui 无尾斜杠）
  let routePath = pathname;
  const bases = [fullBase, base, fullBase.replace(/\/$/, ''), base.replace(/\/$/, '')].filter(
    (v, i, arr) => !!v && arr.indexOf(v) === i,
  );
  for (const b of bases) {
    if (pathname === b || pathname === b.replace(/\/$/, '')) {
      routePath = '/';
      break;
    }
    if (pathname.startsWith(b.endsWith('/') ? b : b + '/')) {
      const prefix = b.endsWith('/') ? b : b + '/';
      routePath = '/' + pathname.substring(prefix.length);
      break;
    }
  }

  const isValid = isTokenValid(token);

  if (!isValid && !isWhiteListed(routePath)) {
    if (token) {
      localStorage.removeItem('flow_token');
    }
    history.push('/login');
    oldRender();
    return;
  }

  // 已登录或白名单路径 → 正常渲染
  oldRender();
}

// 全局初始化数据配置，用于 Layout 用户信息和权限初始化
export async function getInitialState(): Promise<{
  name: string;
  displayName?: string;
  isLogin: boolean;
  userId?: string;
  roles?: string[];
  permissions?: string[];
  legacyAdmin?: boolean;
}> {
  const token = localStorage.getItem('flow_token');
  const isLogin = isTokenValid(token);
  if (!isLogin) {
    return { name: '', isLogin: false, roles: [], permissions: [] };
  }
  const me = await fetchAuthMe();
  // /auth/me 失败：清 token，交由 render/onRouteChange 进登录页（此处勿 history.push，易在初始化阶段抛错白屏）
  if (!me) {
    localStorage.removeItem('flow_token');
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
  };
}

/**
 * layout —— 运行时布局配置
 *
 * 安全修复：绑定到 initialState.isLogin，实现响应式更新。
 * 当登录状态变化时（登录/退出），布局立即重新渲染，
 * 确保未登录时菜单栏和页头完全不可见。
 */
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
  // 可以覆盖或添加其他配置
};

// src/app.tsx
export function onRouteChange({ location }: { location: Location }) {
  // 获取 token
  const token = localStorage.getItem('flow_token');

  // 检查当前路径是否在白名单中
  const isWhitePath = isWhiteListed(location.pathname);

  const isValid = isTokenValid(token);

  // 如果不在白名单且没有有效 token，跳转到登录页
  if (!isWhitePath && !isValid) {
    if (token) {
      localStorage.removeItem('flow_token');
    }
    // 进一步检查，防止 history.push 导致的递归调用
    if (location.pathname.endsWith('/login')) {
      return;
    }
    history.push('/login'); // 无刷新跳转，保持路由状态一致
  }
}